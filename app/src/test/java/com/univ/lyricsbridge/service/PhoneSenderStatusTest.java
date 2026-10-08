package com.univ.lyricsbridge.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PhoneSenderStatusTest {
    @Test
    public void reportsConnectedCarAndCurrentLyricStateTogether() {
        PhoneSenderStatus status = new PhoneSenderStatus();
        status.setBluetoothStatus("已连接车机：UNI-V");
        status.setPlaybackStatus("已匹配到 12 行时间戳歌词");

        assertEquals("已连接车机：UNI-V · 已匹配到 12 行时间戳歌词",
                status.getDisplayStatus());
    }

    @Test
    public void reportsBluetoothWaitingAfterDisconnectAndKeepsPlaybackState() {
        PhoneSenderStatus status = new PhoneSenderStatus();
        status.setBluetoothStatus("已连接车机：UNI-V");
        status.setPlaybackStatus("未匹配到带时间戳歌词");
        status.setBluetoothStatus("等待车机蓝牙连接");

        assertEquals("等待车机蓝牙连接 · 未匹配到带时间戳歌词",
                status.getDisplayStatus());
    }
}
