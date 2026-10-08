package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.TrackInfo;
import java.util.Collections;
import java.util.List;

/** Returns lyrics explicitly imported by the user for the currently matching track. */
public final class LocalLrcLyricProvider implements LyricProvider {
    private final LocalLyricStore store;

    public LocalLrcLyricProvider(LocalLyricStore store) {
        if (store == null) throw new IllegalArgumentException("store is required");
        this.store = store;
    }

    @Override
    public List<LyricCandidate> search(TrackInfo track) {
        if (track == null || track.getTitle().isEmpty() || track.getArtist().isEmpty()) {
            return Collections.emptyList();
        }
        String lrc = store.get(track);
        if (lrc.isEmpty()) return Collections.emptyList();
        return Collections.singletonList(new LyricCandidate(track.getTitle(), track.getArtist(),
                track.getAlbum(), track.getDurationMs(), lrc, LyricSource.LOCAL_LRC));
    }
}
