import dev.ashraf.buds4readonly.protocol.GestureRecord;
import dev.ashraf.buds4readonly.protocol.GestureResponseParser;
import dev.ashraf.buds4readonly.protocol.InnerPacket;
import dev.ashraf.buds4readonly.protocol.OpoV1;
import dev.ashraf.buds4readonly.core.GestureSelectors;

import java.util.List;

public final class ProtocolSelfTest {
    public static void main(String[] args) {
        byte[] request = OpoV1.buildGestureRefresh(0);
        require("AA 07 00 00 08 01 00 00 00".equals(toHex(request)), "read frame bytes");

        byte[] setNone = OpoV1.buildSingleGestureSet(1, 0x02, 0x01, 0x02, 0x00);
        require("AA 0C 00 00 01 04 01 05 00 01 02 01 02 00".equals(toHex(setNone)),
                "one-record set frame bytes");

        require("AA 0C 00 00 01 04 07 05 00 01 01 01 01 00".equals(toHex(
                OpoV1.buildSingleGestureSet(7, 0x01, 0x01, 0x01, 0x00))),
                "left single-tap None frame bytes");
        require("AA 0C 00 00 01 04 08 05 00 01 02 01 03 00".equals(toHex(
                OpoV1.buildSingleGestureSet(8, 0x02, 0x01, 0x03, 0x00))),
                "right triple-tap None frame bytes");

        byte[] payload = new byte[] {0x00, 0x02, 0x01, 0x10, 0x20, 0x00, 0x02, 0x11, 0x21, 0x07};
        byte[] inner = new byte[5 + payload.length];
        inner[0] = 0x08; inner[1] = 0x01; inner[2] = 0x35;
        inner[3] = (byte) payload.length; inner[4] = 0;
        System.arraycopy(payload, 0, inner, 5, payload.length);
        byte[] frame = OpoV1.wrapUnfragmented(inner);

        OpoV1.StreamDecoder decoder = new OpoV1.StreamDecoder();
        require(decoder.accept(java.util.Arrays.copyOfRange(frame, 0, 3)).isEmpty(), "partial frame waits");
        List<OpoV1.LinkFrame> frames = decoder.accept(java.util.Arrays.copyOfRange(frame, 3, frame.length));
        require(frames.size() == 1, "one reassembled frame");
        InnerPacket packet = InnerPacket.parse(frames.get(0).innerPart());
        require(packet.command() == 0x0108, "command LE");
        require(packet.sequence() == 0x35, "sequence");
        List<GestureRecord> records = GestureResponseParser.parse(packet.payload());
        require(records.size() == 2, "record count");
        require("[00] DT=01 DB=10 BA=20 FN=00".equals(records.get(0).hexLine()), "record 0");
        require("[01] DT=02 DB=11 BA=21 FN=07".equals(records.get(1).hexLine()), "record 1");

        byte[] observedResponse = fromHex(
                "08 81 00 4A 00 00 12 " +
                "01 01 01 01 01 01 02 06 01 01 03 05 01 01 04 08 01 01 06 00 01 01 05 07 " +
                "02 01 01 01 02 01 02 06 02 01 03 05 02 01 04 08 02 01 06 00 02 01 05 07 " +
                "01 06 02 1D 01 06 03 00 01 06 06 1C 02 06 02 1D 02 06 03 00 02 06 06 1C");
        InnerPacket observedPacket = InnerPacket.parse(observedResponse);
        require(observedPacket.command() == OpoV1.GESTURE_REFRESH_RESPONSE,
                "observed response command 0x8108");
        List<GestureRecord> observedRecords = GestureResponseParser.parse(observedPacket.payload());
        require(observedRecords.size() == 18,
                "observed response has 18 records");
        List<GestureRecord> six = GestureSelectors.requireComplete(observedRecords);
        require(six.size() == 6, "exactly six supported tap selectors");
        require(GestureSelectors.find(six, GestureSelectors.LEFT,
                GestureSelectors.SINGLE_TAP).function() == 0x01, "left single function retained");
        require(GestureSelectors.find(six, GestureSelectors.RIGHT,
                GestureSelectors.TRIPLE_TAP).function() == 0x05, "right triple function retained");

        byte[] rightSingleNonePayload = observedPacket.payload().clone();
        rightSingleNonePayload[2 + 6 * 4 + 3] = 0x00;
        List<GestureRecord> rightSingleNone = GestureResponseParser.parse(rightSingleNonePayload);
        require(GestureSelectors.find(rightSingleNone, GestureSelectors.RIGHT,
                GestureSelectors.SINGLE_TAP).function() == 0x00,
                "controlled right single-tap None record");

        byte[] rightTripleNonePayload = observedPacket.payload().clone();
        rightTripleNonePayload[2 + 8 * 4 + 3] = 0x00;
        List<GestureRecord> rightTripleNone = GestureResponseParser.parse(rightTripleNonePayload);
        require(GestureSelectors.find(rightTripleNone, GestureSelectors.RIGHT,
                GestureSelectors.TRIPLE_TAP).function() == 0x00,
                "controlled right triple-tap None record");

        List<GestureRecord> duplicated = new java.util.ArrayList<>(six);
        duplicated.add(new GestureRecord(99, GestureSelectors.RIGHT,
                GestureSelectors.CONTROL_BANK_NOT_ON_CALL,
                GestureSelectors.TRIPLE_TAP, GestureSelectors.FUNCTION_NONE));
        requireThrows(() -> GestureSelectors.requireComplete(duplicated),
                "duplicate supported selector rejected");
        System.out.println("Protocol self-test passed");
    }

    private static byte[] fromHex(String text) {
        String compact = text.replace(" ", "");
        byte[] result = new byte[compact.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 3);
        for (byte value : bytes) {
            if (result.length() > 0) result.append(' ');
            result.append(String.format("%02X", value & 0xFF));
        }
        return result.toString();
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void requireThrows(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label);
    }
}
