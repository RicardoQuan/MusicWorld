package com.univ.lyricsbridge.lyric;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Stores only an AES/GCM-encrypted NetEase session cookie in app-private preferences. */
public final class NetEaseSessionStore {
    private static final String PREFS = "netease_session";
    private static final String COOKIE = "encrypted_cookie";
    private static final String KEY_ALIAS = "univ_lyrics_netease_session_v1";
    private final SharedPreferences preferences;

    public NetEaseSessionStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void saveCookie(String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) throw new IllegalArgumentException("Session cookie is empty");
        try {
            byte[] ciphertext = NetEaseSessionCipher.encrypt(cookie.getBytes(StandardCharsets.UTF_8), getOrCreateKey());
            preferences.edit().putString(COOKIE, Base64.encodeToString(ciphertext, Base64.NO_WRAP)).apply();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not protect the NetEase session", exception);
        }
    }

    public synchronized String getCookie() {
        String encoded = preferences.getString(COOKIE, "");
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            byte[] ciphertext = Base64.decode(encoded, Base64.NO_WRAP);
            byte[] plaintext = NetEaseSessionCipher.decrypt(ciphertext, getOrCreateKey());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            clear();
            return "";
        }
    }

    public synchronized boolean isLoggedIn() {
        return !getCookie().isEmpty();
    }

    public synchronized void clear() {
        preferences.edit().remove(COOKIE).apply();
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // Removing the encrypted value is sufficient if the platform keystore is unavailable.
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }
}
