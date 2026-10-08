package com.univ.lyricsbridge.service;

final class PhoneSenderStatus {
    private String bluetoothStatus = "等待车机蓝牙连接";
    private String playbackStatus = "等待网易云音乐播放";

    synchronized void setBluetoothStatus(String status) {
        bluetoothStatus = clean(status, "等待车机蓝牙连接");
    }

    synchronized void setPlaybackStatus(String status) {
        playbackStatus = clean(status, "等待网易云音乐播放");
    }

    synchronized String getDisplayStatus() {
        if (bluetoothStatus.isEmpty()) return playbackStatus;
        if (playbackStatus.isEmpty()) return bluetoothStatus;
        return bluetoothStatus + " · " + playbackStatus;
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
