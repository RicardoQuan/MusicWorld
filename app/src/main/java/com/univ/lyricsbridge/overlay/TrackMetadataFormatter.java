package com.univ.lyricsbridge.overlay;

public final class TrackMetadataFormatter {
    private TrackMetadataFormatter() {
    }

    public static String format(String title, String artist) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanArtist = artist == null ? "" : artist.trim();
        if (cleanTitle.isEmpty()) return cleanArtist;
        if (cleanArtist.isEmpty()) return cleanTitle;
        return cleanTitle + " · " + cleanArtist;
    }
}
