package dev.ashraf.buds4readonly.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** APK-derived OPOv1 link framing. This class contains no Android dependencies. */
public final class OpoV1 {
    public static final int GESTURE_REFRESH_COMMAND = 0x0108;
    public static final int GESTURE_REFRESH_RESPONSE = 0x8108;
    public static final int GESTURE_SET_COMMAND = 0x0401;
    public static final int GESTURE_SET_RESPONSE = 0x8401;

    private OpoV1() {}

    public static byte[] buildGestureRefresh(int sequence) {
        return buildCommand(GESTURE_REFRESH_COMMAND, sequence, new byte[0]);
    }

    /**
     * Builds the APK-confirmed set-key-functions command for exactly one record.
     * The caller supplies the original selector bytes and the desired function.
     */
    public static byte[] buildSingleGestureSet(int sequence, int deviceType,
                                                int deviceButton, int buttonAction,
                                                int function) {
        byte[] payload = new byte[] {
                0x01,
                (byte) deviceType,
                (byte) deviceButton,
                (byte) buttonAction,
                (byte) function
        };
        return buildCommand(GESTURE_SET_COMMAND, sequence, payload);
    }

    public static byte[] buildCommand(int command, int sequence, byte[] payload) {
        byte[] inner = new byte[5 + payload.length];
        inner[0] = (byte) (command & 0xFF);
        inner[1] = (byte) ((command >>> 8) & 0xFF);
        inner[2] = (byte) sequence;
        inner[3] = (byte) (payload.length & 0xFF);
        inner[4] = (byte) ((payload.length >>> 8) & 0xFF);
        System.arraycopy(payload, 0, inner, 5, payload.length);
        return wrapUnfragmented(inner);
    }

    public static byte[] wrapUnfragmented(byte[] inner) {
        int declaredLength = inner.length + 2;
        byte[] length = encodeVarint(declaredLength);
        byte[] frame = new byte[1 + length.length + 2 + inner.length];
        frame[0] = (byte) 0xAA;
        System.arraycopy(length, 0, frame, 1, length.length);
        frame[1 + length.length] = 0x00; // control/FSN: unsliced
        frame[2 + length.length] = 0x00; // reserved
        System.arraycopy(inner, 0, frame, 3 + length.length, inner.length);
        return frame;
    }

    private static byte[] encodeVarint(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(2);
        do {
            int next = value & 0x7F;
            value >>>= 7;
            if (value != 0) next |= 0x80;
            out.write(next);
        } while (value != 0);
        return out.toByteArray();
    }

    public record LinkFrame(int fsn, byte[] innerPart) {
        public boolean sliced() { return fsn != 0; }
        public boolean finalSlice() { return fsn == 3; }
    }

    /** Streaming decoder matching the APK's LEB128 frame-length calculation. */
    public static final class StreamDecoder {
        private byte[] pending = new byte[0];

        public List<LinkFrame> accept(byte[] chunk) {
            byte[] merged = Arrays.copyOf(pending, pending.length + chunk.length);
            System.arraycopy(chunk, 0, merged, pending.length, chunk.length);
            List<LinkFrame> frames = new ArrayList<>();
            int cursor = 0;

            while (cursor < merged.length) {
                while (cursor < merged.length && (merged[cursor] & 0xFF) != 0xAA) cursor++;
                if (cursor >= merged.length) break;

                Varint decoded = readVarint(merged, cursor + 1);
                if (decoded == null) break;
                int total = 1 + decoded.bytes + decoded.value;
                if (decoded.value < 2) {
                    cursor++; // invalid frame; resynchronize at a later AA
                    continue;
                }
                if (merged.length - cursor < total) break;

                byte[] raw = Arrays.copyOfRange(merged, cursor, cursor + total);
                int controlIndex = 1 + decoded.bytes;
                int fsn = raw[controlIndex] & 0x03;
                int innerOffset = controlIndex + (fsn == 0 ? 2 : 3);
                if (innerOffset <= raw.length) {
                    frames.add(new LinkFrame(fsn, Arrays.copyOfRange(raw, innerOffset, raw.length)));
                }
                cursor += total;
            }
            pending = Arrays.copyOfRange(merged, cursor, merged.length);
            return frames;
        }

        private static Varint readVarint(byte[] data, int offset) {
            int value = 0;
            int shift = 0;
            for (int i = offset; i < data.length && shift <= 28; i++) {
                int current = data[i] & 0xFF;
                value |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) return new Varint(value, i - offset + 1);
                shift += 7;
            }
            return null;
        }

        private record Varint(int value, int bytes) {}
    }
}
