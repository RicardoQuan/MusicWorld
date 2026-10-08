package com.univ.lyricsbridge.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.univ.lyricsbridge.MainActivity;
import com.univ.lyricsbridge.data.AppSettings;
import com.univ.lyricsbridge.data.CarStateStore;
import com.univ.lyricsbridge.overlay.OverlayColorPalette;
import com.univ.lyricsbridge.service.CarReceiverService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class CarModeActivity extends Activity {
    private static final int REQUEST_BLUETOOTH_CONNECT = 201;
    private static final int REQUEST_POST_NOTIFICATIONS = 202;
    private static final int REQUEST_ENABLE_BLUETOOTH = 203;
    private static final int BG = Color.rgb(15, 25, 35);
    private static final int CARD = Color.rgb(27, 43, 56);
    private static final int WHITE = Color.rgb(244, 248, 250);
    private static final int MUTED = Color.rgb(169, 186, 198);
    private static final int GOLD = Color.rgb(242, 205, 105);
    private static final int TEAL = Color.rgb(54, 148, 135);

    private TextView permissionStatus;
    private TextView connectionStatus;
    private TextView trackStatus;
    private TextView selectedDeviceStatus;
    private Button devicePickerButton;
    private List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private Switch autoConnectSwitch;
    private TextView fontSizeValue;
    private TextView opacityValue;
    private Button sungColorButton;
    private Button unsungColorButton;
    private Button currentLineColorButton;
    private String selectedAddress = "";
    private String lastAutoConnectAddress = "";
    private String pendingBluetoothEnableAddress = "";
    private boolean bluetoothStateReceiverRegistered;
    private Runnable pendingPermissionAction;
    private final CarStateStore.Listener stateListener = this::renderState;
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_TURNING_ON && connectionStatus != null) {
                connectionStatus.setText("正在开启蓝牙…");
            } else if (state == BluetoothAdapter.STATE_ON && !pendingBluetoothEnableAddress.isEmpty()) {
                String address = pendingBluetoothEnableAddress;
                pendingBluetoothEnableAddress = "";
                startRememberedConnection(address);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedAddress = AppSettings.getPairedDeviceAddress(this);
        setContentView(buildPage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        CarStateStore.addListener(stateListener);
        updatePermissionStatus();
        renderState(CarStateStore.current());
        refreshPairedDevices();
        maybeAutoConnectOnEntry();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothStateReceiver, filter);
        }
        bluetoothStateReceiverRegistered = true;
    }

    @Override
    protected void onPause() {
        CarStateStore.removeListener(stateListener);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (bluetoothStateReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver);
            bluetoothStateReceiverRegistered = false;
        }
        super.onStop();
    }

    private View buildPage() {
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24));
        page.setBackgroundColor(BG);

        TextView title = text("车机显示设置", 26, WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(page, title, 0);

        LinearLayout statusCard = card();
        connectionStatus = text("蓝牙尚未连接", 17, GOLD);
        add(statusCard, connectionStatus, 0);
        trackStatus = text("等待手机歌曲与时间戳歌词", 14, MUTED);
        trackStatus.setMaxLines(2);
        add(statusCard, trackStatus, dp(7));
        permissionStatus = text("正在检查权限…", 13, MUTED);
        add(statusCard, permissionStatus, dp(7));
        add(page, statusCard, dp(12));

        boolean wide = CarLayoutPolicy.useTwoColumns(
                getResources().getConfiguration().screenWidthDp,
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout devicesColumn = new LinearLayout(this);
        devicesColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout devicesCard = card();
        TextView devicesTitle = text("已配对蓝牙设备", 16, WHITE);
        devicesTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(devicesCard, devicesTitle, 0);
        selectedDeviceStatus = text("选择一次后会记住上次使用的手机", 13, MUTED);
        add(devicesCard, selectedDeviceStatus, dp(5));
        devicePickerButton = button("选择已配对手机", TEAL, this::showDevicePicker);
        add(devicesCard, devicePickerButton, dp(8));
        add(devicesColumn, devicesCard, 0);

        LinearLayout settingsColumn = new LinearLayout(this);
        settingsColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout settingsCard = card();
        TextView settingsTitle = text("显示与启动", 16, WHITE);
        settingsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(settingsCard, settingsTitle, 0);

        autoConnectSwitch = new Switch(this);
        autoConnectSwitch.setText("车机开机自动连接");
        autoConnectSwitch.setTextColor(WHITE);
        autoConnectSwitch.setTextSize(15);
        autoConnectSwitch.setChecked(AppSettings.isAutoConnectOnBoot(this));
        autoConnectSwitch.setOnCheckedChangeListener((button, checked) ->
                AppSettings.setAutoConnectOnBoot(this, checked));
        add(settingsCard, autoConnectSwitch, dp(8));

        fontSizeValue = text("歌词字号　" + AppSettings.getOverlayTextSize(this) + " sp", 14, WHITE);
        add(settingsCard, fontSizeValue, dp(10));
        SeekBar fontSizeBar = new SeekBar(this);
        fontSizeBar.setMax(24);
        fontSizeBar.setProgress(AppSettings.getOverlayTextSize(this) - 18);
        fontSizeBar.setProgressTintList(ColorStateList.valueOf(TEAL));
        fontSizeBar.setThumbTintList(ColorStateList.valueOf(GOLD));
        fontSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = 18 + progress;
                fontSizeValue.setText("歌词字号　" + size + " sp");
                if (fromUser) {
                    AppSettings.setOverlayTextSize(CarModeActivity.this, size);
                    refreshVisibleOverlay();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        add(settingsCard, fontSizeBar, dp(2));

        int scrollDuration = AppSettings.getOverlayScrollDuration(this);
        TextView scrollSpeedValue = text(scrollSpeedLabel(scrollDuration), 14, WHITE);
        add(settingsCard, scrollSpeedValue, dp(8));
        SeekBar scrollSpeedBar = new SeekBar(this);
        scrollSpeedBar.setMax(18);
        scrollSpeedBar.setProgress((scrollDuration - 100) / 50);
        scrollSpeedBar.setProgressTintList(ColorStateList.valueOf(TEAL));
        scrollSpeedBar.setThumbTintList(ColorStateList.valueOf(GOLD));
        scrollSpeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int duration = 100 + progress * 50;
                scrollSpeedValue.setText(scrollSpeedLabel(duration));
                if (fromUser) {
                    AppSettings.setOverlayScrollDuration(CarModeActivity.this, duration);
                    refreshVisibleOverlay();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        add(settingsCard, scrollSpeedBar, dp(2));

        int opacityPercent = Math.round(AppSettings.getOverlayTextOpacity(this) * 100);
        opacityValue = text("歌词文字透明度　" + opacityPercent + "%", 14, WHITE);
        add(settingsCard, opacityValue, dp(8));
        SeekBar opacityBar = new SeekBar(this);
        opacityBar.setMax(60);
        opacityBar.setProgress(opacityPercent - 40);
        opacityBar.setProgressTintList(ColorStateList.valueOf(TEAL));
        opacityBar.setThumbTintList(ColorStateList.valueOf(GOLD));
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = 40 + progress;
                opacityValue.setText("歌词文字透明度　" + percent + "%");
                if (fromUser) {
                    AppSettings.setOverlayTextOpacity(CarModeActivity.this, percent / 100f);
                    refreshVisibleOverlay();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        add(settingsCard, opacityBar, dp(2));

        TextView colorHint = text("逐字歌词中已唱字使用当前行颜色，未唱字可单独设置", 12, MUTED);
        add(settingsCard, colorHint, dp(7));
        currentLineColorButton = colorChoiceButton("当前行",
                AppSettings.getOverlayCurrentLineColor(this), ColorTarget.CURRENT_LINE);
        add(settingsCard, currentLineColorButton, dp(4));
        sungColorButton = colorChoiceButton("已唱（上一句）", AppSettings.getOverlaySungColor(this), ColorTarget.SUNG);
        add(settingsCard, sungColorButton, dp(4));
        unsungColorButton = colorChoiceButton("未唱（下一句）", AppSettings.getOverlayUnsungColor(this), ColorTarget.UNSUNG);
        add(settingsCard, unsungColorButton, dp(4));

        Button resetPositionButton = button("复位歌词位置（屏幕居中）", Color.rgb(51, 68, 79), this::resetOverlayPosition);
        add(settingsCard, resetPositionButton, dp(8));

        Button overlayPermission = button("开启悬浮窗权限", TEAL, this::openOverlaySettings);
        add(settingsCard, overlayPermission, dp(8));
        add(settingsColumn, settingsCard, 0);

        LinearLayout actions = card();
        Button connectButton = button("连接并显示歌词", TEAL, this::connectAndShow);
        add(actions, connectButton, 0);
        Button hideButton = button("隐藏歌词", Color.rgb(51, 68, 79), this::hideOverlay);
        add(actions, hideButton, dp(6));
        Button roleButton = button("返回角色选择", Color.rgb(51, 68, 79), this::returnToRoleSelection);
        add(actions, roleButton, dp(6));
        add(settingsColumn, actions, dp(10));

        if (wide) {
            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            leftParams.setMargins(0, dp(12), dp(8), 0);
            columns.addView(devicesColumn, leftParams);
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightParams.setMargins(dp(8), dp(12), 0, 0);
            columns.addView(settingsColumn, rightParams);
        } else {
            add(columns, devicesColumn, dp(12));
            add(columns, settingsColumn, dp(10));
        }
        add(page, columns, 0);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
    }

    private void refreshPairedDevices() {
        if (devicePickerButton == null) return;
        if (!hasBluetoothConnectPermission()) {
            if (pendingPermissionAction == null) {
                requestBluetoothConnectPermission(this::refreshPairedDevices);
            }
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        pairedDevices.clear();
        if (adapter == null) {
            selectedDeviceStatus.setText("此设备没有蓝牙适配器");
            devicePickerButton.setEnabled(false);
            return;
        }
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            List<BluetoothDevice> devices = bonded == null
                    ? new ArrayList<>() : new ArrayList<>(bonded);
            pairedDevices.addAll(devices);
            Collections.sort(devices, Comparator.comparing(device -> {
                String name = device.getName();
                return name == null ? device.getAddress() : name;
            }, String.CASE_INSENSITIVE_ORDER));
            if (devices.isEmpty()) {
                selectedDeviceStatus.setText("还没有已配对设备");
                devicePickerButton.setText("打开系统蓝牙配对");
                devicePickerButton.setEnabled(true);
                return;
            }
            devicePickerButton.setText("选择已配对设备（" + devices.size() + "）");
            devicePickerButton.setEnabled(true);
            boolean rememberedDeviceFound = false;
            String selectedName = "";
            for (BluetoothDevice device : devices) {
                String address = device.getAddress();
                String name = safeDeviceName(device);
                boolean selected = selectedAddress.equals(address);
                if (selected) {
                    rememberedDeviceFound = true;
                    selectedName = name;
                }
            }
            if (!selectedAddress.isEmpty() && !rememberedDeviceFound) {
                selectedDeviceStatus.setText("上次使用的手机暂未配对；重新配对后会继续记住它");
            } else if (rememberedDeviceFound) {
                selectedDeviceStatus.setText("已记住：" + selectedName + "；可在列表中切换");
            } else {
                selectedDeviceStatus.setText("请选择一台手机，之后会自动记住");
            }
        } catch (SecurityException exception) {
            selectedDeviceStatus.setText("需要蓝牙连接权限才能读取已配对设备");
        }
    }

    private void showDevicePicker() {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothConnectPermission(this::refreshPairedDevices);
            return;
        }
        if (pairedDevices.isEmpty()) {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            return;
        }
        String[] labels = new String[pairedDevices.size()];
        int checked = -1;
        for (int i = 0; i < pairedDevices.size(); i++) {
            BluetoothDevice device = pairedDevices.get(i);
            labels[i] = safeDeviceName(device) + "\n" + device.getAddress();
            if (selectedAddress.equals(device.getAddress())) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择已配对手机")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    BluetoothDevice device = pairedDevices.get(which);
                    selectDevice(device.getAddress(), safeDeviceName(device));
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "蓝牙设备" : name;
        } catch (SecurityException ignored) {
            return "蓝牙设备";
        }
    }

    private void selectDevice(String address, String name) {
        selectedAddress = address;
        AppSettings.setPairedDeviceAddress(this, address);
        lastAutoConnectAddress = "";
        selectedDeviceStatus.setText("已记住：" + (name == null ? "蓝牙手机" : name));
        refreshPairedDevices();
        maybeAutoConnectOnEntry();
    }

    private static String scrollSpeedLabel(int durationMs) {
        return "歌词滚动时长　" + durationMs + " 毫秒（数值越大越慢）";
    }

    private void connectAndShow() {
        if (selectedAddress.isEmpty()) {
            connectionStatus.setText("请先选择一台已配对手机");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            permissionStatus.setText("请先开启悬浮窗权限，然后再连接");
            openOverlaySettings();
            return;
        }
        withConnectionPermissions(() -> {
            AppSettings.setPairedDeviceAddress(this, selectedAddress);
            Intent service = new Intent(this, CarReceiverService.class)
                    .putExtra(CarReceiverService.EXTRA_DEVICE_ADDRESS, selectedAddress)
                    .putExtra(CarReceiverService.EXTRA_AUTO_SHOW_OVERLAY, true);
            startServiceCompat(service);
        });
    }

    private void maybeAutoConnectOnEntry() {
        if (selectedAddress.isEmpty() || selectedAddress.equals(lastAutoConnectAddress)
                || pendingPermissionAction != null) return;
        lastAutoConnectAddress = selectedAddress;
        if (!hasBluetoothConnectPermission()) {
            pendingPermissionAction = () -> ensureBluetoothEnabledAndConnect(selectedAddress);
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
            return;
        }
        ensureBluetoothEnabledAndConnect(selectedAddress);
    }

    private void ensureBluetoothEnabledAndConnect(String address) {
        if (address == null || address.isEmpty() || !address.equals(selectedAddress)) return;
        if (!hasBluetoothConnectPermission()) {
            pendingPermissionAction = () -> ensureBluetoothEnabledAndConnect(address);
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            connectionStatus.setText("此设备没有蓝牙适配器");
            return;
        }
        if (adapter.isEnabled()) {
            pendingBluetoothEnableAddress = "";
            startRememberedConnection(address);
            return;
        }
        pendingBluetoothEnableAddress = address;
        connectionStatus.setText("正在开启蓝牙…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                        REQUEST_ENABLE_BLUETOOTH);
            } catch (RuntimeException exception) {
                pendingBluetoothEnableAddress = "";
                connectionStatus.setText("请先在系统设置中开启蓝牙");
            }
            return;
        }
        boolean requested;
        try {
            requested = adapter.enable();
        } catch (SecurityException exception) {
            pendingBluetoothEnableAddress = "";
            connectionStatus.setText("系统未允许应用开启蓝牙，请在系统设置中手动开启");
            return;
        }
        if (adapter.isEnabled()) {
            pendingBluetoothEnableAddress = "";
            startRememberedConnection(address);
        } else if (!requested && adapter.getState() != BluetoothAdapter.STATE_TURNING_ON) {
            pendingBluetoothEnableAddress = "";
            connectionStatus.setText("无法自动开启蓝牙，请检查系统蓝牙设置");
        }
    }

    private void startRememberedConnection(String address) {
        if (address == null || address.isEmpty() || !address.equals(selectedAddress)) return;
        withConnectionPermissions(() -> {
            if (!address.equals(AppSettings.getPairedDeviceAddress(this))) return;
            Intent service = new Intent(this, CarReceiverService.class)
                    .putExtra(CarReceiverService.EXTRA_DEVICE_ADDRESS, address)
                    .putExtra(CarReceiverService.EXTRA_AUTO_SHOW_OVERLAY, true);
            startServiceCompat(service);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ENABLE_BLUETOOTH) return;
        String address = pendingBluetoothEnableAddress;
        pendingBluetoothEnableAddress = "";
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            startRememberedConnection(address);
        } else if (connectionStatus != null) {
            connectionStatus.setText("蓝牙未开启，暂时无法自动连接");
        }
    }

    private void hideOverlay() {
        if (!CarReceiverService.RUNNING) return;
        startServiceCompat(new Intent(this, CarReceiverService.class)
                .setAction(CarReceiverService.ACTION_HIDE_OVERLAY));
    }

    private void returnToRoleSelection() {
        AppSettings.setRole(this, "");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void refreshVisibleOverlay() {
        if (!CarReceiverService.RUNNING) return;
        startServiceCompat(new Intent(this, CarReceiverService.class)
                .setAction(CarReceiverService.ACTION_REFRESH_OVERLAY));
    }

    private void resetOverlayPosition() {
        AppSettings.clearOverlayPosition(this);
        if (CarReceiverService.RUNNING) {
            startServiceCompat(new Intent(this, CarReceiverService.class)
                    .setAction(CarReceiverService.ACTION_RESET_OVERLAY_POSITION));
        }
        Toast.makeText(this, "歌词位置已复位到屏幕中央", Toast.LENGTH_SHORT).show();
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void withConnectionPermissions(Runnable action) {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothConnectPermission(() -> withConnectionPermissions(action));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = () -> withConnectionPermissions(action);
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            return;
        }
        action.run();
    }

    private void requestBluetoothConnectPermission(Runnable action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            action.run();
            return;
        }
        pendingPermissionAction = action;
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void startServiceCompat(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void updatePermissionStatus() {
        String bluetooth = hasBluetoothConnectPermission() ? "蓝牙权限已开" : "需要蓝牙连接权限";
        String overlay = Settings.canDrawOverlays(this) ? "悬浮窗权限已开" : "需要开启悬浮窗权限";
        permissionStatus.setText(bluetooth + "　·　" + overlay);
        permissionStatus.setTextColor(Settings.canDrawOverlays(this) && hasBluetoothConnectPermission()
                ? Color.rgb(128, 220, 160) : GOLD);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BLUETOOTH_CONNECT && requestCode != REQUEST_POST_NOTIFICATIONS) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        Runnable pending = pendingPermissionAction;
        pendingPermissionAction = null;
        updatePermissionStatus();
        if (granted && pending != null) {
            pending.run();
            if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
                refreshPairedDevices();
                maybeAutoConnectOnEntry();
            }
        }
        else if (!granted) permissionStatus.setText("权限未授予，暂时无法连接或显示歌词");
    }

    private void renderState(CarStateStore.State state) {
        if (state == null || connectionStatus == null) return;
        connectionStatus.setText(state.getConnectionStatus());
        if (state.getTrack() == null) {
            trackStatus.setText("等待手机歌曲与时间戳歌词");
        } else {
            String detail = state.getTrack().getTitle() + " — " + state.getTrack().getArtist();
            if (!state.getLines().isEmpty()) detail += "\n已收到 " + state.getLines().size() + " 行同步歌词";
            trackStatus.setText(detail);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(14));
        card.setBackground(background);
        return card;
    }

    private Button button(String label, int color, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(WHITE);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private Button colorChoiceButton(String label, int color, ColorTarget target) {
        Button choice = button("", color, () -> showColorPicker(label, target));
        updateColorChoiceButton(choice, label, color);
        return choice;
    }

    private void showColorPicker(String label, ColorTarget target) {
        int selectedColor;
        switch (target) {
            case SUNG:
                selectedColor = AppSettings.getOverlaySungColor(this);
                break;
            case UNSUNG:
                selectedColor = AppSettings.getOverlayUnsungColor(this);
                break;
            case CURRENT_LINE:
                selectedColor = AppSettings.getOverlayCurrentLineColor(this);
                break;
            default:
                throw new IllegalStateException("Unknown color target: " + target);
        }
        int selectedIndex = OverlayColorPalette.indexOfColor(selectedColor);
        new AlertDialog.Builder(this)
                .setTitle("选择" + label + "颜色")
                .setSingleChoiceItems(OverlayColorPalette.labels(), selectedIndex, (dialog, which) -> {
                    int color = OverlayColorPalette.colorAt(which);
                    switch (target) {
                        case SUNG:
                            AppSettings.setOverlaySungColor(this, color);
                            updateColorChoiceButton(sungColorButton, "已唱（上一句）", color);
                            break;
                        case UNSUNG:
                            AppSettings.setOverlayUnsungColor(this, color);
                            updateColorChoiceButton(unsungColorButton, "未唱（下一句）", color);
                            break;
                        case CURRENT_LINE:
                            AppSettings.setOverlayCurrentLineColor(this, color);
                            updateColorChoiceButton(currentLineColorButton, "当前行", color);
                            break;
                    }
                    refreshVisibleOverlay();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private enum ColorTarget {
        SUNG,
        UNSUNG,
        CURRENT_LINE
    }

    private void updateColorChoiceButton(Button button, String label, int color) {
        int index = OverlayColorPalette.indexOfColor(color);
        String colorName = index >= 0 ? OverlayColorPalette.labelAt(index) : "自定义";
        button.setText(label + "：" + colorName);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setTextColor(contrastTextColor(color));
    }

    private int contrastTextColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
        return luminance > 0.62 ? Color.rgb(18, 24, 34) : Color.WHITE;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void add(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        parent.addView(child, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
