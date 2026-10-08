package com.univ.lyricsbridge.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.univ.lyricsbridge.lyric.NetEaseApiClient;
import org.junit.Test;

public class NetEaseQrStatusTextTest {
    @Test
    public void waitingExpiredAndAuthorizedStatusesHaveClearText() {
        assertEquals("等待扫码", NetEaseQrStatusText.describe(
                NetEaseApiClient.QrLoginStatus.WAITING_SCAN, ""));
        assertEquals("已扫码，请在网易云音乐 App 确认登录", NetEaseQrStatusText.describe(
                NetEaseApiClient.QrLoginStatus.WAITING_CONFIRMATION, ""));
        assertEquals("二维码已过期，请刷新二维码", NetEaseQrStatusText.describe(
                NetEaseApiClient.QrLoginStatus.EXPIRED, ""));
        assertEquals("网易云账号登录成功", NetEaseQrStatusText.describe(
                NetEaseApiClient.QrLoginStatus.AUTHORIZED, ""));
    }

    @Test
    public void securityChallengeExplainsOfficialAppVerification() {
        String message = NetEaseQrStatusText.describe(NetEaseApiClient.QrLoginStatus.CHALLENGE, "");
        assertTrue(message.contains("官方网易云音乐 App"));
        assertTrue(message.contains("完成安全验证"));
    }

    @Test
    public void providerDetailsArePreservedForChallengesAndErrors() {
        String challenge = "网易云要求安全验证（接口码 8821）。请先在官方网易云音乐 App 完成验证。";
        assertEquals(challenge, NetEaseQrStatusText.describe(
                NetEaseApiClient.QrLoginStatus.CHALLENGE, challenge));
        assertEquals("连接失败", NetEaseQrStatusText.describe(
                NetEaseApiClient.QrLoginStatus.ERROR, "连接失败"));
    }
}
