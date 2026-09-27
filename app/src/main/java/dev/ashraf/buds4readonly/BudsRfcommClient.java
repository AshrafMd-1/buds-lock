package dev.ashraf.buds4readonly;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import dev.ashraf.buds4readonly.protocol.GestureRecord;
import dev.ashraf.buds4readonly.protocol.GestureResponseParser;
import dev.ashraf.buds4readonly.protocol.InnerPacket;
import dev.ashraf.buds4readonly.protocol.OpoV1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Thin transport client. It contains framing and I/O, but no Sleep Lock policy. */
public final class BudsRfcommClient
        implements AutoCloseable, SleepLockTransaction.GestureGateway {
    private static final UUID BUDS_COMMAND_UUID =
            UUID.fromString("0000079a-d102-11e1-9b23-00025b00a5a5");
    private static final long RESPONSE_TIMEOUT_MS = 8_000;

    private final BluetoothSocket socket;
    private final InputStream input;
    private final OutputStream output;
    private final ReceiveState receive = new ReceiveState();
    private int sequence;

    private BudsRfcommClient(BluetoothSocket socket) throws IOException {
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
    }

    @SuppressLint("MissingPermission")
    public static BudsRfcommClient connect(BluetoothAdapter adapter, BluetoothDevice device)
            throws Exception {
        Exception first = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            BluetoothSocket socket = null;
            try {
                socket = device.createRfcommSocketToServiceRecord(BUDS_COMMAND_UUID);
                adapter.cancelDiscovery();
                socket.connect();
                return new BudsRfcommClient(socket);
            } catch (Exception error) {
                if (socket != null) try { socket.close(); } catch (IOException ignored) {}
                if (attempt == 1) {
                    first = error;
                    Thread.sleep(1_000);
                } else {
                    throw new IOException("Could not connect after two attempts; first="
                            + message(first) + ", second=" + message(error), error);
                }
            }
        }
        throw new IOException("RFCOMM connection failed");
    }

    @Override public List<GestureRecord> readMappings() throws Exception {
        byte[] request = OpoV1.buildGestureRefresh(nextSequence());
        transmit(request);
        InnerPacket response = receiveUntil(OpoV1.GESTURE_REFRESH_RESPONSE);
        List<GestureRecord> records = GestureResponseParser.parse(response.payload());
        return records;
    }

    @Override public void setGesture(GestureRecord selector, int function) throws Exception {
        byte[] request = OpoV1.buildSingleGestureSet(nextSequence(),
                selector.deviceType(), selector.deviceButton(), selector.buttonAction(), function);
        transmit(request);
        InnerPacket acknowledgement = receiveUntil(OpoV1.GESTURE_SET_RESPONSE);
        if (acknowledgement.payload().length < 1) {
            throw new IOException("0x8401 acknowledgement has no status byte");
        }
        int status = acknowledgement.payload()[0] & 0xFF;
        if (status != 0) {
            throw new IOException(String.format("Buds rejected 0x0401 with status %02X", status));
        }
    }

    private int nextSequence() {
        int current = sequence;
        sequence = (sequence + 1) & 0xFF;
        return current;
    }

    private void transmit(byte[] frame) throws IOException {
        output.write(frame);
        output.flush();
    }

    private InnerPacket receiveUntil(int expectedCommand) throws Exception {
        long deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            int available = input.available();
            if (available == 0) {
                Thread.sleep(20);
                continue;
            }
            byte[] buffer = new byte[Math.min(available, 4096)];
            int read = input.read(buffer);
            if (read < 0) throw new IOException("Socket closed while awaiting response");
            if (read == 0) continue;
            byte[] chunk = Arrays.copyOf(buffer, read);
            InnerPacket match = null;
            for (OpoV1.LinkFrame frame : receive.decoder.accept(chunk)) {
                byte[] inner = null;
                if (!frame.sliced()) {
                    inner = frame.innerPart();
                } else {
                    receive.slices.write(frame.innerPart(), 0, frame.innerPart().length);
                    if (frame.finalSlice()) {
                        inner = receive.slices.toByteArray();
                        receive.slices.reset();
                    }
                }
                if (inner == null) continue;
                try {
                    InnerPacket packet = InnerPacket.parse(inner);
                    if (packet.command() == expectedCommand) match = packet;
                } catch (IllegalArgumentException ignored) {}
            }
            if (match != null) return match;
        }
        throw new IOException(String.format("No 0x%04X response within 8 seconds", expectedCommand));
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private static String message(Exception error) {
        if (error == null || error.getMessage() == null) return "unknown";
        return error.getMessage();
    }

    private static final class ReceiveState {
        final OpoV1.StreamDecoder decoder = new OpoV1.StreamDecoder();
        final ByteArrayOutputStream slices = new ByteArrayOutputStream();
    }
}
