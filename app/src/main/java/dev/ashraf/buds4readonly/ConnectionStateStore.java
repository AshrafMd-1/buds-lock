package dev.ashraf.buds4readonly;

import android.content.Context;
import android.content.SharedPreferences;

import dev.ashraf.buds4readonly.core.ConnectionStatus;

/** Cached connection state for the selected Buds address. */
public final class ConnectionStateStore {
    private static final String PREFS = "buds_connection_state_v1";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_STATUS = "status";
    private final SharedPreferences preferences;

    public ConnectionStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public ConnectionStatus statusFor(String address) {
        if (address == null || !address.equals(preferences.getString(KEY_ADDRESS, null))) {
            return ConnectionStatus.UNKNOWN;
        }
        try {
            return ConnectionStatus.valueOf(preferences.getString(
                    KEY_STATUS, ConnectionStatus.UNKNOWN.name()));
        } catch (IllegalArgumentException ignored) {
            return ConnectionStatus.UNKNOWN;
        }
    }

    public boolean update(String address, ConnectionStatus status) {
        if (address == null) return false;
        return preferences.edit()
                .putString(KEY_ADDRESS, address)
                .putString(KEY_STATUS, status.name())
                .commit();
    }
}
