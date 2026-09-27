import dev.ashraf.buds4readonly.SleepLockTransaction;
import dev.ashraf.buds4readonly.core.GestureSelectors;
import dev.ashraf.buds4readonly.core.SleepLockStatus;
import dev.ashraf.buds4readonly.protocol.GestureRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SleepLockTransactionTest {
    public static void main(String[] args) throws Exception {
        successfulLockAndRestore();
        interruptedLockKeepsOriginalsAndRestores();
        interruptedRestoreKeepsBackupAndRetries();
        System.out.println("Sleep Lock transaction regression tests passed");
    }

    private static void successfulLockAndRestore() throws Exception {
        List<GestureRecord> originals = originals();
        FakeStore store = new FakeStore();
        FakeGateway gateway = new FakeGateway(originals);

        SleepLockTransaction.Result locked = SleepLockTransaction.lock(
                "AA:BB:CC:DD:EE:FF", store, gateway);
        require(locked.status() == SleepLockStatus.LOCKED, "successful lock status");
        require(store.backup != null, "backup retained while locked");
        require(GestureSelectors.allNone(gateway.readMappings()), "all six controls are None");

        SleepLockTransaction.Result restored = SleepLockTransaction.restore(
                "AA:BB:CC:DD:EE:FF", store.backup, store, gateway);
        require(restored.status() == SleepLockStatus.NORMAL, "successful restore status");
        require(store.backup == null, "backup cleared only after verified restore");
        require(GestureSelectors.matchesFunctions(gateway.readMappings(), originals),
                "exact originals restored");
    }

    private static void interruptedLockKeepsOriginalsAndRestores() throws Exception {
        List<GestureRecord> originals = originals();
        FakeStore store = new FakeStore();
        FakeGateway gateway = new FakeGateway(originals);
        gateway.failAfterSuccessfulWrites(2);

        requireThrows(() -> SleepLockTransaction.lock(
                "AA:BB:CC:DD:EE:FF", store, gateway), "lock interruption propagated");
        require(store.backup != null, "interrupted lock retains backup");
        require(GestureSelectors.matchesFunctions(store.backup.records(), originals),
                "interrupted lock never overwrites originals");

        gateway.disableFailure();
        SleepLockTransaction.Result restored = SleepLockTransaction.restore(
                "AA:BB:CC:DD:EE:FF", store.backup, store, gateway);
        require(restored.status() == SleepLockStatus.NORMAL, "partial lock is recoverable");
        require(store.backup == null, "recovery clears only after exact read-back");
        require(GestureSelectors.matchesFunctions(gateway.readMappings(), originals),
                "partial lock recovery restores exact originals");
    }

    private static void interruptedRestoreKeepsBackupAndRetries() throws Exception {
        List<GestureRecord> originals = originals();
        FakeStore store = new FakeStore();
        FakeGateway gateway = new FakeGateway(originals);
        SleepLockTransaction.lock("AA:BB:CC:DD:EE:FF", store, gateway);

        gateway.failAfterSuccessfulWrites(2);
        requireThrows(() -> SleepLockTransaction.restore(
                "AA:BB:CC:DD:EE:FF", store.backup, store, gateway),
                "restore interruption propagated");
        require(store.backup != null, "interrupted restore retains backup");
        require(GestureSelectors.matchesFunctions(store.backup.records(), originals),
                "interrupted restore preserves original snapshot");

        gateway.disableFailure();
        SleepLockTransaction.Result retry = SleepLockTransaction.restore(
                "AA:BB:CC:DD:EE:FF", store.backup, store, gateway);
        require(retry.status() == SleepLockStatus.NORMAL, "restore retry succeeds");
        require(store.backup == null, "restore retry clears backup after verification");
        require(GestureSelectors.matchesFunctions(gateway.readMappings(), originals),
                "restore retry is idempotent");
    }

    private static List<GestureRecord> originals() {
        return List.of(
                record(GestureSelectors.LEFT, GestureSelectors.SINGLE_TAP, 0x01),
                record(GestureSelectors.LEFT, GestureSelectors.DOUBLE_TAP, 0x06),
                record(GestureSelectors.LEFT, GestureSelectors.TRIPLE_TAP, 0x05),
                record(GestureSelectors.RIGHT, GestureSelectors.SINGLE_TAP, 0x01),
                record(GestureSelectors.RIGHT, GestureSelectors.DOUBLE_TAP, 0x06),
                record(GestureSelectors.RIGHT, GestureSelectors.TRIPLE_TAP, 0x05));
    }

    private static GestureRecord record(int side, int action, int function) {
        return new GestureRecord(-1, side,
                GestureSelectors.CONTROL_BANK_NOT_ON_CALL, action, function);
    }

    private static final class FakeStore implements SleepLockTransaction.RecoveryStore {
        private Backup backup;

        @Override public boolean saveIfAbsent(String address, List<GestureRecord> records) {
            if (backup != null) return false;
            backup = new Backup(address, List.copyOf(records));
            return true;
        }

        @Override public boolean clearAfterVerifiedRestore(String address) {
            if (backup == null || !backup.address().equals(address)) return false;
            backup = null;
            return true;
        }

        private record Backup(String address, List<GestureRecord> records)
                implements SleepLockTransaction.Backup {}
    }

    private static final class FakeGateway implements SleepLockTransaction.GestureGateway {
        private final List<GestureRecord> current;
        private int successfulWrites;
        private int failAfter = Integer.MAX_VALUE;

        FakeGateway(List<GestureRecord> initial) {
            current = new ArrayList<>(initial);
        }

        void failAfterSuccessfulWrites(int writes) {
            successfulWrites = 0;
            failAfter = writes;
        }

        void disableFailure() {
            successfulWrites = 0;
            failAfter = Integer.MAX_VALUE;
        }

        @Override public List<GestureRecord> readMappings() {
            return List.copyOf(current);
        }

        @Override public void setGesture(GestureRecord selector, int function) throws IOException {
            if (successfulWrites >= failAfter) throw new IOException("simulated disconnect");
            for (int i = 0; i < current.size(); i++) {
                GestureRecord record = current.get(i);
                if (record.deviceType() == selector.deviceType()
                        && record.deviceButton() == selector.deviceButton()
                        && record.buttonAction() == selector.buttonAction()) {
                    current.set(i, new GestureRecord(record.index(), record.deviceType(),
                            record.deviceButton(), record.buttonAction(), function));
                    successfulWrites++;
                    return;
                }
            }
            throw new IOException("selector missing");
        }
    }

    @FunctionalInterface
    private interface ThrowingAction { void run() throws Exception; }

    private static void requireThrows(ThrowingAction action, String label) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(label);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
