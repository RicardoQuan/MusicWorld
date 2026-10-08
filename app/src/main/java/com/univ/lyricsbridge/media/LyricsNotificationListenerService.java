package com.univ.lyricsbridge.media;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.os.Build;
import android.service.notification.NotificationListenerService;

public final class LyricsNotificationListenerService extends NotificationListenerService {
    private static volatile boolean listenerConnected;
    private MediaSessionMonitor monitor;

    public static boolean isListenerConnected() {
        return listenerConnected;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        listenerConnected = true;
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
