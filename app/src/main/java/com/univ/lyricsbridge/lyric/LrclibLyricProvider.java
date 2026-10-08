package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Finds synced LRCLIB lyrics after higher-priority sources have no usable match. */
public final class LrclibLyricProvider implements LyricProvider {
    private final LrclibApiClient apiClient;

    public LrclibLyricProvider(LrclibApiClient apiClient) {
        if (apiClient == null) throw new IllegalArgumentException("apiClient is required");
        this.apiClient = apiClient;
    }

    @Override
    public List<LyricCandidate> search(TrackInfo track) throws IOException {
        if (track == null || track.getTitle().isEmpty() || track.getArtist().isEmpty()) {
            return Collections.emptyList();
        }
        IOException lastFailure = null;
        boolean anyRequestResponded = false;

        try {
            LrclibApiClient.Response exact = apiClient.get(exactPath(track));
            anyRequestResponded = true;
            LyricCandidate candidate = parseCandidate(new JSONObject(exact.body), track);
            if (candidate != null) return Collections.singletonList(candidate);
        } catch (IOException exception) {
            lastFailure = exception;
        } catch (JSONException ignored) {
            anyRequestResponded = true;
        }

        List<String> searchPaths = new ArrayList<>();
        searchPaths.add(searchPath(track.getTitle(), track.getArtist()));
        if (!track.getArtist().isEmpty()) searchPaths.add(searchPath(track.getTitle(), ""));
        for (String path : searchPaths) {
            try {
                LrclibApiClient.Response response = apiClient.get(path);
                anyRequestResponded = true;
                List<LyricCandidate> candidates = parseCandidates(new JSONArray(response.body), track);
                if (!candidates.isEmpty()) return candidates;
            } catch (IOException exception) {
                lastFailure = exception;
            } catch (JSONException ignored) {
                anyRequestResponded = true;
            }
        }
        if (!anyRequestResponded && lastFailure != null) throw lastFailure;
        return Collections.emptyList();
    }

    private static String exactPath(TrackInfo track) throws IOException {
        StringBuilder path = new StringBuilder("/api/get?track_name=")
                .append(encode(track.getTitle()));
        if (!track.getArtist().isEmpty()) path.append("&artist_name=").append(encode(track.getArtist()));
        if (!track.getAlbum().isEmpty()) path.append("&album_name=").append(encode(track.getAlbum()));
        long durationSeconds = Math.round(track.getDurationMs() / 1000.0);
        if (durationSeconds >= 1 && durationSeconds <= 3600) {
            path.append("&duration=").append(durationSeconds);
        }
        return path.toString();
    }

    private static String searchPath(String title, String artist) throws IOException {
        StringBuilder path = new StringBuilder("/api/search?track_name=").append(encode(title));
        if (artist != null && !artist.isEmpty()) path.append("&artist_name=").append(encode(artist));
        return path.toString();
    }

    private static String encode(String value) throws IOException {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exception) {
            throw new IOException("Could not encode LRCLIB query", exception);
        }
    }

    private static List<LyricCandidate> parseCandidates(JSONArray items, TrackInfo track) {
        List<ScoredCandidate> scored = new ArrayList<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) continue;
            LyricCandidate candidate = parseCandidate(item, track);
            if (candidate == null) continue;
            double score = LyricMatcher.metadataScore(track, candidate.getTitle(), candidate.getArtist(),
                    candidate.getDurationMs());
            scored.add(new ScoredCandidate(candidate, score));
        }
        scored.sort(Comparator.comparingDouble((ScoredCandidate item) -> item.score).reversed());
        List<LyricCandidate> candidates = new ArrayList<>();
        for (ScoredCandidate item : scored) candidates.add(item.candidate);
        return candidates;
    }

    private static LyricCandidate parseCandidate(JSONObject item, TrackInfo track) {
        String title = item.optString("trackName", "").trim();
        String artist = item.optString("artistName", "").trim();
        String album = item.optString("albumName", "").trim();
        String lrc = item.optString("syncedLyrics", "");
        if (title.isEmpty() || artist.isEmpty() || LrcParser.parse(lrc).isEmpty()) return null;
        long durationMs = Math.round(item.optDouble("duration", 0.0) * 1000.0);
        LyricCandidate candidate = new LyricCandidate(title, artist, album, durationMs, lrc,
                LyricSource.LRCLIB);
        return LyricMatcher.metadataScore(track, title, artist, durationMs)
                >= LyricMatcher.ACCEPTANCE_THRESHOLD ? candidate : null;
    }

    private static final class ScoredCandidate {
        final LyricCandidate candidate;
        final double score;

        ScoredCandidate(LyricCandidate candidate, double score) {
            this.candidate = candidate;
            this.score = score;
        }
    }
}
