package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public class NetEaseSessionCipherTest {
    @Test
    public void sessionCipherRoundTripsWithoutStoringPlaintext() throws Exception {
        byte[] keyBytes = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        byte[] cookie = "MUSIC_U=private-session-cookie".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = NetEaseSessionCipher.encrypt(cookie, key);

        assertFalse(new String(encrypted, StandardCharsets.UTF_8).contains("private-session-cookie"));
        assertArrayEquals(cookie, NetEaseSessionCipher.decrypt(encrypted, key));
    }
}
