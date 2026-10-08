package com.univ.lyricsbridge.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    public static final int DEFAULT_OVERLAY_SUNG_COLOR = 0xFFADB8C2;
    public static final int DEFAULT_OVERLAY_UNSUNG_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_OVERLAY_CURRENT_LINE_COLOR = 0xFFFFCD53;
    private static final String PREFS = "lyrics_bridge";
    private static final String ROLE = "role";
    private static final String PAIRED_DEVICE_ADDRESS = "paired_device_address";
    private static final String AUTO_CONNECT_ON_BOOT = "auto_connect_on_boot";
    private static final String OVERLAY_TEXT_SIZE = "overlay_text_size";
    private static final String OVERLAY_OPACITY = "overlay_opacity";
    private static final String OVERLAY_SUNG_COLOR = "overlay_sung_color";
    private static final String OVERLAY_UNSUNG_COLOR = "overlay_unsung_color";
    private static final String OVERLAY_CURRENT_LINE_COLOR = "overlay_current_line_color";
    private static final String OVERLAY_SCROLL_DURATION = "overlay_scroll_duration";
    private static final String OVERLAY_X = "overlay_x";
    private static final String OVERLAY_Y = "overlay_y";
    private static final String OVERLAY_POSITION_SET = "overlay_position_set";

    private AppSettings() {
    }

    public static String getRole(Context context) {
        return preferences(context).getString(ROLE, "");
    }

    public static void setRole(Context context, String role) {
        preferences(context).edit().putString(ROLE, role == null ? "" : role).apply();
    }

    public static String getPairedDeviceAddress(Context context) {
        return preferences(context).getString(PAIRED_DEVICE_ADDRESS, "");
    }

    public static void setPairedDeviceAddress(Context context, String address) {
        preferences(context).edit().putString(PAIRED_DEVICE_ADDRESS, address == null ? "" : address).apply();
    }

    public static boolean isAutoConnectOnBoot(Context context) {
        return preferences(context).getBoolean(AUTO_CONNECT_ON_BOOT, true);
    }

    public static void setAutoConnectOnBoot(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(AUTO_CONNECT_ON_BOOT, enabled).apply();
    }

    public static int getOverlayTextSize(Context context) {
        return preferences(context).getInt(OVERLAY_TEXT_SIZE, 28);
    }

    public static void setOverlayTextSize(Context context, int sizeSp) {
        preferences(context).edit().putInt(OVERLAY_TEXT_SIZE, Math.max(18, Math.min(42, sizeSp))).apply();
    }

    public static float getOverlayOpacity(Context context) {
        return getOverlayTextOpacity(context);
    }

    public static void setOverlayOpacity(Context context, float opacity) {
        setOverlayTextOpacity(context, opacity);
    }

    public static float getOverlayTextOpacity(Context context) {
        return preferences(context).getFloat(OVERLAY_OPACITY, 0.90f);
    }

    public static void setOverlayTextOpacity(Context context, float opacity) {
        preferences(context).edit().putFloat(OVERLAY_OPACITY,
                Math.max(0.40f, Math.min(1.0f, opacity))).apply();
    }

    public static int getOverlaySungColor(Context context) {
        return preferences(context).getInt(OVERLAY_SUNG_COLOR, DEFAULT_OVERLAY_SUNG_COLOR);
    }

    public static void setOverlaySungColor(Context context, int color) {
        preferences(context).edit().putInt(OVERLAY_SUNG_COLOR, color).apply();
    }

    public static int getOverlayUnsungColor(Context context) {
        return preferences(context).getInt(OVERLAY_UNSUNG_COLOR, DEFAULT_OVERLAY_UNSUNG_COLOR);
    }

    public static void setOverlayUnsungColor(Context context, int color) {
        preferences(context).edit().putInt(OVERLAY_UNSUNG_COLOR, color).apply();
    }

    public static int getOverlayCurrentLineColor(Context context) {
        return preferences(context).getInt(
                OVERLAY_CURRENT_LINE_COLOR, DEFAULT_OVERLAY_CURRENT_LINE_COLOR);
    }

    public static void setOverlayCurrentLineColor(Context context, int color) {
        preferences(context).edit().putInt(OVERLAY_CURRENT_LINE_COLOR, color).apply();
    }

    public static int getOverlayScrollDuration(Context context) {
        return preferences(context).getInt(OVERLAY_SCROLL_DURATION, 350);
    }

    public static void setOverlayScrollDuration(Context context, int durationMs) {
        preferences(context).edit().putInt(OVERLAY_SCROLL_DURATION,
                Math.max(100, Math.min(1000, durationMs))).apply();
    }

    public static int getOverlayX(Context context) {
        return preferences(context).getInt(OVERLAY_X, 0);
    }

    public static int getOverlayY(Context context) {
        return preferences(context).getInt(OVERLAY_Y, 40);
    }

    public static boolean hasOverlayPosition(Context context) {
        return preferences(context).getBoolean(OVERLAY_POSITION_SET, false);
    }

    public static void clearOverlayPosition(Context context) {
        preferences(context).edit().remove(OVERLAY_X).remove(OVERLAY_Y)
                .putBoolean(OVERLAY_POSITION_SET, false).commit();
    }

    public static void setOverlayPosition(Context context, int x, int y) {
        preferences(context).edit().putInt(OVERLAY_X, Math.max(0, x)).putInt(OVERLAY_Y, Math.max(0, y))
                .putBoolean(OVERLAY_POSITION_SET, true).commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
