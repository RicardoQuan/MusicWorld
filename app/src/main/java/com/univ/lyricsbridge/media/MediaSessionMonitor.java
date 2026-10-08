package com.univ.lyricsbridge.media;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.model.TrackInfo;
import java.util.List;

public final class MediaSessionMonitor {
    private static volatile String diagnosticStatus = "等待通知监听服务连接";
    private final Context appContext;
    private final ComponentName listenerComponent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            controllers -> selectNetEaseSession(controllers);
    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publishCurrent();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publishCurrent();
        }

        @Override
        public void onSessionDestroyed() {
            refreshSessions();
        }
    };

    private MediaSessionManager sessionManager;
    private MediaController activeController;
    private boolean started;

    public static String getDiagnosticStatus() {
        return diagnosticStatus;
    }

    public MediaSessionMonitor(Context context) {
        appContext = context.getApplicationContext();
        listenerComponent = new ComponentName(appContext, LyricsNotificationListenerService.class);
    }

    public void start() {
        if (started) return;
        started = true;
        sessionManager = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessionManager == null) {
            diagnosticStatus = "设备未提供媒体会话服务";
            MediaStateStore.publish(MediaStateStore.State.noPlayer());
            return;
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, mainHandler);
            refreshSessions();
        } catch (SecurityException ignored) {
            diagnosticStatus = "系统拒绝读取媒体会话，请检查通知使用权";
            MediaStateStore.publish(MediaStateStore.State.noPlayer());
        }
    }

    public void stop() {
        if (!started) return;
        started = false;
        detachController();
        if (sessionManager != null) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (RuntimeException ignored) {
                // The listener can already be detached while Android is shutting down the service.
            }
        }
        sessionManager = null;
        diagnosticStatus = "通知监听服务未连接";
        MediaStateStore.publish(MediaStateStore.State.noPlayer());
    }

    private void refreshSessions() {
        if (!started || sessionManager == null) return;
        try {
            selectNetEaseSession(sessionManager.getActiveSessions(listenerComponent));
        } catch (SecurityException ignored) {
            detachController();
            diagnosticStatus = "系统拒绝读取媒体会话，请检查通知使用权";
            MediaStateStore.publish(MediaStateStore.State.noPlayer());
        }
    }

    private void selectNetEaseSession(List<MediaController> controllers) {
        if (!started) return;
        MediaController selected = null;
        if (controllers != null) {
            for (MediaController candidate : controllers) {
                if (candidate != null && NetEasePackageFilter.accepts(candidate.getPackageName())) {
                    selected = candidate;
                    break;
                }
            }
        }
        if (selected == null) {
            detachController();
            diagnosticStatus = describeUnmatchedSessions(controllers);
            MediaStateStore.publish(MediaStateStore.State.noPlayer());
            return;
        }
        if (activeController == null
                || !activeController.getSessionToken().equals(selected.getSessionToken())) {
            detachController();
            activeController = selected;
            try {
                activeController.registerCallback(controllerCallback, mainHandler);
            } catch (RuntimeException ignored) {
                activeController = null;
                diagnosticStatus = "已发现网易云会话，但系统未允许订阅播放状态";
                MediaStateStore.publish(MediaStateStore.State.noPlayer());
                return;
            }
        }
        diagnosticStatus = "已匹配网易云媒体会话";
        publishCurrent();
    }

    private static String describeUnmatchedSessions(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) return "系统当前没有活动媒体会话";
        StringBuilder packages = new StringBuilder();
        int shown = 0;
        for (MediaController controller : controllers) {
            if (controller == null) continue;
            if (shown > 0) packages.append("、");
            packages.append(controller.getPackageName());
            shown++;
            if (shown == 3) break;
        }
        return "系统报告 " + controllers.size() + " 个媒体会话，未匹配网易云包名："
                + (packages.length() == 0 ? "未知" : packages);
    }

    private void publishCurrent() {
        MediaController controller = activeController;
        if (!started || controller == null) {
            MediaStateStore.publish(MediaStateStore.State.noPlayer());
            return;
        }

        MediaMetadata metadata = controller.getMetadata();
        String title = metadata == null ? "" : value(metadata, MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata == null ? "" : value(metadata, MediaMetadata.METADATA_KEY_ARTIST);
        String album = metadata == null ? "" : value(metadata, MediaMetadata.METADATA_KEY_ALBUM);
        long durationMs = metadata == null || !metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)
                ? 0 : Math.max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        TrackInfo track = new TrackInfo(title, artist, album, controller.getPackageName(), durationMs);

        PlaybackState playbackState = controller.getPlaybackState();
        long now = SystemClock.elapsedRealtime();
        long positionMs = playbackState == null ? 0 : Math.max(0, playbackState.getPosition());
        long updateTimeMs = playbackState == null ? now : playbackState.getLastPositionUpdateTime();
        if (updateTimeMs <= 0) updateTimeMs = now;
        boolean playing = playbackState != null
                && (playbackState.getState() == PlaybackState.STATE_PLAYING
                || playbackState.getState() == PlaybackState.STATE_FAST_FORWARDING
                || playbackState.getState() == PlaybackState.STATE_REWINDING);
        float speed = playbackState == null ? 1.0f : playbackState.getPlaybackSpeed();
        PlaybackSnapshot snapshot = new PlaybackSnapshot(positionMs, updateTimeMs, playing, speed, durationMs);
        MediaStateStore.publish(new MediaStateStore.State(true, track, snapshot));
    }

    private static String value(MediaMetadata metadata, String key) {
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString();
    }

    private void detachController() {
        if (activeController != null) {
            try {
                activeController.unregisterCallback(controllerCallback);
            } catch (RuntimeException ignored) {
                // A destroyed controller no longer has callbacks to unregister.
            }
            activeController = null;
        }
    }
}
