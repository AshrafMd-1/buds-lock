package dev.ashraf.buds4readonly;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import dev.ashraf.buds4readonly.core.ConnectionStatus;

/** Keeps app/widget availability synchronized with Classic Bluetooth connection events. */
public final class BluetoothConnectionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        String targetAddress = targetAddress(context);
        if (targetAddress == null) return;

        ConnectionStatus status = null;
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            int adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            if (adapterState == BluetoothAdapter.STATE_OFF
                    || adapterState == BluetoothAdapter.STATE_TURNING_OFF) {
                status = ConnectionStatus.DISCONNECTED;
            }
        } else {
            BluetoothDevice device = deviceFrom(intent);
            if (device == null || !targetAddress.equals(device.getAddress())) return;
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                status = ConnectionStatus.CONNECTED;
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                status = ConnectionStatus.DISCONNECTED;
            } else {
                int profileState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                if (profileState == BluetoothProfile.STATE_CONNECTED) {
                    status = ConnectionStatus.CONNECTED;
                } else if (profileState == BluetoothProfile.STATE_DISCONNECTED) {
                    refreshProfiles(context, targetAddress);
                    return;
                }
            }
        }

        if (status == null) return;
        new ConnectionStateStore(context).update(targetAddress, status);
        BudsLockWidgetProvider.updateAll(context);
        context.sendBroadcast(new Intent(SleepLockService.ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName()));
    }

    private static void refreshProfiles(Context context, String targetAddress) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        BluetoothConnectionMonitor.query(context, adapter, targetAddress, status -> {
            new ConnectionStateStore(context).update(targetAddress, status);
            BudsLockWidgetProvider.updateAll(context);
            context.sendBroadcast(new Intent(SleepLockService.ACTION_STATE_CHANGED)
                    .setPackage(context.getPackageName()));
        });
    }

    private static String targetAddress(Context context) {
        SleepLockBackupStore.Backup backup = new SleepLockBackupStore(context).load();
        return backup == null
                ? new LockStateStore(context).load().deviceAddress()
                : backup.address();
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private static BluetoothDevice deviceFrom(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }
}
