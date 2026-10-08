package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public class NetEaseWeApiCryptoTest {
    @Test
    public void encryptedPayloadRoundTripsTheTwoAesLayers() throws Exception {
        String key = "0123456789abcdef";
        String json = "{\"s\":\"掉了 张惠妹\",\"limit\":20}";

        NetEaseWeApiCrypto.Payload payload = NetEaseWeApiCrypto.encryptWithSecretKey(json, key);

        String innerCiphertext = decrypt(payload.getParams(), key);
        String actualJson = decrypt(innerCiphertext, NetEaseWeApiCrypto.NONCE);
        assertEquals(json, actualJson);
        assertEquals(256, payload.getEncSecKey().length());
        assertTrue(payload.getEncSecKey().matches("[0-9a-f]{256}"));
    }

    private static String decrypt(String base64, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(NetEaseWeApiCrypto.IV.getBytes(StandardCharsets.UTF_8)));
        return new String(cipher.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
    }
}
