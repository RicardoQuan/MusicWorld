package com.univ.lyricsbridge.media;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

public final class LyricsNotificationListenerService extends NotificationListenerService {
    private static volatile boolean listenerConnected;
    private static long lastRebindRequestMs = -10000;
    private MediaSessionMonitor monitor;

    public static boolean isListenerConnected() {
        return listenerConnected;
    }

    public static synchronized void ensureListenerConnected(Context context) {
        if (listenerConnected) return;
        ComponentName component = new ComponentName(context, LyricsNotificationListenerService.class);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.isNotificationListenerAccessGranted(component)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastRebindRequestMs < 10000) return;
        lastRebindRequestMs = now;
        try {
            requestRebind(component);
            android.util.Log.i("LyricsBridge", "Requested notification listener reconnect");
        } catch (RuntimeException exception) {
            android.util.Log.w("LyricsBridge", "Notification listener reconnect failed", exception);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        listenerConnected = true;
        android.util.Log.i("LyricsBridge", "Notification listener connected");
        if (monitor != null) monitor.stop();
        monitor = new MediaSessionMonitor(this);
        monitor.start();
    }

    @Override
    public void onListenerDisconnected() {
        listenerConnected = false;
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        super.onListenerDisconnected();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ComponentName component = new ComponentName(this, LyricsNotificationListenerService.class);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.isNotificationListenerAccessGranted(component)) {
                requestRebind(component);
            }
        }
    }

    @Override
    public void onDestroy() {
        listenerConnected = false;
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        super.onDestroy();
    }
}
