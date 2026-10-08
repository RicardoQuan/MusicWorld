package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import org.junit.Test;

public class FallbackLyricProviderTest {
    private static final TrackInfo TRACK = new TrackInfo("掉了", "张惠妹", "", "", 240_000);

    @Test
    public void validNetEaseCandidateWinsWithoutCallingFallback() throws Exception {
        List<String> calls = new ArrayList<>();
        LyricCandidate netEase = new LyricCandidate("掉了", "张惠妹", "专辑", 250_000,
                "[00:01.00]网易云歌词", LyricSource.NETEASE);
        LyricProvider first = provider("网易云", calls, Collections.singletonList(netEase), false);
        LyricProvider second = provider("LRCLIB", calls,
                Collections.singletonList(candidate(LyricSource.LRCLIB, "[00:02.00]备用歌词")), false);

        List<LyricCandidate> result = new FallbackLyricProvider(Arrays.asList(first, second)).search(TRACK);

        assertEquals(Collections.singletonList("网易云"), calls);
        assertEquals(LyricSource.NETEASE, LyricMatcher.bestMatch(TRACK, result).getCandidate().getSource());
    }

    @Test
    public void emptyOrFailedNetEaseProviderFallsBackToLrclib() throws Exception {
        List<String> calls = new ArrayList<>();
        LyricProvider first = provider("网易云", calls, Collections.emptyList(), false);
        LyricCandidate backup = candidate(LyricSource.LRCLIB, "[00:02.00]备用歌词");
        LyricProvider second = provider("LRCLIB", calls, Collections.singletonList(backup), false);

        List<LyricCandidate> result = new FallbackLyricProvider(Arrays.asList(first, second)).search(TRACK);

        assertEquals(Arrays.asList("网易云", "LRCLIB"), calls);
        assertEquals(backup, LyricMatcher.bestMatch(TRACK, result).getCandidate());

        calls.clear();
        first = provider("网易云", calls, Collections.emptyList(), true);
        result = new FallbackLyricProvider(Arrays.asList(first, second)).search(TRACK);
        assertEquals(Arrays.asList("网易云", "LRCLIB"), calls);
        assertEquals(LyricSource.LRCLIB, LyricMatcher.bestMatch(TRACK, result).getCandidate().getSource());
    }

    @Test
    public void candidatesWithoutTimestampOrMetadataMatchDoNotBlockFallback() throws Exception {
        List<String> calls = new ArrayList<>();
        LyricCandidate untimed = candidate(LyricSource.NETEASE, "plain text only");
        LyricCandidate wrongTrack = new LyricCandidate("Other", "Someone else", "", 0,
                "[00:01.00]wrong lyrics", LyricSource.NETEASE);
        LyricProvider first = provider("网易云", calls, Arrays.asList(untimed, wrongTrack), false);
        LyricCandidate backup = candidate(LyricSource.LRCLIB, "[00:02.00]备用歌词");
        LyricProvider second = provider("LRCLIB", calls, Collections.singletonList(backup), false);

        List<LyricCandidate> result = new FallbackLyricProvider(Arrays.asList(first, second)).search(TRACK);

        assertEquals(Arrays.asList("网易云", "LRCLIB"), calls);
        assertEquals(LyricSource.LRCLIB, LyricMatcher.bestMatch(TRACK, result).getCandidate().getSource());
    }

    @Test
    public void noProviderWithValidTimedMatchReturnsEmptyCandidates() throws Exception {
        List<String> calls = new ArrayList<>();
        LyricProvider first = provider("网易云", calls, Collections.emptyList(), false);
        LyricProvider second = provider("LRCLIB", calls, Collections.singletonList(
                new LyricCandidate("Other", "Artist", "", 0, "[00:01.00]line",
                        LyricSource.LRCLIB)), false);

        List<LyricCandidate> result = new FallbackLyricProvider(Arrays.asList(first, second)).search(TRACK);

        assertTrue(result.isEmpty());
        assertEquals(Arrays.asList("网易云", "LRCLIB"), calls);
    }

    @Test
    public void failedLookupIsReportedWhenAllRemainingProvidersHaveNoMatch() throws Exception {
        List<String> calls = new ArrayList<>();
        LyricProvider first = provider("网易云", calls, Collections.emptyList(), true);
        LyricProvider second = provider("LRCLIB", calls, Collections.emptyList(), false);

        try {
            new FallbackLyricProvider(Arrays.asList(first, second)).search(TRACK);
            throw new AssertionError("Expected the unavailable provider to be reported");
        } catch (IOException expected) {
            assertEquals("temporary network failure", expected.getMessage());
        }
        assertEquals(Arrays.asList("网易云", "LRCLIB"), calls);
    }

    private static LyricCandidate candidate(LyricSource source, String lrc) {
        return new LyricCandidate("掉了", "张惠妹", "专辑", 240_000, lrc, source);
    }

    private static LyricProvider provider(String name, List<String> calls,
                                          List<LyricCandidate> result, boolean fail) {
        return track -> {
            calls.add(name);
            if (fail) throw new IOException("temporary network failure");
            return result;
        };
    }
}
