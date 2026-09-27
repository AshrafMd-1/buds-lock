package dev.ashraf.buds4readonly;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import dev.ashraf.buds4readonly.core.SleepLockStatus;

/**
 * Transaction policy shared by the activity and widget service.
 *
 * Invariants:
 * - all six originals are durably saved before the first write;
 * - an existing backup is never overwritten;
 * - LOCKED is published only after all six read back as None;
 * - a backup is cleared only after all six originals read back exactly;
 * - interrupted work remains retryable through the restore path.
 */
public final class SleepLockController {
    public record Result(SleepLockStatus status, String message) {}

    private final BluetoothAdapter adapter;
    private final SleepLockBackupStore backupStore;
    private final LockStateStore stateStore;

    public SleepLockController(BluetoothAdapter adapter, SleepLockBackupStore backupStore,
                               LockStateStore stateStore) {
        this.adapter = adapter;
        this.backupStore = backupStore;
        this.stateStore = stateStore;
    }

    @SuppressLint("MissingPermission")
    public Result toggle(BluetoothDevice device) {
        if (adapter == null || !adapter.isEnabled()) {
            return publish(SleepLockStatus.ERROR, "Turn on Bluetooth and try again.");
        }

        SleepLockBackupStore.Backup backup = backupStore.load();
        if (backupStore.hasMarker() && backup == null) {
            return publish(SleepLockStatus.RECOVERY,
                    "Recovery data is damaged. No command was sent.");
        }
        if (backup != null && !backup.address().equals(device.getAddress())) {
            return publish(SleepLockStatus.RECOVERY,
                    "Reconnect the original Buds used for Sleep Lock, then tap again.");
        }

        String action = backup == null ? "Blocking controls…" : "Restoring controls…";
        publish(SleepLockStatus.WORKING, action);
        try (BudsRfcommClient client = BudsRfcommClient.connect(adapter, device)) {
            SleepLockTransaction.Result result = backup == null
                    ? SleepLockTransaction.lock(device.getAddress(), backupStore, client)
                    : SleepLockTransaction.restore(device.getAddress(), backup,
                            backupStore, client);
            return publish(result.status(), result.message());
        } catch (SecurityException error) {
            return failure(backupStore.hasMarker(), "Nearby devices permission is required.");
        } catch (Exception error) {
            return failure(backupStore.hasMarker(), friendlyError(error));
        }
    }

    private Result failure(boolean recoveryAvailable, String message) {
        if (recoveryAvailable) {
            return publish(SleepLockStatus.RECOVERY,
                    message + " Your original settings are saved; tap again to restore.");
        }
        return publish(SleepLockStatus.ERROR, message);
    }

    private Result publish(SleepLockStatus status, String message) {
        stateStore.setStatus(status, message);
        return new Result(status, message);
    }

    private static String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "The operation did not complete.";
        if (message.contains("connect") || message.contains("RFCOMM")) {
            return "Could not connect. Make sure the Buds are connected and HeyMelody is closed.";
        }
        return "Operation stopped safely: " + message;
    }
}
