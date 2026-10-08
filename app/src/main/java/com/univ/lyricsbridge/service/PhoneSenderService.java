package com.univ.lyricsbridge.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import com.univ.lyricsbridge.lyric.NetEaseApiClient;
import com.univ.lyricsbridge.lyric.NetEaseLyricProvider;
import com.univ.lyricsbridge.lyric.NetEaseSessionStore;
import com.univ.lyricsbridge.lyric.LocalLyricStore;
import com.univ.lyricsbridge.lyric.LocalLrcLyricProvider;
import com.univ.lyricsbridge.lyric.LrclibApiClient;
import com.univ.lyricsbridge.lyric.LrclibLyricProvider;
import com.univ.lyricsbridge.lyric.FallbackLyricProvider;
import com.univ.lyricsbridge.lyric.LyricCache;
import com.univ.lyricsbridge.lyric.LyricLookupEngine;
import com.univ.lyricsbridge.lyric.LyricLookupResult;
import com.univ.lyricsbridge.lyric.LyricProvider;
import com.univ.lyricsbridge.media.MediaStateStore;
import com.univ.lyricsbridge.model.TrackInfo;
import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.sync.PlaybackSyncEngine;
import com.univ.lyricsbridge.transport.BluetoothMessage;
import com.univ.lyricsbridge.transport.PhoneBluetoothServer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PhoneSenderService extends Service implements MediaStateStore.Listener {
    public static final String ACTION_STOP = "com.univ.lyricsbridge.action.STOP_PHONE_SENDER";
    public static final String ACTION_REFRESH_CURRENT_TRACK = "com.univ.lyricsbridge.action.REFRESH_CURRENT_TRACK";
    public static volatile boolean RUNNING;
    public static volatile String STATUS = "发送服务尚未启动";
    private static final String NOTIFICATION_TITLE = "歌词桥手机发送端";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService lyricWorker = Executors.newSingleThreadExecutor();
    private final Runnable playbackTicker = new Runnable() {
        @Override
        public void run() {
            MediaStateStore.State state = latestState;
            sendCurrentPlayback(state);
            if (active) mainHandler.postDelayed(this, 500);
        }
    };

    private volatile boolean active;
    private volatile MediaStateStore.State latestState = MediaStateStore.State.noPlayer();
    private volatile String currentTrackKey = "";
    private volatile String currentLrc = "";
    private final PhoneSenderStatus senderStatus = new PhoneSenderStatus();
    private boolean notificationStarted;
    private PhoneBluetoothServer server;
    private BluetoothAdapter bluetoothAdapter;
    private boolean bluetoothReceiverRegistered;
    private boolean bluetoothEnableRequested;
    private LyricCache cache;
    private LyricLookupEngine lookupEngine;
    private NetEaseSessionStore sessions;

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        RUNNING = true;
        STATUS = senderStatus.getDisplayStatus();
        ForegroundNotifications.start(this, NOTIFICATION_TITLE, STATUS, ACTION_STOP);
        notificationStarted = true;
        cache = new LyricCache(this);
        sessions = new NetEaseSessionStore(this);
        LocalLrcLyricProvider localLrc = new LocalLrcLyricProvider(new LocalLyricStore(this));
        NetEaseLyricProvider netEase = new NetEaseLyricProvider(new NetEaseApiClient(), sessions::getCookie);
        LyricProvider providers = new FallbackLyricProvider(Arrays.asList(
                localLrc, netEase, new LrclibLyricProvider(new LrclibApiClient())));
        // Never read cached lyrics: entries do not retain their source and could be mislabeled.
        lookupEngine = new LyricLookupEngine(providers, cache, () -> false);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        server = new PhoneBluetoothServer(bluetoothAdapter,
                new PhoneBluetoothServer.Listener() {
                    @Override
                    public void onConnected(String remoteDeviceName) {
                        mainHandler.post(() -> onCarConnected(remoteDeviceName));
                    }

                    @Override
                    public void onDisconnected() {
                        mainHandler.post(() -> {
                            if (active) {
                                setBluetoothStatus("车机连接断开，正在重新等待连接");
                                ensureBluetoothServerStarted();
                                android.util.Log.i("LyricsBridge", "车机蓝牙已断开");
                            }
                        });
                    }

                    @Override
                    public void onMessage(BluetoothMessage message) {
                        if (message.getType() == BluetoothMessage.Type.HELLO) {
                            mainHandler.post(() -> android.util.Log.i("LyricsBridge",
                                    "车机已连接：" + message.getDeviceName()));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> {
                            android.util.Log.w("LyricsBridge", message);
                            if (active) handleBluetoothServerError(message);
                        });
                    }
                });
        MediaStateStore.addListener(this);
        registerBluetoothStateReceiver();
        server.start();
        recoverBluetoothIfNeeded();
        mainHandler.post(playbackTicker);
    }

    private void registerBluetoothStateReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bluetoothStateReceiver, filter);
        }
        bluetoothReceiverRegistered = true;
    }

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())
                    || !active) return;
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                setBluetoothStatus("手机蓝牙已断开，正在尝试恢复发送");
                if (state == BluetoothAdapter.STATE_OFF) recoverBluetoothIfNeeded();
            } else if (state == BluetoothAdapter.STATE_TURNING_ON) {
                setBluetoothStatus("手机蓝牙正在开启，稍后恢复发送");
            } else if (state == BluetoothAdapter.STATE_ON) {
                bluetoothEnableRequested = false;
                setBluetoothStatus("蓝牙已恢复，正在等待车机连接");
                ensureBluetoothServerStarted();
            }
        }
    };

    private void recoverBluetoothIfNeeded() {
        if (!active) return;
        BluetoothAdapter adapter = bluetoothAdapter;
        if (adapter == null) {
            setBluetoothStatus("此设备没有可用的蓝牙适配器");
            return;
        }
        try {
            int state = adapter.getState();
            if (state == BluetoothAdapter.STATE_ON) {
                ensureBluetoothServerStarted();
                return;
            }
            if (state != BluetoothAdapter.STATE_OFF || bluetoothEnableRequested) return;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                bluetoothEnableRequested = true;
                boolean accepted = adapter.enable();
                if (!accepted) {
                    bluetoothEnableRequested = false;
                    setBluetoothStatus("蓝牙未能自动开启，请手动开启手机蓝牙");
                } else {
                    setBluetoothStatus("正在重新开启手机蓝牙");
                }
            } else {
                setBluetoothStatus("手机蓝牙已关闭，请手动开启；开启后会自动恢复发送");
            }
        } catch (SecurityException exception) {
            bluetoothEnableRequested = false;
            setBluetoothStatus("缺少蓝牙权限，请授予附近设备权限后重试");
        }
    }

    private void ensureBluetoothServerStarted() {
        PhoneBluetoothServer current = server;
        if (active && current != null) current.start();
    }

    private void handleBluetoothServerError(String message) {
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
                recoverBluetoothIfNeeded();
                return;
            }
        } catch (SecurityException exception) {
            setBluetoothStatus("缺少蓝牙权限，请授予附近设备权限后重试");
            return;
        }
        setBluetoothStatus("蓝牙异常：" + message);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_REFRESH_CURRENT_TRACK.equals(intent.getAction())) {
            currentTrackKey = "";
            onMediaState(latestState);
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onMediaState(MediaStateStore.State state) {
        latestState = state == null ? MediaStateStore.State.noPlayer() : state;
        if (!latestState.hasSession() || latestState.getTrack() == null) {
            if (!currentTrackKey.isEmpty()) {
                currentTrackKey = "";
                currentLrc = "";
                setPlaybackStatus("未检测到网易云音乐播放会话");
                sendStatus("NO_PLAYER", "手机未检测到网易云音乐播放");
            }
            return;
        }

        TrackInfo track = latestState.getTrack();
        String key = trackKey(track);
        if (!key.equals(currentTrackKey)) {
            currentTrackKey = key;
            currentLrc = "";
            setPlaybackStatus("正在匹配带时间戳歌词：" + track.getTitle());
            sendIfConnected(BluetoothMessage.track(track));
            sendStatus("LYRIC_SEARCHING", "正在匹配带时间戳歌词");
            if (track.getTitle().isEmpty() || track.getArtist().isEmpty()) {
                setPlaybackStatus("歌曲信息不足，无法匹配歌词");
                sendStatus("NO_METADATA", "歌曲信息不足，无法自动匹配歌词");
                return;
            }
            lyricWorker.execute(() -> lookupLyrics(track, key));
        }
    }

    private void lookupLyrics(TrackInfo track, String requestedKey) {
        LyricLookupEngine engine = lookupEngine;
        if (engine == null || !active) return;
        LyricLookupResult result = engine.lookup(track);
        mainHandler.post(() -> {
            if (!active || !requestedKey.equals(currentTrackKey)) return;
            if (result.getStatus() == LyricLookupResult.Status.FOUND) {
                currentLrc = result.getRawLrc();
                String source = result.getSource().displayName();
                String sourceText = source.isEmpty() ? "" : " · 歌词来源：" + source;
                setPlaybackStatus("已匹配到 " + result.getLines().size() + " 行时间戳歌词" + sourceText);
                sendIfConnected(BluetoothMessage.lyrics(currentLrc));
                sendCurrentPlayback(latestState);
            } else if (result.getStatus() == LyricLookupResult.Status.NO_MATCH) {
                setPlaybackStatus("未匹配到带时间戳歌词");
                sendStatus("LYRIC_NOT_FOUND", "未匹配到带时间戳歌词");
            } else {
                setPlaybackStatus(result.getMessage());
                sendStatus("LYRIC_ERROR", result.getMessage());
            }
        });
    }

    private void onCarConnected(String remoteDeviceName) {
        String deviceName = remoteDeviceName == null || remoteDeviceName.trim().isEmpty()
                ? "已配对设备" : remoteDeviceName.trim();
        setBluetoothStatus("已连接车机：" + deviceName);
        sendIfConnected(BluetoothMessage.hello("手机发送端"));
        MediaStateStore.State state = latestState;
        if (state.hasSession() && state.getTrack() != null) {
            sendIfConnected(BluetoothMessage.track(state.getTrack()));
            if (!currentLrc.isEmpty()) sendIfConnected(BluetoothMessage.lyrics(currentLrc));
            else sendStatus("LYRIC_SEARCHING", "正在查找可用歌词来源");
            sendCurrentPlayback(state);
        } else {
            setPlaybackStatus("等待网易云音乐播放");
            sendStatus("NO_PLAYER", "手机未检测到网易云音乐播放");
        }
        android.util.Log.i("LyricsBridge", "车机已连接：" + remoteDeviceName);
    }

    private void setBluetoothStatus(String status) {
        senderStatus.setBluetoothStatus(status);
        refreshPublishedStatus();
    }

    private void setPlaybackStatus(String status) {
        senderStatus.setPlaybackStatus(status);
        refreshPublishedStatus();
    }

    private void refreshPublishedStatus() {
        STATUS = senderStatus.getDisplayStatus();
        if (notificationStarted) {
            ForegroundNotifications.update(this, NOTIFICATION_TITLE, STATUS, ACTION_STOP);
        }
    }

    private void sendStatus(String code, String message) {
        sendIfConnected(BluetoothMessage.status(code, message));
    }

    private void sendCurrentPlayback(MediaStateStore.State state) {
        if (server == null || state == null || !state.hasSession() || state.getPlayback() == null) return;
        PlaybackSnapshot source = state.getPlayback();
        long now = SystemClock.elapsedRealtime();
        long position = PlaybackSyncEngine.positionAt(source, now);
        PlaybackSnapshot outgoing = new PlaybackSnapshot(position, now, source.isPlaying(),
                source.getSpeed(), source.getDurationMs());
        sendIfConnected(BluetoothMessage.playback(outgoing));
    }

    private void sendIfConnected(BluetoothMessage message) {
        PhoneBluetoothServer current = server;
        if (current != null) current.send(message);
    }

    private static String trackKey(TrackInfo track) {
        return track.getTitle() + "\u0000" + track.getArtist() + "\u0000" + track.getDurationMs();
    }

    @Override
    public void onDestroy() {
        active = false;
        RUNNING = false;
        STATUS = "发送服务已停止";
        notificationStarted = false;
        MediaStateStore.removeListener(this);
        mainHandler.removeCallbacks(playbackTicker);
        if (bluetoothReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothStateReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver may already have been removed during service shutdown.
            }
            bluetoothReceiverRegistered = false;
        }
        if (server != null) {
            server.close();
            server = null;
        }
        lyricWorker.shutdownNow();
        if (cache != null) {
            cache.close();
            cache = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
