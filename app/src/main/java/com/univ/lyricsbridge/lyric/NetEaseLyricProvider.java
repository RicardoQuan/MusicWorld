package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Looks up timestamped lyrics from the signed-in user's NetEase session. */
public final class NetEaseLyricProvider implements LyricProvider {
    private static final int MAX_LYRIC_FETCHES = 6;
    private final NetEaseApiClient apiClient;
    private final SessionProvider sessionProvider;

    public NetEaseLyricProvider(NetEaseApiClient apiClient, SessionProvider sessionProvider) {
        if (apiClient == null || sessionProvider == null) throw new IllegalArgumentException("client and session are required");
        this.apiClient = apiClient;
        this.sessionProvider = sessionProvider;
    }

    @Override
    public List<LyricCandidate> search(TrackInfo track) throws IOException {
        if (track == null || track.getTitle().isEmpty()) return Collections.emptyList();
        String cookie = sessionProvider.getCookie();
        if (cookie == null || cookie.isEmpty()) return Collections.emptyList();
        List<NetEaseApiClient.Song> songs = apiClient.searchSongs(track, cookie);
        List<ScoredSong> ranked = new ArrayList<>();
        for (NetEaseApiClient.Song song : songs) {
            double score = LyricMatcher.metadataScore(track, song.getTitle(), song.getArtist(), song.getDurationMs());
            if (score >= LyricMatcher.ACCEPTANCE_THRESHOLD) ranked.add(new ScoredSong(song, score));
        }
        ranked.sort(Comparator.comparingDouble((ScoredSong item) -> item.score).reversed());
        List<LyricCandidate> matches = new ArrayList<>();
        int limit = Math.min(MAX_LYRIC_FETCHES, ranked.size());
        for (int index = 0; index < limit; index++) {
            NetEaseApiClient.Song song = ranked.get(index).song;
            String lrc = apiClient.getLyric(song.getId(), cookie);
            if (lrc == null || LrcParser.parse(lrc).isEmpty()) continue;
            matches.add(new LyricCandidate(song.getTitle(), song.getArtist(), song.getAlbum(),
                    song.getDurationMs(), lrc, LyricSource.NETEASE));
        }
        return matches;
    }

    public interface SessionProvider {
        String getCookie();
    }

    private static final class ScoredSong {
        final NetEaseApiClient.Song song;
        final double score;
        ScoredSong(NetEaseApiClient.Song song, double score) {
            this.song = song;
            this.score = score;
        }
    }
}
