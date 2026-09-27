package dev.ashraf.buds4readonly.core;

import dev.ashraf.buds4readonly.protocol.GestureRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * The deliberately narrow selector set supported by Sleep Lock v1.
 *
 * Evidence-backed fields:
 * DB=01 is the not-on-call control bank, DT=01/02 are left/right, and
 * BA=01/02/03 are single/double/triple tap. Swipe, hold, and call controls are
 * intentionally absent.
 */
public final class GestureSelectors {
    public static final int CONTROL_BANK_NOT_ON_CALL = 0x01;
    public static final int LEFT = 0x01;
    public static final int RIGHT = 0x02;
    public static final int SINGLE_TAP = 0x01;
    public static final int DOUBLE_TAP = 0x02;
    public static final int TRIPLE_TAP = 0x03;
    public static final int FUNCTION_NONE = 0x00;

    private static final int[][] SUPPORTED = {
            {LEFT, SINGLE_TAP}, {LEFT, DOUBLE_TAP}, {LEFT, TRIPLE_TAP},
            {RIGHT, SINGLE_TAP}, {RIGHT, DOUBLE_TAP}, {RIGHT, TRIPLE_TAP}
    };

    private GestureSelectors() {}

    public static List<GestureRecord> requireComplete(List<GestureRecord> records) {
        List<GestureRecord> result = new ArrayList<>(SUPPORTED.length);
        for (int[] selector : SUPPORTED) {
            GestureRecord match = find(records, selector[0], selector[1]);
            if (match == null) {
                throw new IllegalArgumentException(String.format(
                        "required selector DT=%02X DB=01 BA=%02X is missing",
                        selector[0], selector[1]));
            }
            result.add(match);
        }
        return result;
    }

    public static GestureRecord find(List<GestureRecord> records, int deviceType, int action) {
        GestureRecord match = null;
        for (GestureRecord record : records) {
            if (record.deviceType() == deviceType
                    && record.deviceButton() == CONTROL_BANK_NOT_ON_CALL
                    && record.buttonAction() == action) {
                if (match != null) {
                    throw new IllegalArgumentException(String.format(
                            "duplicate selector DT=%02X DB=01 BA=%02X",
                            deviceType, action));
                }
                match = record;
            }
        }
        return match;
    }

    public static boolean isCompleteBackup(List<GestureRecord> records) {
        if (records == null || records.size() != SUPPORTED.length) return false;
        try {
            return requireComplete(records).size() == SUPPORTED.length;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean allNone(List<GestureRecord> records) {
        for (GestureRecord record : requireComplete(records)) {
            if (record.function() != FUNCTION_NONE) return false;
        }
        return true;
    }

    public static boolean matchesFunctions(List<GestureRecord> actual,
                                           List<GestureRecord> expected) {
        List<GestureRecord> completeActual = requireComplete(actual);
        List<GestureRecord> completeExpected = requireComplete(expected);
        for (GestureRecord wanted : completeExpected) {
            GestureRecord found = find(completeActual, wanted.deviceType(), wanted.buttonAction());
            if (found == null || found.function() != wanted.function()) return false;
        }
        return true;
    }

}
