package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.LyricLine;
import com.univ.lyricsbridge.model.TrackInfo;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public final class LyricMatcher {
    public static final double ACCEPTANCE_THRESHOLD = 0.85;

    private LyricMatcher() {
    }

    public static Match bestMatch(TrackInfo track, List<LyricCandidate> candidates) {
        if (track == null || candidates == null || candidates.isEmpty()) return null;
        String trackTitle = normalize(track.getTitle());
        String trackArtist = normalizeArtist(track.getArtist());
        if (trackTitle.isEmpty() || trackArtist.isEmpty()) return null;

        Match best = null;
        for (LyricCandidate candidate : candidates) {
            if (candidate == null || LrcParser.parse(candidate.getRawLrc()).isEmpty()) continue;
            double score = score(track, candidate, trackTitle, trackArtist);
            if (score >= ACCEPTANCE_THRESHOLD && (best == null || score > best.score)) {
                best = new Match(candidate, score);
            }
        }
        return best;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static double metadataScore(TrackInfo track, String candidateTitle,
                                       String candidateArtist, long candidateDurationMs) {
        if (track == null) return 0;
        String trackTitle = normalize(track.getTitle());
        String trackArtist = normalizeArtist(track.getArtist());
        if (trackTitle.isEmpty() || trackArtist.isEmpty()) return 0;
        return scoreMetadata(track, candidateTitle, candidateArtist, candidateDurationMs,
                trackTitle, trackArtist);
    }

    private static String normalizeArtist(String value) {
        String normalized = normalize(value);
        return normalized.replace("張惠妹", "张惠妹").replace("amei", "张惠妹");
    }

    private static double score(TrackInfo track, LyricCandidate candidate,
                                String trackTitle, String trackArtist) {
        return scoreMetadata(track, candidate.getTitle(), candidate.getArtist(),
                candidate.getDurationMs(), trackTitle, trackArtist);
    }

    private static double scoreMetadata(TrackInfo track, String title, String artist,
                                        long candidateDurationMs, String trackTitle, String trackArtist) {
        String candidateTitle = normalize(title);
        String candidateArtist = normalizeArtist(artist);
        if (candidateTitle.isEmpty() || candidateArtist.isEmpty()) return 0;

        double score = 0;
        if (trackTitle.equals(candidateTitle)) {
            score += 0.60;
        } else if (trackTitle.contains(candidateTitle) || candidateTitle.contains(trackTitle)) {
            score += 0.45;
        }

        if (trackArtist.equals(candidateArtist)) {
            score += 0.30;
        } else if (trackArtist.contains(candidateArtist) || candidateArtist.contains(trackArtist)) {
            score += 0.20;
        }

        long trackDuration = track.getDurationMs();
        long candidateDuration = candidateDurationMs;
        if (trackDuration > 0 && candidateDuration > 0
                && absoluteDifference(trackDuration, candidateDuration) <= 5000) {
            score += 0.10;
        }
        return score;
    }

    private static long absoluteDifference(long left, long right) {
        if (left >= right) return left - right;
        return right - left;
    }

    public static final class Match {
        private final LyricCandidate candidate;
        private final double score;

        private Match(LyricCandidate candidate, double score) {
            this.candidate = candidate;
            this.score = score;
        }

        public LyricCandidate getCandidate() {
            return candidate;
        }

        public double getScore() {
            return score;
        }
    }
}
