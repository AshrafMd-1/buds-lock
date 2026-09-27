package dev.ashraf.buds4readonly;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import dev.ashraf.buds4readonly.core.ConnectionStatus;
import dev.ashraf.buds4readonly.core.SleepLockStatus;
import dev.ashraf.buds4readonly.core.UiStateResolver;

public final class BudsLockWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int widgetId : widgetIds) manager.updateAppWidget(widgetId, render(context));
        refreshConnection(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, BudsLockWidgetProvider.class));
        for (int id : ids) manager.updateAppWidget(id, render(context));
    }

    private static RemoteViews render(Context context) {
        LockStateStore.Snapshot state = new LockStateStore(context).load();
        String address = targetAddress(context);
        ConnectionStatus connection = new ConnectionStateStore(context).statusFor(address);
        SleepLockStatus displayStatus = SleepLockService.isOperationRunning()
                ? SleepLockStatus.WORKING : state.status();
        UiStateResolver.Presentation presentation = UiStateResolver.resolve(
                displayStatus, connection);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_buds_lock);
        int icon = R.drawable.ic_bluetooth_off_widget;
        String description = context.getString(R.string.widget_offline_description);
        switch (presentation.visualState()) {
            case OFFLINE -> {
                icon = R.drawable.ic_bluetooth_off_widget;
                description = context.getString(R.string.widget_offline_description);
            }
            case LOCKED -> {
                icon = R.drawable.ic_lock_closed_widget;
                description = context.getString(R.string.widget_locked_description);
            }
            case WORKING -> {
                icon = R.drawable.ic_sync_widget;
                description = context.getString(R.string.widget_working_description);
            }
            case RECOVERY, ERROR -> {
                icon = R.drawable.ic_warning_widget;
                description = context.getString(R.string.widget_recovery_description);
            }
            case NORMAL -> {
                icon = R.drawable.ic_lock_open_widget;
                description = context.getString(R.string.widget_normal_description);
            }
        }
        views.setImageViewResource(R.id.widget_icon, icon);
        views.setContentDescription(R.id.widget_root, description);
        PendingIntent click;
        if (presentation.toggleEnabled()) {
            Intent toggle = new Intent(context, SleepLockService.class)
                    .setAction(SleepLockService.ACTION_TOGGLE);
            click = Build.VERSION.SDK_INT >= 26
                    ? PendingIntent.getForegroundService(context, 10, toggle,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                    : PendingIntent.getService(context, 10, toggle,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            Intent openApp = new Intent(context, MainActivity.class);
            click = PendingIntent.getActivity(context, 11, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        views.setOnClickPendingIntent(R.id.widget_root, click);
        return views;
    }

    private static void refreshConnection(Context context) {
        String address = targetAddress(context);
        if (address == null) return;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        BluetoothConnectionMonitor.query(context, adapter, address, status -> {
            new ConnectionStateStore(context).update(address, status);
            updateAll(context);
        });
    }

    private static String targetAddress(Context context) {
        SleepLockBackupStore.Backup backup = new SleepLockBackupStore(context).load();
        return backup == null
                ? new LockStateStore(context).load().deviceAddress()
                : backup.address();
    }
}
