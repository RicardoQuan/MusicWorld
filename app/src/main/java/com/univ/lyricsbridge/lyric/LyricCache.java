package com.univ.lyricsbridge.lyric;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.univ.lyricsbridge.model.TrackInfo;

public final class LyricCache extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "lyrics-cache.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "lyric_cache";

    public LyricCache(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "cache_key TEXT PRIMARY KEY NOT NULL,"
                + "title TEXT NOT NULL,"
                + "artist TEXT NOT NULL,"
                + "duration_ms INTEGER NOT NULL,"
                + "raw_lrc TEXT NOT NULL,"
                + "updated_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 has no earlier schema to migrate from.
    }

    public String get(TrackInfo track) {
        if (track == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE, new String[]{"raw_lrc"}, "cache_key = ?",
                new String[]{cacheKey(track)}, null, null, null, "1")) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public void put(TrackInfo track, String rawLrc) {
        if (track == null || rawLrc == null || rawLrc.trim().isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("cache_key", cacheKey(track));
        values.put("title", track.getTitle());
        values.put("artist", track.getArtist());
        values.put("duration_ms", track.getDurationMs());
        values.put("raw_lrc", rawLrc);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void remove(TrackInfo track) {
        if (track == null) return;
        getWritableDatabase().delete(TABLE, "cache_key = ?", new String[]{cacheKey(track)});
    }

    private static String cacheKey(TrackInfo track) {
        return LyricMatcher.normalize(track.getTitle()) + "|"
                + LyricMatcher.normalize(track.getArtist()) + "|" + track.getDurationMs();
    }
}
