package com.univ.lyricsbridge.lyric;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** Builds the URL encoded in a locally generated NetEase login QR code. */
public final class NetEaseQrLoginUrl {
    private NetEaseQrLoginUrl() {
    }

    public static String build(String key) {
        String cleanKey = key == null ? "" : key.trim();
        if (cleanKey.isEmpty()) throw new IllegalArgumentException("QR key is required");
        try {
            return "https://music.163.com/login?codekey=" + URLEncoder.encode(cleanKey, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable", impossible);
        }
    }
}
