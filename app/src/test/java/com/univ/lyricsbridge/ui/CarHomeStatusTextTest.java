package com.univ.lyricsbridge.ui;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import org.junit.Test;

public final class CarHomeStatusTextTest {
    @Test
    public void showsTheLiveConnectionStatusOnTheCarRoleHome() {
        assertEquals("车机状态：已连接 Pixel",
                formatHomeStatus("已连接 Pixel"));
    }

    @Test
    public void explainsHowToStartWhenTheReceiverIsNotConnected() {
        assertEquals("车机状态：未连接（请进入车机显示配置连接已配对手机）",
                formatHomeStatus("未连接"));
    }

    private String formatHomeStatus(String status) {
        try {
            Class<?> type = Class.forName("com.univ.lyricsbridge.ui.CarHomeStatusText");
            Method format = type.getMethod("format", String.class);
            return (String) format.invoke(null, status);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
