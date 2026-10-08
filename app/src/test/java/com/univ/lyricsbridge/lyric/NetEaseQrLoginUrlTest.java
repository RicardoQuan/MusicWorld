package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class NetEaseQrLoginUrlTest {
    @Test
    public void buildsTheNetEaseWebLoginUrlWithEncodedQrKey() {
        assertEquals("https://music.163.com/login?codekey=qr-key",
                NetEaseQrLoginUrl.build("qr-key"));
        assertEquals("https://music.163.com/login?codekey=a%2Bb%2F%3F+%26",
                NetEaseQrLoginUrl.build("a+b/? &"));
    }

    @Test
    public void rejectsAnEmptyQrKey() {
        assertThrows(IllegalArgumentException.class, () -> NetEaseQrLoginUrl.build(" "));
    }
}
