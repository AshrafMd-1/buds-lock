package dev.ashraf.buds4readonly.protocol;

import java.util.ArrayList;
import java.util.List;

public final class GestureResponseParser {
    private GestureResponseParser() {}

    public static List<GestureRecord> parse(byte[] payload) {
        if (payload.length < 2) {
            throw new IllegalArgumentException("gesture response payload shorter than 2 bytes");
        }
        int count = payload[1] & 0xFF;
        int required = 2 + count * 4;
        if (payload.length < required) {
            throw new IllegalArgumentException(
                    "gesture record count requires " + required + " bytes; received " + payload.length);
        }

        List<GestureRecord> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int offset = 2 + i * 4;
            records.add(new GestureRecord(i,
                    payload[offset] & 0xFF,
                    payload[offset + 1] & 0xFF,
                    payload[offset + 2] & 0xFF,
                    payload[offset + 3] & 0xFF));
        }
        return records;
    }
}
