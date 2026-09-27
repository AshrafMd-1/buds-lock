package dev.ashraf.buds4readonly;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import dev.ashraf.buds4readonly.core.ConnectionStatus;

import java.util.function.Consumer;

/** Queries the public A2DP and Headset profiles for one exact device address. */
public final class BluetoothConnectionMonitor {
    private static final long QUERY_TIMEOUT_MS = 5_000;

    private BluetoothConnectionMonitor() {}

    public static void query(Context context, BluetoothAdapter adapter, String address,
                             Consumer<ConnectionStatus> callback) {
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(
                () -> new Query(appContext, adapter, address, callback).start());
    }

    private static final class Query {
        private final Context context;
        private final BluetoothAdapter adapter;
        private final String address;
        private final Consumer<ConnectionStatus> callback;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private int remaining = 2;
        private boolean finished;

        Query(Context context, BluetoothAdapter adapter, String address,
              Consumer<ConnectionStatus> callback) {
            this.context = context;
            this.adapter = adapter;
            this.address = address;
            this.callback = callback;
        }

        @SuppressLint("MissingPermission")
        void start() {
            if (adapter == null || address == null || !adapter.isEnabled()) {
                finish(ConnectionStatus.DISCONNECTED);
                return;
            }
            if (Build.VERSION.SDK_INT >= 31
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                finish(ConnectionStatus.UNKNOWN);
                return;
            }
            if (hasExactAudioRoute()) {
                finish(ConnectionStatus.CONNECTED);
                return;
            }
            requestProfile(BluetoothProfile.A2DP);
            requestProfile(BluetoothProfile.HEADSET);
            handler.postDelayed(() -> finish(ConnectionStatus.UNKNOWN), QUERY_TIMEOUT_MS);
        }

        private boolean hasExactAudioRoute() {
            AudioManager audio = context.getSystemService(AudioManager.class);
            if (audio == null) return false;
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = device.getType();
                boolean bluetoothAudio = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        || type == AudioDeviceInfo.TYPE_BLE_BROADCAST;
                if (bluetoothAudio && address.equalsIgnoreCase(device.getAddress())) return true;
            }
            return false;
        }

        @SuppressLint("MissingPermission")
        private void requestProfile(int profileId) {
            ProfileRequest request = new ProfileRequest(profileId);
            try {
                if (!adapter.getProfileProxy(context, request, profileId)) {
                    request.complete(false, null);
                }
            } catch (SecurityException denied) {
                finish(ConnectionStatus.UNKNOWN);
            }
        }

        private synchronized void profileFinished(boolean connected) {
            if (finished) return;
            if (connected) {
                finish(ConnectionStatus.CONNECTED);
                return;
            }
            remaining--;
            if (remaining == 0) finish(ConnectionStatus.DISCONNECTED);
        }

        private synchronized void finish(ConnectionStatus status) {
            if (finished) return;
            finished = true;
            handler.removeCallbacksAndMessages(null);
            callback.accept(status);
        }

        private final class ProfileRequest implements BluetoothProfile.ServiceListener {
            private final int requestedProfile;
            private boolean completed;

            ProfileRequest(int requestedProfile) {
                this.requestedProfile = requestedProfile;
            }

            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                boolean connected = false;
                try {
                    // Some Android 16 Bluetooth stacks expose an identity-address-aware
                    // result through getConnectionState(target) while their connected
                    // device list can contain a transport/alias BluetoothDevice that
                    // does not compare as the selected bonded device. Ask for the exact
                    // target first, then retain the list walk as a compatibility fallback.
                    BluetoothDevice target = adapter.getRemoteDevice(address);
                    connected = proxy.getConnectionState(target)
                            == BluetoothProfile.STATE_CONNECTED;
                    if (!connected) {
                        for (BluetoothDevice device : proxy.getConnectedDevices()) {
                            if (address.equals(device.getAddress())) {
                                connected = true;
                                break;
                            }
                        }
                    }
                } catch (SecurityException denied) {
                    adapter.closeProfileProxy(requestedProfile, proxy);
                    finish(ConnectionStatus.UNKNOWN);
                    return;
                }
                complete(connected, proxy);
            }

            @Override public void onServiceDisconnected(int profile) {
                complete(false, null);
            }

            synchronized void complete(boolean connected, BluetoothProfile proxy) {
                if (proxy != null) adapter.closeProfileProxy(requestedProfile, proxy);
                if (completed) return;
                completed = true;
                profileFinished(connected);
            }
        }
    }
}
