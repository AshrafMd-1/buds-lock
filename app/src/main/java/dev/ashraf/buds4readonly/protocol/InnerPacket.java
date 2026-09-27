package dev.ashraf.buds4readonly.protocol;

import java.util.Arrays;

public record InnerPacket(int command, int sequence, byte[] payload) {
    public static InnerPacket parse(byte[] bytes) {
        if (bytes.length < 5) throw new IllegalArgumentException("inner packet shorter than 5 bytes");
        int command = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8);
        int sequence = bytes[2] & 0xFF;
        int payloadLength = (bytes[3] & 0xFF) | ((bytes[4] & 0xFF) << 8);
        if (bytes.length != 5 + payloadLength) {
            throw new IllegalArgumentException(
                    "inner length mismatch: header=" + payloadLength + ", actual=" + (bytes.length - 5));
        }
        return new InnerPacket(command, sequence, Arrays.copyOfRange(bytes, 5, bytes.length));
    }
}
