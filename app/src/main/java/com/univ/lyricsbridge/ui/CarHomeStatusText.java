package com.univ.lyricsbridge.ui;

public final class CarHomeStatusText {
    private CarHomeStatusText() {
    }

    public static String format(String connectionStatus) {
        String status = connectionStatus == null ? "" : connectionStatus.trim();
        if (status.isEmpty() || "未连接".equals(status)) {
            return "车机状态：未连接（请进入车机显示配置连接已配对手机）";
        }
        return "车机状态：" + status;
    }
}
