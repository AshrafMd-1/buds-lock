package dev.ashraf.buds4readonly;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import dev.ashraf.buds4readonly.core.ConnectionStatus;
import dev.ashraf.buds4readonly.core.SleepLockStatus;
import dev.ashraf.buds4readonly.core.UiStateResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BLUETOOTH_PERMISSION_REQUEST = 7;

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothAdapter adapter;
    private LockStateStore stateStore;
    private SleepLockBackupStore backupStore;
    private ConnectionStateStore connectionStore;
    private Spinner deviceSpinner;
    private TextView stateTitle;
    private TextView stateMessage;
    private TextView deviceMessage;
    private View stateIconContainer;
    private ImageView stateIcon;
    private Button toggleButton;
    private Button refreshButton;
    private boolean suppressSelection;
    private boolean firstResume = true;
    private int devicePositionOffset;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { renderState(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        stateStore = new LockStateStore(this);
        backupStore = new SleepLockBackupStore(this);
        connectionStore = new ConnectionStateStore(this);
        recoverInterruptedState();
        setContentView(R.layout.activity_main);
        bindViews();
        applyInsets();
        renderState();
    }

    private void recoverInterruptedState() {
        if (stateStore.load().status() == SleepLockStatus.WORKING
                && !SleepLockService.isOperationRunning()) {
            stateStore.setStatus(backupStore.hasMarker()
                            ? SleepLockStatus.RECOVERY : SleepLockStatus.ERROR,
                    backupStore.hasMarker()
                            ? "The previous operation was interrupted. Your originals are saved; tap Restore controls."
                            : "The previous operation was interrupted before any recoverable change was recorded.");
        }
    }

    private void bindViews() {
        deviceSpinner = findViewById(R.id.device_spinner);
        stateTitle = findViewById(R.id.state_title);
        stateMessage = findViewById(R.id.state_message);
        deviceMessage = findViewById(R.id.device_message);
        stateIconContainer = findViewById(R.id.state_icon_container);
        stateIcon = findViewById(R.id.state_icon);
        toggleButton = findViewById(R.id.toggle_button);
        refreshButton = findViewById(R.id.refresh_button);
        toggleButton.setOnClickListener(view -> startToggle());
        refreshButton.setOnClickListener(view -> ensurePermissionAndLoadDevices());
        deviceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                int deviceIndex = position - devicePositionOffset;
                if (suppressSelection || deviceIndex < 0 || deviceIndex >= devices.size()) return;
                BluetoothDevice selected = devices.get(deviceIndex);
                stateStore.selectDevice(selected.getAddress());
                connectionStore.update(selected.getAddress(), ConnectionStatus.UNKNOWN);
                deviceMessage.setText(getString(R.string.device_ready, safeName(selected)));
                refreshConnection(selected.getAddress());
                BudsLockWidgetProvider.updateAll(MainActivity.this);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void applyInsets() {
        View root = findViewById(R.id.root);
        int horizontal = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        int vertical = getResources().getDimensionPixelSize(R.dimen.screen_vertical_padding);
        if (Build.VERSION.SDK_INT < 30) {
            root.setFitsSystemWindows(true);
            root.setPadding(horizontal, vertical, horizontal, vertical);
            return;
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            view.setPadding(horizontal, vertical + bars.top, horizontal, vertical + bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensurePermissionAndLoadDevices() {
        if (!hasBluetoothPermission()) {
            requestPermissions(new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN},
                    BLUETOOTH_PERMISSION_REQUEST);
            return;
        }
        loadDevices();
    }

    @SuppressLint("MissingPermission")
    private void loadDevices() {
        devices.clear();
        if (adapter == null) {
            deviceMessage.setText(R.string.no_bluetooth_adapter);
            renderState();
            return;
        }
        if (!adapter.isEnabled()) {
            deviceMessage.setText(R.string.enable_bluetooth);
            String address = targetAddress();
            if (address != null) connectionStore.update(address, ConnectionStatus.DISCONNECTED);
            renderState();
            return;
        }
        String preferredAddress = stateStore.load().deviceAddress();
        SleepLockBackupStore.Backup backup = backupStore.load();
        if (backup != null) preferredAddress = backup.address();
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (device.getAddress().equals(preferredAddress) || isSupportedBuds(device)) {
                devices.add(device);
            }
        }
        devices.sort(Comparator.comparing(device -> safeName(device).toLowerCase(Locale.ROOT)));
        List<String> labels = new ArrayList<>();
        int selection = findAddress(preferredAddress);
        devicePositionOffset = selection < 0 && devices.size() > 1 ? 1 : 0;
        if (devicePositionOffset == 1) labels.add(getString(R.string.choose_buds_short));
        for (BluetoothDevice device : devices) labels.add(safeName(device));

        suppressSelection = true;
        deviceSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        if (selection < 0 && devices.size() == 1) selection = 0;
        if (selection >= 0) {
            deviceSpinner.setSelection(selection + devicePositionOffset);
            BluetoothDevice selected = devices.get(selection);
            stateStore.selectDevice(selected.getAddress());
            deviceMessage.setText(getString(R.string.device_ready, safeName(selected)));
            refreshConnection(selected.getAddress());
        } else {
            deviceMessage.setText(R.string.choose_buds);
        }
        suppressSelection = false;
        deviceSpinner.setEnabled(backup == null && devices.size() > 1);
        renderState();
    }

    private int findAddress(String address) {
        if (address == null) return -1;
        for (int i = 0; i < devices.size(); i++) {
            if (address.equals(devices.get(i).getAddress())) return i;
        }
        return -1;
    }

    private static boolean isSupportedBuds(BluetoothDevice device) {
        return safeName(device).toLowerCase(Locale.ROOT).contains("oneplus buds 4");
    }

    private void startToggle() {
        if (!hasBluetoothPermission()) {
            ensurePermissionAndLoadDevices();
            return;
        }
        int deviceIndex = deviceSpinner.getSelectedItemPosition() - devicePositionOffset;
        if (deviceIndex < 0 || deviceIndex >= devices.size()) {
            deviceMessage.setText(R.string.choose_buds);
            return;
        }
        BluetoothDevice selected = devices.get(deviceIndex);
        stateStore.selectDevice(selected.getAddress());
        UiStateResolver.Presentation presentation = UiStateResolver.resolve(
                stateStore.load().status(), connectionStore.statusFor(targetAddress()));
        if (!presentation.toggleEnabled()) {
            refreshConnection(selected.getAddress());
            return;
        }
        toggleButton.setEnabled(false);
        Intent intent = new Intent(this, SleepLockService.class)
                .setAction(SleepLockService.ACTION_TOGGLE);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    private void renderState() {
        LockStateStore.Snapshot snapshot = stateStore.load();
        String targetAddress = targetAddress();
        ConnectionStatus connectionStatus = connectionStore.statusFor(targetAddress);
        SleepLockStatus displayStatus = SleepLockService.isOperationRunning()
                ? SleepLockStatus.WORKING : snapshot.status();
        UiStateResolver.Presentation presentation = UiStateResolver.resolve(
                displayStatus, connectionStatus);
        int accent = getColor(R.color.accent);
        int muted = getColor(R.color.unlocked_icon);
        int warning = getColor(R.color.warning);
        switch (presentation.visualState()) {
            case OFFLINE -> {
                stateIconContainer.setBackgroundResource(0);
                stateTitle.setText(connectionStatus == ConnectionStatus.UNKNOWN
                        ? R.string.checking_connection : R.string.buds_disconnected);
                stateIcon.setImageResource(R.drawable.ic_bluetooth_off);
                stateIcon.setImageTintList(ColorStateList.valueOf(muted));
                toggleButton.setText(backupStore.hasMarker()
                        ? R.string.restore_controls : R.string.block_controls);
                toggleButton.setBackgroundTintList(ColorStateList.valueOf(muted));
                stateMessage.setText(backupStore.hasMarker()
                        ? R.string.reconnect_to_restore : R.string.connect_to_use);
            }
            case LOCKED -> {
                stateIconContainer.setBackgroundResource(R.drawable.icon_circle_background);
                stateTitle.setText(R.string.controls_blocked);
                stateIcon.setImageResource(R.drawable.ic_lock_closed);
                stateIcon.setImageTintList(ColorStateList.valueOf(accent));
                toggleButton.setText(R.string.restore_controls);
                toggleButton.setBackgroundTintList(ColorStateList.valueOf(accent));
                stateMessage.setText(snapshot.message());
            }
            case RECOVERY -> {
                stateIconContainer.setBackgroundResource(R.drawable.icon_circle_background);
                stateTitle.setText(R.string.recovery_needed);
                stateIcon.setImageResource(R.drawable.ic_warning);
                stateIcon.setImageTintList(ColorStateList.valueOf(warning));
                toggleButton.setText(R.string.restore_controls);
                toggleButton.setBackgroundTintList(ColorStateList.valueOf(warning));
                stateMessage.setText(snapshot.message());
            }
            case ERROR -> {
                stateIconContainer.setBackgroundResource(R.drawable.icon_circle_background);
                stateTitle.setText(R.string.not_changed);
                stateIcon.setImageResource(R.drawable.ic_warning);
                stateIcon.setImageTintList(ColorStateList.valueOf(warning));
                toggleButton.setText(backupStore.hasMarker()
                        ? R.string.restore_controls : R.string.block_controls);
                toggleButton.setBackgroundTintList(ColorStateList.valueOf(accent));
                stateMessage.setText(snapshot.message());
            }
            case WORKING -> {
                stateIconContainer.setBackgroundResource(R.drawable.icon_circle_background);
                stateTitle.setText(R.string.working);
                stateIcon.setImageResource(R.drawable.ic_sync);
                stateIcon.setImageTintList(ColorStateList.valueOf(accent));
                toggleButton.setText(R.string.please_wait);
                toggleButton.setBackgroundTintList(ColorStateList.valueOf(accent));
                stateMessage.setText(snapshot.message());
            }
            case NORMAL -> {
                stateIconContainer.setBackgroundResource(0);
                stateTitle.setText(R.string.controls_normal);
                stateIcon.setImageResource(R.drawable.ic_lock_open);
                stateIcon.setImageTintList(ColorStateList.valueOf(muted));
                toggleButton.setText(R.string.block_controls);
                toggleButton.setBackgroundTintList(ColorStateList.valueOf(accent));
                stateMessage.setText(snapshot.message());
            }
        }
        stateIcon.setContentDescription(stateTitle.getText());
        toggleButton.setEnabled(presentation.toggleEnabled() && !devices.isEmpty());
        boolean working = presentation.visualState() == UiStateResolver.VisualState.WORKING;
        refreshButton.setEnabled(!working);
        deviceSpinner.setEnabled(!working && !backupStore.hasMarker() && devices.size() > 1);
    }

    private String targetAddress() {
        SleepLockBackupStore.Backup backup = backupStore.load();
        return backup == null ? stateStore.load().deviceAddress() : backup.address();
    }

    private void refreshConnection(String address) {
        if (address == null || !hasBluetoothPermission()) {
            renderState();
            return;
        }
        connectionStore.update(address, ConnectionStatus.UNKNOWN);
        renderState();
        BluetoothConnectionMonitor.query(this, adapter, address, status -> {
            if (!address.equals(targetAddress())) return;
            connectionStore.update(address, status);
            renderState();
            BudsLockWidgetProvider.updateAll(MainActivity.this);
        });
    }

    @SuppressLint("MissingPermission")
    private static String safeName(BluetoothDevice device) {
        String name = device.getName();
        return name == null ? "Unnamed device" : name;
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(SleepLockService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, filter);
    }

    @Override protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            ensurePermissionAndLoadDevices();
        } else if (hasBluetoothPermission()) {
            loadDevices();
        }
        renderState();
    }

    @Override protected void onStop() {
        unregisterReceiver(stateReceiver);
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST && hasBluetoothPermission()) loadDevices();
        else deviceMessage.setText(R.string.permission_required);
    }
}
