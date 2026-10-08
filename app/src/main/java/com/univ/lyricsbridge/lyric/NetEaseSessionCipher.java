package com.univ.lyricsbridge.lyric;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES/GCM helper kept independent of Android Keystore so its format can be unit tested. */
public final class NetEaseSessionCipher {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte[] AAD = "com.univ.lyricsbridge.netease.session.v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private NetEaseSessionCipher() { }

    public static byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Let Android Keystore generate the IV. Its randomized-encryption policy rejects
        // caller-supplied IVs during encryption.
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_LENGTH) {
            throw new IllegalStateException("AES/GCM provider returned an invalid IV");
        }
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] stored = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, stored, 0, iv.length);
        System.arraycopy(encrypted, 0, stored, iv.length, encrypted.length);
        return stored;
    }

    public static byte[] decrypt(byte[] stored, SecretKey key) throws Exception {
        if (stored == null || stored.length <= IV_LENGTH) throw new IllegalArgumentException("Invalid session ciphertext");
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[stored.length - IV_LENGTH];
        System.arraycopy(stored, 0, iv, 0, IV_LENGTH);
        System.arraycopy(stored, IV_LENGTH, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        cipher.updateAAD(AAD);
        return cipher.doFinal(encrypted);
    }
}
