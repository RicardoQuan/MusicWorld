package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Queries lyric providers in priority order and stops at the first trusted timed match. */
public final class FallbackLyricProvider implements LyricProvider {
    private final List<LyricProvider> providers;

    public FallbackLyricProvider(List<LyricProvider> providers) {
        if (providers == null) throw new IllegalArgumentException("providers are required");
        List<LyricProvider> copy = new ArrayList<>();
        for (LyricProvider provider : providers) {
            if (provider == null) throw new IllegalArgumentException("provider is required");
            copy.add(provider);
        }
        this.providers = Collections.unmodifiableList(copy);
    }

    @Override
    public List<LyricCandidate> search(TrackInfo track) throws IOException {
        IOException lastFailure = null;
        for (LyricProvider provider : providers) {
            try {
                List<LyricCandidate> candidates = provider.search(track);
                if (LyricMatcher.bestMatch(track, candidates) != null) {
                    return candidates;
                }
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure != null) throw lastFailure;
        return Collections.emptyList();
    }
}
