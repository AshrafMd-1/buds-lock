package dev.ashraf.buds4readonly;

import android.content.Context;
import android.content.SharedPreferences;

import dev.ashraf.buds4readonly.core.SleepLockStatus;

/** Last locally verified state used by the app and widget. */
public final class LockStateStore {
    private static final String PREFS = "buds_lock_state_v1";
    private static final String KEY_STATUS = "status";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_DEVICE_ADDRESS = "selected_device_address";

    public record Snapshot(SleepLockStatus status, String message, String deviceAddress) {}

    private final SharedPreferences preferences;

    public LockStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Snapshot load() {
        SleepLockStatus status;
        try {
            status = SleepLockStatus.valueOf(preferences.getString(
                    KEY_STATUS, SleepLockStatus.NORMAL.name()));
        } catch (IllegalArgumentException ignored) {
            status = SleepLockStatus.ERROR;
        }
        return new Snapshot(status,
                preferences.getString(KEY_MESSAGE, "Ready"),
                preferences.getString(KEY_DEVICE_ADDRESS, null));
    }

    public boolean setStatus(SleepLockStatus status, String message) {
        return preferences.edit()
                .putString(KEY_STATUS, status.name())
                .putString(KEY_MESSAGE, message)
                .commit();
    }

    public boolean selectDevice(String address) {
        return preferences.edit()
                .putString(KEY_DEVICE_ADDRESS, address)
                .commit();
    }
}
