package com.univ.lyricsbridge.ui;

import com.univ.lyricsbridge.lyric.NetEaseApiClient;

/** User-facing text for NetEase QR authorization states. */
public final class NetEaseQrStatusText {
    private NetEaseQrStatusText() {
    }

    public static String describe(NetEaseApiClient.QrLoginStatus status, String detail) {
        if (status == null) return "二维码登录状态未知，请刷新二维码。";
        switch (status) {
            case WAITING_SCAN:
                return "等待扫码";
            case WAITING_CONFIRMATION:
                return "已扫码，请在网易云音乐 App 确认登录";
            case EXPIRED:
                return "二维码已过期，请刷新二维码";
            case AUTHORIZED:
                return "网易云账号登录成功";
            case CHALLENGE:
                return detail == null || detail.trim().isEmpty()
                        ? "请先在官方网易云音乐 App 完成安全验证，再刷新二维码。"
                        : detail;
            case ERROR:
                return detail == null || detail.trim().isEmpty()
                        ? "二维码登录暂时失败，请检查网络后刷新二维码。"
                        : detail;
            default:
                return "二维码登录状态未知，请刷新二维码。";
        }
    }
}
