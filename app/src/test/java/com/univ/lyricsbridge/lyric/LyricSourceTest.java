package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import org.junit.Test;

public class LyricSourceTest {
    @Test
    public void sourceLabelsAreLocalizedAndUnknownHasNoLabel() {
        assertEquals("网易云音乐", LyricSource.NETEASE.displayName());
        assertEquals("LRCLIB", LyricSource.LRCLIB.displayName());
        assertEquals("", LyricSource.UNKNOWN.displayName());
    }

    @Test
    public void legacyCandidatesAndUnmatchedResultsRemainUnknown() {
        LyricCandidate legacy = new LyricCandidate("Song", "Artist", "Album", 180_000,
                "[00:01.00]line");

        assertEquals(LyricSource.UNKNOWN, legacy.getSource());
        assertEquals(LyricSource.UNKNOWN, LyricLookupResult.noMatch().getSource());
        assertEquals(LyricSource.UNKNOWN, LyricLookupResult.error("failed").getSource());
    }

    @Test
    public void foundResultCarriesItsCandidateSource() {
        LyricCandidate candidate = new LyricCandidate("Song", "Artist", "Album", 180_000,
                "[00:01.00]line", LyricSource.LRCLIB);

        LyricLookupResult result = LyricLookupResult.found(candidate, candidate.getRawLrc(),
                LrcParser.parse(candidate.getRawLrc()));

        assertEquals(LyricSource.LRCLIB, result.getSource());
    }
}
