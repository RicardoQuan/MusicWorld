package com.univ.lyricsbridge.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import com.univ.lyricsbridge.data.CarStateStore;
import com.univ.lyricsbridge.overlay.LyricsOverlayController;
import com.univ.lyricsbridge.transport.BluetoothMessage;
import com.univ.lyricsbridge.transport.CarBluetoothClient;
import com.univ.lyricsbridge.sync.PlaybackSyncEngine;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CarReceiverService extends Service {
    public static final String ACTION_STOP = "com.univ.lyricsbridge.action.STOP_CAR_RECEIVER";
    public static final String ACTION_SHOW_OVERLAY = "com.univ.lyricsbridge.action.SHOW_OVERLAY";
    public static final String ACTION_HIDE_OVERLAY = "com.univ.lyricsbridge.action.HIDE_OVERLAY";
    public static final String ACTION_REFRESH_OVERLAY = "com.univ.lyricsbridge.action.REFRESH_OVERLAY";
    public static final String ACTION_RESET_OVERLAY_POSITION = "com.univ.lyricsbridge.action.RESET_OVERLAY_POSITION";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_AUTO_SHOW_OVERLAY = "auto_show_overlay";
    public static volatile boolean RUNNING;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 5000, 10000, 30000};

    private final ExecutorService connectionWorker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean active;
    private volatile CarBluetoothClient currentClient;
    private volatile boolean connectStarted;
    private volatile boolean connected;
    private volatile String requestedAddress = "";
    private volatile boolean autoShowOverlay;
    private LyricsOverlayController overlayController;
    private final CarStateStore.Listener overlayListener = state -> {
        LyricsOverlayController controller = overlayController;
        if (controller != null) controller.update(state);
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ForegroundNotifications.start(this, "歌词桥车机显示端", "准备连接手机", ACTION_STOP);
        active = true;
        RUNNING = true;
        overlayController = new LyricsOverlayController(this, this::hideOverlayFromGesture);
        CarStateStore.addListener(overlayListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_SHOW_OVERLAY.equals(intent.getAction())) {
            autoShowOverlay = true;
            showOverlay();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_HIDE_OVERLAY.equals(intent.getAction())) {
            autoShowOverlay = false;
            if (overlayController != null) overlayController.hide();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_REFRESH_OVERLAY.equals(intent.getAction())) {
            if (overlayController != null) overlayController.refreshAppearance();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESET_OVERLAY_POSITION.equals(intent.getAction())) {
            if (overlayController != null) overlayController.resetPosition();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.hasExtra(EXTRA_AUTO_SHOW_OVERLAY)) {
            autoShowOverlay = intent.getBooleanExtra(EXTRA_AUTO_SHOW_OVERLAY, false);
        }
        String address = intent == null ? "" : intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
        if (address == null || address.isEmpty()) {
            CarStateStore.setStatus("请先选择已配对的手机", true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (connected && address.equals(requestedAddress)
                && CarOverlayStartPolicy.shouldShowAfterConnection(
                        autoShowOverlay, address, requestedAddress)) {
            showOverlay();
        }
        if (!connectStarted) {
            requestedAddress = address;
            connectStarted = true;
            CarStateStore.setStatus("正在连接手机…", true);
            connectionWorker.execute(this::connectLoop);
        } else if (!address.equals(requestedAddress)) {
            requestedAddress = address;
            CarStateStore.setStatus("正在切换连接手机…", true);
            CarBluetoothClient client = currentClient;
            if (client != null) client.close();
        }
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            CarStateStore.setStatus("请授予悬浮窗权限后显示歌词", false);
            return;
        }
        if (overlayController == null) {
            overlayController = new LyricsOverlayController(this, this::hideOverlayFromGesture);
        }
        if (!overlayController.show()) CarStateStore.setStatus("悬浮窗启动失败", false);
    }

    private void hideOverlayFromGesture() {
        autoShowOverlay = false;
        if (overlayController != null) overlayController.hide();
    }

    private void connectLoop() {
        int delayIndex = 0;
        String previousAddress = "";
        while (active) {
            String address = requestedAddress;
            if (address.isEmpty()) return;
            if (!address.equals(previousAddress)) {
                previousAddress = address;
                delayIndex = 0;
            }
            CarBluetoothClient client = new CarBluetoothClient(BluetoothAdapter.getDefaultAdapter());
            currentClient = client;
            try {
                String phoneName = client.connect(address);
                connected = true;
                CarStateStore.setSenderName(phoneName);
                CarStateStore.setStatus("已连接 " + phoneName, false);
                if (CarOverlayStartPolicy.shouldShowAfterConnection(
                        autoShowOverlay, address, requestedAddress)) {
                    mainHandler.post(this::showOverlay);
                }
                client.send(BluetoothMessage.hello(deviceName()));
                delayIndex = 0;
                while (active) {
                    BluetoothMessage message = client.read();
                    if (message == null) throw new IOException("Connection closed");
                    handle(message);
                }
            } catch (IOException | RuntimeException exception) {
                if (active) CarStateStore.setStatus("连接中断，正在重连…", true);
            } finally {
                connected = false;
                client.close();
                if (currentClient == client) currentClient = null;
            }
            if (active) {
                long delay = RETRY_DELAYS_MS[Math.min(delayIndex, RETRY_DELAYS_MS.length - 1)];
                delayIndex = Math.min(delayIndex + 1, RETRY_DELAYS_MS.length - 1);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handle(BluetoothMessage message) {
        switch (message.getType()) {
            case HELLO:
                if (!message.getDeviceName().isEmpty()) CarStateStore.setSenderName(message.getDeviceName());
                CarStateStore.setStatus("已连接 " + CarStateStore.current().getSenderName(), false);
                break;
            case TRACK:
                CarStateStore.setTrack(message.getTrack());
                break;
            case LYRICS:
                java.util.List<com.univ.lyricsbridge.model.LyricLine> lines =
                        com.univ.lyricsbridge.lyric.LrcParser.parse(message.getRawLrc());
                CarStateStore.setLyrics(lines);
                if (lines.isEmpty()) CarStateStore.setStatus("歌词没有时间戳", false);
                else if (autoShowOverlay) mainHandler.post(this::showOverlay);
                break;
            case PLAYBACK:
                CarStateStore.setPlayback(PlaybackSyncEngine.reanchorReceived(
                        message.getPlayback(), SystemClock.elapsedRealtime()));
                break;
            case STATUS:
                handleStatus(message);
                break;
            default:
                break;
        }
    }

    private void handleStatus(BluetoothMessage message) {
        String code = message.getStatusCode();
        if ("NO_PLAYER".equals(code)) {
            CarStateStore.setStatus("手机未检测到网易云音乐播放", true);
        } else if ("LYRIC_NOT_FOUND".equals(code)) {
            CarStateStore.setStatus("未找到带时间戳歌词", false);
        } else if ("LYRIC_ERROR".equals(code)) {
            CarStateStore.setStatus("歌词查询失败", false);
        } else if ("LYRIC_SEARCHING".equals(code)) {
            CarStateStore.setStatus("正在匹配带时间戳歌词…", false);
        } else if ("NO_METADATA".equals(code)) {
            CarStateStore.setStatus("歌曲信息不足，无法匹配歌词", false);
        } else if ("NETEASE_LOGIN_REQUIRED".equals(code)) {
            CarStateStore.setStatus("请先在手机发送端登录网易云", false);
        }
    }

    private String deviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    @Override
    public void onDestroy() {
        active = false;
        RUNNING = false;
        CarBluetoothClient client = currentClient;
        if (client != null) client.close();
        connectionWorker.shutdownNow();
        CarStateStore.removeListener(overlayListener);
        if (overlayController != null) {
            overlayController.dispose();
            overlayController = null;
        }
        CarStateStore.setStatus("未连接", true);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
