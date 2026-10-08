package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.univ.lyricsbridge.model.TrackInfo;
import java.util.Arrays;
import org.junit.Test;

public final class LyricMatcherTest {
    private static final String TIMED_LRC = "[00:00.00]A timed line";

    @Test
    public void acceptsExactTitleArtistAndDuration() {
        TrackInfo track = new TrackInfo("Night Drive", "Mira", "Album", "com.netease.cloudmusic", 200000);
        LyricCandidate candidate = candidate("NightDrive", "Mira", 200000);

        LyricMatcher.Match match = LyricMatcher.bestMatch(track, Arrays.asList(candidate));

        assertSame(candidate, match.getCandidate());
        assertEquals(1.0, match.getScore(), 0.0001);
    }

    @Test
    public void rejectsWrongArtistAndWrongTitle() {
        TrackInfo track = new TrackInfo("Night Drive", "Mira", "Album", "com.netease.cloudmusic", 200000);

        assertNull(LyricMatcher.bestMatch(track,
                Arrays.asList(candidate("Night Drive", "Someone Else", 200000))));
        assertNull(LyricMatcher.bestMatch(track,
                Arrays.asList(candidate("Different Song", "Mira", 200000))));
    }

    @Test
    public void rejectsCandidatesBelowTheConfidenceThreshold() {
        TrackInfo track = new TrackInfo("Night Drive", "Mira", "Album", "com.netease.cloudmusic", 200000);
        LyricCandidate candidate = candidate("Night Drives", "Mira Band", 198000);

        assertNull(LyricMatcher.bestMatch(track, Arrays.asList(candidate)));
    }

    @Test
    public void acceptsDiaoLeByAmeiChineseArtistAliasAndDuration() {
        TrackInfo track = new TrackInfo("掉了", "张惠妹", "", "com.netease.cloudmusic", 238000);
        LyricCandidate candidate = candidate("掉了", "aMEI (張惠妹)", 238000);

        LyricMatcher.Match match = LyricMatcher.bestMatch(track, Arrays.asList(candidate));

        assertSame(candidate, match.getCandidate());
        assertTrue(match.getScore() >= LyricMatcher.ACCEPTANCE_THRESHOLD);
    }

    private LyricCandidate candidate(String title, String artist, long durationMs) {
        return new LyricCandidate(title, artist, "Album", durationMs, TIMED_LRC);
    }
}
