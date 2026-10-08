package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class LyricLookupEngine {
    private final LyricProvider provider;
    private final LyricCache cache;
    private final BooleanSupplier cacheReadsEnabled;

    public LyricLookupEngine(LyricProvider provider, LyricCache cache) {
        this(provider, cache, () -> true);
    }

    public LyricLookupEngine(LyricProvider provider, LyricCache cache,
                             BooleanSupplier cacheReadsEnabled) {
        if (provider == null || cache == null) throw new IllegalArgumentException("provider and cache are required");
        this.provider = provider;
        this.cache = cache;
        this.cacheReadsEnabled = cacheReadsEnabled == null ? () -> true : cacheReadsEnabled;
    }

    /** Call from a worker thread; provider requests block while waiting for the network. */
    public LyricLookupResult lookup(TrackInfo track) {
        if (track == null || track.getTitle().isEmpty() || track.getArtist().isEmpty()) {
            return LyricLookupResult.noMatch();
        }
        try {
            String cachedLrc = cacheReadsEnabled.getAsBoolean() ? cache.get(track) : null;
            if (cachedLrc != null) {
                List<com.univ.lyricsbridge.model.LyricLine> cachedLines = LrcParser.parse(cachedLrc);
                if (!cachedLines.isEmpty()) {
                    LyricCandidate cached = new LyricCandidate(track.getTitle(), track.getArtist(),
                            track.getAlbum(), track.getDurationMs(), cachedLrc);
                    return LyricLookupResult.found(cached, cachedLrc, cachedLines);
                }
                cache.remove(track);
            }

            List<LyricCandidate> candidates = provider.search(track);
            LyricMatcher.Match match = LyricMatcher.bestMatch(track, candidates);
            if (match == null) return LyricLookupResult.noMatch();
            LyricCandidate candidate = match.getCandidate();
            List<com.univ.lyricsbridge.model.LyricLine> lines = LrcParser.parse(candidate.getRawLrc());
            if (lines.isEmpty()) return LyricLookupResult.noMatch();
            cache.put(track, candidate.getRawLrc());
            return LyricLookupResult.found(candidate, candidate.getRawLrc(), lines);
        } catch (IOException | RuntimeException exception) {
            return LyricLookupResult.error("歌词查询失败，请检查网络后重试");
        }
    }
}
