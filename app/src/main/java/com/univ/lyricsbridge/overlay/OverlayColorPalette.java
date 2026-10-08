package com.univ.lyricsbridge.overlay;

import com.univ.lyricsbridge.data.AppSettings;

public final class OverlayColorPalette {
    public static final int DEFAULT_SUNG_COLOR = AppSettings.DEFAULT_OVERLAY_SUNG_COLOR;
    public static final int DEFAULT_UNSUNG_COLOR = AppSettings.DEFAULT_OVERLAY_UNSUNG_COLOR;
    public static final int DEFAULT_CURRENT_LINE_COLOR = AppSettings.DEFAULT_OVERLAY_CURRENT_LINE_COLOR;
    public static final int CURRENT_LINE_COLOR = DEFAULT_CURRENT_LINE_COLOR;

    private static final String[] LABELS = {
            "浅灰", "白色", "金黄", "天蓝", "青绿", "绿色", "橙色", "粉色", "紫色", "红色"
    };
    private static final int[] COLORS = {
            DEFAULT_SUNG_COLOR,
            DEFAULT_UNSUNG_COLOR,
            DEFAULT_CURRENT_LINE_COLOR,
            0xFF72B7FF,
            0xFF55D6BE,
            0xFF8BD17C,
            0xFFFFA45B,
            0xFFFF8DB1,
            0xFFB39DDB,
            0xFFFF6B6B
    };

    private OverlayColorPalette() {
    }

    public static int size() {
        return COLORS.length;
    }

    public static String labelAt(int index) {
        return LABELS[index];
    }

    public static int colorAt(int index) {
        return COLORS[index];
    }

    public static String[] labels() {
        return LABELS.clone();
    }

    public static int indexOfColor(int color) {
        for (int index = 0; index < COLORS.length; index++) {
            if (COLORS[index] == color) return index;
        }
        return -1;
    }
}
