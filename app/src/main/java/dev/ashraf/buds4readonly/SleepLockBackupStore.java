package dev.ashraf.buds4readonly;

import android.content.Context;
import android.content.SharedPreferences;

import dev.ashraf.buds4readonly.core.GestureSelectors;
import dev.ashraf.buds4readonly.protocol.GestureRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Durable recovery data for one device. A valid backup is written atomically
 * before the first mutation and is never overwritten while present.
 */
public final class SleepLockBackupStore implements SleepLockTransaction.RecoveryStore {
    private static final String PREFS = "sleep_lock_recovery_v1";
    private static final String KEY_SCHEMA = "schema";
    private static final String KEY_ADDRESS = "device_address";
    private static final String KEY_RECORDS = "gesture_records";
    private static final int SCHEMA = 1;

    public record Backup(String address, List<GestureRecord> records)
            implements SleepLockTransaction.Backup {}

    private final SharedPreferences preferences;

    public SleepLockBackupStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasMarker() {
        return preferences.contains(KEY_SCHEMA)
                || preferences.contains(KEY_ADDRESS)
                || preferences.contains(KEY_RECORDS);
    }

    @Override public synchronized boolean saveIfAbsent(String address,
                                                       List<GestureRecord> records) {
        if (hasMarker() || !GestureSelectors.isCompleteBackup(records)) return false;
        StringBuilder encoded = new StringBuilder();
        for (GestureRecord record : GestureSelectors.requireComplete(records)) {
            if (encoded.length() > 0) encoded.append(',');
            encoded.append(String.format("%02X%02X%02X%02X",
                    record.deviceType(), record.deviceButton(),
                    record.buttonAction(), record.function()));
        }
        return preferences.edit()
                .putInt(KEY_SCHEMA, SCHEMA)
                .putString(KEY_ADDRESS, address)
                .putString(KEY_RECORDS, encoded.toString())
                .commit();
    }

    public synchronized Backup load() {
        if (!hasMarker()) return null;
        if (preferences.getInt(KEY_SCHEMA, -1) != SCHEMA) return null;
        String address = preferences.getString(KEY_ADDRESS, null);
        String encoded = preferences.getString(KEY_RECORDS, null);
        if (address == null || address.trim().isEmpty() || encoded == null) return null;

        String[] parts = encoded.split(",", -1);
        if (parts.length != 6) return null;
        List<GestureRecord> records = new ArrayList<>(6);
        try {
            for (String part : parts) {
                if (part.length() != 8) return null;
                records.add(new GestureRecord(-1,
                        Integer.parseInt(part.substring(0, 2), 16),
                        Integer.parseInt(part.substring(2, 4), 16),
                        Integer.parseInt(part.substring(4, 6), 16),
                        Integer.parseInt(part.substring(6, 8), 16)));
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (!GestureSelectors.isCompleteBackup(records)) return null;
        return new Backup(address, java.util.Collections.unmodifiableList(
                new ArrayList<>(GestureSelectors.requireComplete(records))));
    }

    @Override public synchronized boolean clearAfterVerifiedRestore(String address) {
        Backup backup = load();
        if (backup == null || !backup.address().equals(address)) return false;
        return preferences.edit().clear().commit();
    }
}
