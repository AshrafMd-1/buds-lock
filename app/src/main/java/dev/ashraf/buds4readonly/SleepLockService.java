package dev.ashraf.buds4readonly;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import dev.ashraf.buds4readonly.core.ConnectionStatus;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground bridge used by both the activity and the home-screen widget. */
public final class SleepLockService extends Service {
    public static final String ACTION_TOGGLE = "dev.ashraf.buds4readonly.action.TOGGLE";
    public static final String ACTION_STATE_CHANGED =
            "dev.ashraf.buds4readonly.action.STATE_CHANGED";
    private static final String CHANNEL_ID = "buds_lock_operations";
    private static final int NOTIFICATION_ID = 41;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static boolean isOperationRunning() { return RUNNING.get(); }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification(getString(R.string.working)));
        if (!ACTION_TOGGLE.equals(intent == null ? null : intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!RUNNING.compareAndSet(false, true)) return START_NOT_STICKY;
        sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
        BudsLockWidgetProvider.updateAll(this);
        executor.execute(() -> {
            try {
                performToggle();
            } finally {
                RUNNING.set(false);
                sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
                BudsLockWidgetProvider.updateAll(this);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
        return START_NOT_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void performToggle() {
        LockStateStore stateStore = new LockStateStore(this);
        SleepLockBackupStore backupStore = new SleepLockBackupStore(this);
        ConnectionStateStore connectionStore = new ConnectionStateStore(this);
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            String address = targetAddress(stateStore, backupStore);
            if (address != null) connectionStore.update(address, ConnectionStatus.DISCONNECTED);
            return;
        }
        BluetoothDevice device = resolveDevice(adapter, stateStore, backupStore);
        if (device == null) {
            return;
        }
        SleepLockController controller = new SleepLockController(adapter, backupStore,
                stateStore);
        SleepLockController.Result result = controller.toggle(device);
        if (result.status() == dev.ashraf.buds4readonly.core.SleepLockStatus.LOCKED
                || result.status() == dev.ashraf.buds4readonly.core.SleepLockStatus.NORMAL) {
            connectionStore.update(device.getAddress(), ConnectionStatus.CONNECTED);
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothDevice resolveDevice(BluetoothAdapter adapter, LockStateStore stateStore,
                                          SleepLockBackupStore backupStore) {
        String wanted = stateStore.load().deviceAddress();
        SleepLockBackupStore.Backup backup = backupStore.load();
        if (backup != null) wanted = backup.address();
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (wanted != null) {
            for (BluetoothDevice device : bonded) {
                if (wanted.equals(device.getAddress())) return device;
            }
        }
        BluetoothDevice onlyMatch = null;
        for (BluetoothDevice device : bonded) {
            String name = device.getName();
            if (name != null && name.toLowerCase(java.util.Locale.ROOT)
                    .contains("oneplus buds 4")) {
                if (onlyMatch != null) return null;
                onlyMatch = device;
            }
        }
        if (onlyMatch != null) stateStore.selectDevice(onlyMatch.getAddress());
        return onlyMatch;
    }

    private static String targetAddress(LockStateStore stateStore,
                                        SleepLockBackupStore backupStore) {
        SleepLockBackupStore.Backup backup = backupStore.load();
        return backup == null ? stateStore.load().deviceAddress() : backup.address();
    }

    @SuppressWarnings("deprecation")
    private Notification notification(String text) {
        PendingIntent content = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_lock_small)
                .setContentTitle(getString(R.string.app_name)).setContentText(text)
                .setContentIntent(content).setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE).build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
