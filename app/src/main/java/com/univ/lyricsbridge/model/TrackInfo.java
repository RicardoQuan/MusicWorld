package com.univ.lyricsbridge.model;

import java.util.Objects;

public final class TrackInfo {
    private final String title;
    private final String artist;
    private final String album;
    private final String packageName;
    private final long durationMs;

    public TrackInfo(String title, String artist, String album, String packageName, long durationMs) {
        this.title = clean(title);
        this.artist = clean(artist);
        this.album = clean(album);
        this.packageName = clean(packageName);
        this.durationMs = Math.max(0, durationMs);
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

    public String getPackageName() {
        return packageName;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TrackInfo)) return false;
        TrackInfo that = (TrackInfo) other;
        return durationMs == that.durationMs
                && title.equals(that.title)
                && artist.equals(that.artist)
                && album.equals(that.album)
                && packageName.equals(that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, artist, album, packageName, durationMs);
    }
}
