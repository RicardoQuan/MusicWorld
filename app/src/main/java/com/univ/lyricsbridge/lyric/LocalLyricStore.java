package com.univ.lyricsbridge.lyric;

import android.content.Context;
import android.content.SharedPreferences;
import com.univ.lyricsbridge.model.TrackInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Persists user-imported timestamped lyrics inside this app, keyed by track title and artist. */
public final class LocalLyricStore {
    public static final int MAX_LRC_CHARS = 256 * 1024;
    private static final String PREFERENCES_NAME = "local_lyrics";
    private static final String KEY_PREFIX = "track_";
    private final SharedPreferences preferences;

    public LocalLyricStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void save(TrackInfo track, String rawLrc) {
        String key = keyFor(track);
        String lyrics = rawLrc == null ? "" : rawLrc;
        if (lyrics.length() > MAX_LRC_CHARS) {
            throw new IllegalArgumentException("LRC 文件太大，请选择小于 256 KB 的歌词文件。");
        }
        if (LrcParser.parse(lyrics).isEmpty()) {
            throw new IllegalArgumentException("文件中没有可识别的时间戳歌词。");
        }
        preferences.edit().putString(key, lyrics).apply();
    }

    public String get(TrackInfo track) {
        String key = keyFor(track);
        String lyrics = preferences.getString(key, "");
        if (lyrics != null && !LrcParser.parse(lyrics).isEmpty()) return lyrics;
        if (lyrics != null && !lyrics.isEmpty()) preferences.edit().remove(key).apply();
        return "";
    }

    private static String keyFor(TrackInfo track) {
        if (track == null) throw new IllegalArgumentException("track is required");
        String title = LyricMatcher.normalize(track.getTitle());
        String artist = LyricMatcher.normalize(track.getArtist());
        if (title.isEmpty() || artist.isEmpty()) {
            throw new IllegalArgumentException("歌曲名和歌手信息不完整，无法绑定本地歌词。");
        }
        String identity = title + "\n" + artist;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(KEY_PREFIX);
            for (byte value : digest) key.append(String.format("%02x", value & 0xff));
            return key.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
