package com.univ.lyricsbridge.sync;

import com.univ.lyricsbridge.model.LyricLine;
import com.univ.lyricsbridge.model.PlaybackSnapshot;
import java.util.List;

public final class PlaybackSyncEngine {
    private PlaybackSyncEngine() {
    }

    public static long positionAt(PlaybackSnapshot snapshot, long nowElapsedRealtimeMs) {
        if (snapshot == null) return 0;
        double position = snapshot.getPositionMs();
        if (snapshot.isPlaying()) {
            long elapsed = Math.max(0, nowElapsedRealtimeMs - snapshot.getSampledAtElapsedRealtimeMs());
            position += elapsed * (double) snapshot.getSpeed();
        }
        long rounded = position >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(position);
        rounded = Math.max(0, rounded);
        long duration = snapshot.getDurationMs();
        return duration > 0 ? Math.min(rounded, duration) : rounded;
    }

    /** The sender's monotonic clock has a different origin on another device. */
    public static PlaybackSnapshot reanchorReceived(PlaybackSnapshot received,
                                                     long receiverElapsedRealtimeMs) {
        if (received == null) return null;
        return new PlaybackSnapshot(received.getPositionMs(), receiverElapsedRealtimeMs,
                received.isPlaying(), received.getSpeed(), received.getDurationMs());
    }

    public static int lineIndexAt(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (lines.get(middle).getTimestampMs() <= positionMs) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low - 1;
    }
}
