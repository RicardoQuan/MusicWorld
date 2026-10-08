package com.univ.lyricsbridge.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;

final class ForegroundNotifications {
    private static final String CHANNEL_ID = "lyrics_bridge_connection";
    private static final int NOTIFICATION_ID = 73;

    private ForegroundNotifications() {
    }

    static void start(Service service, String title, String detail, String stopAction) {
        NotificationManager manager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "歌词蓝牙连接", NotificationManager.IMPORTANCE_LOW));
        }

        Notification notification = build(service, title, detail, stopAction);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            service.startForeground(NOTIFICATION_ID, notification);
        }
    }

    static void update(Service service, String title, String detail, String stopAction) {
        NotificationManager manager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, build(service, title, detail, stopAction));
    }

    private static Notification build(Service service, String title, String detail, String stopAction) {
        Intent stopIntent = new Intent(service, service.getClass()).setAction(stopAction);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent stopPendingIntent = PendingIntent.getService(service, stopAction.hashCode(), stopIntent, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(service, CHANNEL_ID) : new Notification.Builder(service);
        return builder
                .setSmallIcon(com.univ.lyricsbridge.R.drawable.ic_notification)
                .setLargeIcon(Icon.createWithResource(service, com.univ.lyricsbridge.R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "停止", stopPendingIntent).build())
                .build();
    }
}
