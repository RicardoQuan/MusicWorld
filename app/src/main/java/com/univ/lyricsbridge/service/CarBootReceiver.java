package com.univ.lyricsbridge.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.univ.lyricsbridge.data.AppSettings;
import com.univ.lyricsbridge.data.CarStateStore;

/** Restarts only the remembered car receiver after the head unit finishes booting. */
public final class CarBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        String role = AppSettings.getRole(context);
        boolean enabled = AppSettings.isAutoConnectOnBoot(context);
        String address = AppSettings.getPairedDeviceAddress(context);
        if (!CarBootStartPolicy.shouldStart(role, enabled, address)) return;

        Intent service = new Intent(context, CarReceiverService.class)
                .putExtra(CarReceiverService.EXTRA_DEVICE_ADDRESS, address)
                .putExtra(CarReceiverService.EXTRA_AUTO_SHOW_OVERLAY, true);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
            else context.startService(service);
        } catch (IllegalStateException | SecurityException exception) {
            CarStateStore.setStatus("开机自动连接未启动，请打开车机显示设置检查权限", false);
        }
    }
}
