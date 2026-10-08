package com.univ.lyricsbridge.lyric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Implements the legacy WeAPI request envelope used by NetEase Music endpoints. */
public final class NetEaseWeApiCrypto {
    static final String NONCE = "0CoJUm6Qyw8W8jud";
    static final String IV = "0102030405060708";
    private static final String RSA_EXPONENT = "010001";
    private static final String RSA_MODULUS =
            "e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
    private static final char[] KEY_ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private NetEaseWeApiCrypto() { }

    public static String newSecretKey() {
        char[] key = new char[16];
        synchronized (RANDOM) {
            for (int i = 0; i < key.length; i++) key[i] = KEY_ALPHABET[RANDOM.nextInt(KEY_ALPHABET.length)];
        }
        return new String(key);
    }

    static Payload encryptWithSecretKey(String json, String secretKey) throws Exception {
        if (json == null || secretKey == null || secretKey.length() != 16) {
            throw new IllegalArgumentException("WeAPI requires JSON and a 16-character key");
        }
        String first = encryptAes(json, NONCE);
        String params = encryptAes(first, secretKey);
        String reversed = new StringBuilder(secretKey).reverse().toString();
        BigInteger message = new BigInteger(1, reversed.getBytes(StandardCharsets.UTF_8));
        BigInteger encrypted = message.modPow(new BigInteger(RSA_EXPONENT, 16), new BigInteger(RSA_MODULUS, 16));
        String encSecKey = encrypted.toString(16).toLowerCase(Locale.ROOT);
        StringBuilder padded = new StringBuilder(encSecKey);
        while (padded.length() < 256) padded.insert(0, '0');
        if (padded.length() > 256) padded.delete(0, padded.length() - 256);
        return new Payload(params, padded.toString());
    }

    public static Payload encrypt(String json) throws Exception {
        return encryptWithSecretKey(json, newSecretKey());
    }

    private static String encryptAes(String input, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
        byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static final class Payload {
        private final String params;
        private final String encSecKey;

        Payload(String params, String encSecKey) {
            this.params = params;
            this.encSecKey = encSecKey;
        }

        public String getParams() { return params; }
        public String getEncSecKey() { return encSecKey; }
    }
}
