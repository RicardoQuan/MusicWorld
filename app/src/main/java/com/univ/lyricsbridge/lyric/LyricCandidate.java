package com.univ.lyricsbridge.lyric;

public final class LyricCandidate {
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;
    private final String rawLrc;
    private final LyricSource source;

    public LyricCandidate(String title, String artist, String album, long durationMs, String rawLrc) {
        this(title, artist, album, durationMs, rawLrc, LyricSource.UNKNOWN);
    }

    public LyricCandidate(String title, String artist, String album, long durationMs, String rawLrc,
                          LyricSource source) {
        this.title = clean(title);
        this.artist = clean(artist);
        this.album = clean(album);
        this.durationMs = Math.max(0, durationMs);
        this.rawLrc = rawLrc == null ? "" : rawLrc;
        this.source = source == null ? LyricSource.UNKNOWN : source;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getRawLrc() {
        return rawLrc;
    }

    public LyricSource getSource() {
        return source;
    }
}
