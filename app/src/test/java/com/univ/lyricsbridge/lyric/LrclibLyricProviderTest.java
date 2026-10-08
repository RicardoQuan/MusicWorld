package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LrclibLyricProviderTest {
    private static final TrackInfo TRACK = new TrackInfo("掉了 & Live", "张惠妹", "阿妹", "", 240_000);

    @Test
    public void exactLookupEncodesMetadataAndReturnsTimedLrclibCandidate() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(response(200, lyricObject("掉了 & Live", "张惠妹", "阿妹",
                240, "[00:01.00]时间歌词")));
        LrclibLyricProvider provider = new LrclibLyricProvider(new LrclibApiClient(transport));

        List<LyricCandidate> candidates = provider.search(TRACK);

        assertEquals(1, candidates.size());
        assertEquals(LyricSource.LRCLIB, candidates.get(0).getSource());
        assertEquals("[00:01.00]时间歌词", candidates.get(0).getRawLrc());
        assertTrue(transport.paths.get(0).startsWith("/api/get?"));
        String query = transport.paths.get(0).substring(transport.paths.get(0).indexOf('?') + 1);
        assertTrue(query.contains("track_name=%E6%8E%89%E4%BA%86+%26+Live"));
        assertTrue(query.contains("artist_name=%E5%BC%A0%E6%83%A0%E5%A6%B9"));
        assertTrue(query.contains("album_name=%E9%98%BF%E5%A6%B9"));
        assertTrue(query.contains("duration=240"));
    }

    @Test
    public void exactMissSearchesTitleAndArtistBeforeTitleOnly() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(response(404, "not found"));
        transport.responses.add(response(200, "[]"));
        transport.responses.add(response(200, "[" + lyricObject("掉了 & Live", "张惠妹", "阿妹",
                240, "[00:02.00]备用时间歌词") + "]"));
        LrclibLyricProvider provider = new LrclibLyricProvider(new LrclibApiClient(transport));

        List<LyricCandidate> candidates = provider.search(TRACK);

        assertEquals(3, transport.paths.size());
        assertTrue(transport.paths.get(0).startsWith("/api/get?"));
        assertTrue(transport.paths.get(1).startsWith("/api/search?"));
        assertTrue(transport.paths.get(1).contains("artist_name="));
        assertTrue(transport.paths.get(2).startsWith("/api/search?"));
        assertFalse(transport.paths.get(2).contains("artist_name="));
        assertEquals("掉了 & Live", queryValue(transport.paths.get(1), "track_name"));
        assertEquals("张惠妹", queryValue(transport.paths.get(1), "artist_name"));
        assertEquals("掉了 & Live", queryValue(transport.paths.get(2), "track_name"));
        assertEquals(LyricSource.LRCLIB, candidates.get(0).getSource());
    }

    @Test
    public void untimedOrPoorMetadataCandidatesAreRejected() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(response(200, lyricObject("掉了 & Live", "张惠妹", "阿妹",
                240, "instrumental plain text")));
        transport.responses.add(response(200, "[]"));
        transport.responses.add(response(200, "[]"));
        LrclibLyricProvider provider = new LrclibLyricProvider(new LrclibApiClient(transport));

        assertTrue(provider.search(TRACK).isEmpty());

        RecordingTransport wrongTrack = new RecordingTransport();
        wrongTrack.responses.add(response(404, "not found"));
        wrongTrack.responses.add(response(200, "[" + lyricObject("Another song", "Someone else", "",
                0, "[00:01.00]wrong song") + "]"));
        wrongTrack.responses.add(response(200, "[]"));
        assertTrue(new LrclibLyricProvider(new LrclibApiClient(wrongTrack)).search(TRACK).isEmpty());
    }

    @Test
    public void networkFailureIsRetriedAcrossSearchVariantsAndReportedIfAllFail() throws Exception {
        final int[] requestCount = {0};
        LrclibApiClient client = new LrclibApiClient(path -> {
            requestCount[0]++;
            if (requestCount[0] == 1) throw new IOException("offline");
            if (requestCount[0] == 2) return response(200, "[]");
            return response(200, "[" + lyricObject("掉了 & Live", "张惠妹", "阿妹",
                    240, "[00:03.00]找回时间歌词") + "]");
        });

        List<LyricCandidate> result = new LrclibLyricProvider(client).search(TRACK);

        assertEquals(3, requestCount[0]);
        assertEquals("[00:03.00]找回时间歌词", result.get(0).getRawLrc());

        final int[] failures = {0};
        LrclibApiClient offline = new LrclibApiClient(path -> {
            failures[0]++;
            throw new IOException("offline");
        });
        try {
            new LrclibLyricProvider(offline).search(TRACK);
            throw new AssertionError("Expected IOException");
        } catch (IOException expected) {
            assertEquals("offline", expected.getMessage());
            assertEquals(3, failures[0]);
        }
    }

    @Test
    public void invalidJsonAndHttpErrorsBecomeIoExceptions() throws Exception {
        LrclibApiClient malformed = new LrclibApiClient(path -> response(200, "not json"));
        try {
            malformed.get("/api/search?q=x");
            throw new AssertionError("Expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid"));
        }

        LrclibApiClient httpError = new LrclibApiClient(path -> response(503, "temporarily unavailable"));
        try {
            httpError.get("/api/search?q=x");
            throw new AssertionError("Expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("503"));
        }
    }

    @Test
    public void missingArtistDoesNotSendAnInvalidExactLookup() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        TrackInfo trackWithoutArtist = new TrackInfo("掉了", "", "", "", 0);

        assertTrue(new LrclibLyricProvider(new LrclibApiClient(transport))
                .search(trackWithoutArtist).isEmpty());
        assertTrue(transport.paths.isEmpty());
    }

    private static String lyricObject(String title, String artist, String album,
                                      int durationSeconds, String lyrics) {
        return "{\"trackName\":\"" + jsonEscape(title) + "\","
                + "\"artistName\":\"" + jsonEscape(artist) + "\","
                + "\"albumName\":\"" + jsonEscape(album) + "\","
                + "\"duration\":" + durationSeconds + ","
                + "\"syncedLyrics\":\"" + jsonEscape(lyrics) + "\"}";
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static LrclibApiClient.Response response(int status, String body) {
        return new LrclibApiClient.Response(status, body);
    }

    private static String queryValue(String path, String name) throws Exception {
        String query = path.substring(path.indexOf('?') + 1);
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2 && name.equals(URLDecoder.decode(pair[0], "UTF-8"))) {
                return URLDecoder.decode(pair[1], "UTF-8");
            }
        }
        return null;
    }

    private static final class RecordingTransport implements LrclibApiClient.Transport {
        final List<String> paths = new ArrayList<>();
        final List<LrclibApiClient.Response> responses = new ArrayList<>();
        int next;

        @Override
        public LrclibApiClient.Response get(String path) throws IOException {
            paths.add(path);
            return responses.get(next++);
        }
    }
}
