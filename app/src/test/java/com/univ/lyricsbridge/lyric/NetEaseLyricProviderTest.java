package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class NetEaseLyricProviderTest {
    private static final TrackInfo TRACK = new TrackInfo(
            "掉了", "张惠妹", "", "com.netease.cloudmusic", 238000);

    @Test
    public void loggedInLookupReturnsOnlyTimestampedNetEaseLyrics() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) -> {
            assertEquals("MUSIC_U=fake-session", cookie);
            if (path.equals("/weapi/cloudsearch/get/web")) {
                return response("{\"code\":200,\"result\":{\"songs\":[{\"id\":77,\"name\":\"掉了\",\"ar\":[{\"name\":\"張惠妹\"}],\"al\":{\"name\":\"Bad Boy\"},\"dt\":238000}]}} ");
            }
            assertEquals("/weapi/song/lyric", path);
            return response("{\"code\":200,\"lrc\":{\"lyric\":\"[00:00.00]普通歌词\"},"
                    + "\"yrc\":{\"lyric\":\"[0,800](0,400,0)第(400,400,0)一句\"}}");
        });
        LyricProvider netEase = new NetEaseLyricProvider(client, () -> "MUSIC_U=fake-session");

        List<LyricCandidate> candidates = netEase.search(TRACK);

        assertEquals(1, candidates.size());
        assertEquals(LyricSource.NETEASE, candidates.get(0).getSource());
        assertEquals("Bad Boy", candidates.get(0).getAlbum());
        List<com.univ.lyricsbridge.model.LyricLine> lines = LrcParser.parse(candidates.get(0).getRawLrc());
        assertEquals(1, lines.size());
        assertEquals("第一句", lines.get(0).getText());
        assertEquals(2, lines.get(0).getWords().size());
    }

    @Test
    public void missingOrUntimedNetEaseLyricsProduceNoMatch() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) -> {
            if (path.equals("/weapi/cloudsearch/get/web")) {
                return response("{\"code\":200,\"result\":{\"songs\":[{\"id\":77,\"name\":\"掉了\",\"ar\":[{\"name\":\"张惠妹\"}],\"dt\":238000}]}} ");
            }
            return response("{\"code\":200,\"lrc\":{\"lyric\":\"无时间戳\"}}");
        });
        LyricProvider primary = new NetEaseLyricProvider(client, () -> "MUSIC_U=fake-session");

        List<LyricCandidate> results = primary.search(TRACK);

        assertTrue(results.isEmpty());
    }

    @Test
    public void missingSessionReturnsNoNetEaseCandidatesForFallbackProvider() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) -> {
            throw new AssertionError("Network lookup must not run without a NetEase session");
        });
        NetEaseLyricProvider provider = new NetEaseLyricProvider(client, () -> "");

        assertTrue(provider.search(TRACK).isEmpty());
    }

    private static NetEaseApiClient.Response response(String body) {
        return new NetEaseApiClient.Response(200, body, Collections.emptyList());
    }
}
