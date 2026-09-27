package dev.ashraf.buds4readonly;

import dev.ashraf.buds4readonly.core.GestureSelectors;
import dev.ashraf.buds4readonly.core.SleepLockStatus;
import dev.ashraf.buds4readonly.protocol.GestureRecord;

import java.util.List;

/**
 * Android-independent save/write/verify/restore transaction.
 *
 * Exceptions intentionally escape to the platform controller. Recovery data
 * has already been committed before any lock write, and is never cleared by an
 * interrupted lock or restore.
 */
public final class SleepLockTransaction {
    public interface Backup {
        String address();
        List<GestureRecord> records();
    }

    public interface RecoveryStore {
        boolean saveIfAbsent(String address, List<GestureRecord> records);
        boolean clearAfterVerifiedRestore(String address);
    }

    public interface GestureGateway {
        List<GestureRecord> readMappings() throws Exception;
        void setGesture(GestureRecord selector, int function) throws Exception;
    }

    public record Result(SleepLockStatus status, String message) {}

    private SleepLockTransaction() {}

    public static Result lock(String address, RecoveryStore store, GestureGateway gateway)
            throws Exception {
        List<GestureRecord> originals = GestureSelectors.requireComplete(gateway.readMappings());
        if (GestureSelectors.allNone(originals)) {
            return new Result(SleepLockStatus.ERROR,
                    "All six controls are already None. Restore them in HeyMelody first so Buds Lock can save the originals.");
        }
        if (!store.saveIfAbsent(address, originals)) {
            return new Result(SleepLockStatus.RECOVERY,
                    "Could not safely save all six original controls. No write was sent.");
        }

        for (GestureRecord record : originals) {
            if (record.function() != GestureSelectors.FUNCTION_NONE) {
                gateway.setGesture(record, GestureSelectors.FUNCTION_NONE);
            }
        }

        List<GestureRecord> verified = GestureSelectors.requireComplete(gateway.readMappings());
        if (!GestureSelectors.allNone(verified)) {
            throw new IllegalStateException("Final read-back did not show all six controls as None");
        }
        return new Result(SleepLockStatus.LOCKED,
                "Single, double and triple taps are blocked on both earbuds.");
    }

    public static Result restore(String address, Backup backup, RecoveryStore store,
                                 GestureGateway gateway) throws Exception {
        if (!address.equals(backup.address())) {
            return new Result(SleepLockStatus.RECOVERY,
                    "Reconnect the original Buds used for Sleep Lock, then tap again.");
        }

        List<GestureRecord> current = GestureSelectors.requireComplete(gateway.readMappings());
        for (GestureRecord saved : backup.records()) {
            GestureRecord actual = GestureSelectors.find(current,
                    saved.deviceType(), saved.buttonAction());
            if (actual.function() != saved.function()) {
                gateway.setGesture(saved, saved.function());
            }
        }

        List<GestureRecord> verified = GestureSelectors.requireComplete(gateway.readMappings());
        if (!GestureSelectors.matchesFunctions(verified, backup.records())) {
            throw new IllegalStateException("Final read-back did not match the saved originals");
        }
        if (!store.clearAfterVerifiedRestore(address)) {
            return new Result(SleepLockStatus.RECOVERY,
                    "Controls are restored, but recovery cleanup failed. Tap once more safely.");
        }
        return new Result(SleepLockStatus.NORMAL,
                "Your original tap controls are restored.");
    }
}
