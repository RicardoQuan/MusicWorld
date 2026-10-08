package com.univ.lyricsbridge.sync;

import static org.junit.Assert.assertEquals;

import com.univ.lyricsbridge.model.LyricLine;
import com.univ.lyricsbridge.model.PlaybackSnapshot;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class PlaybackSyncEngineTest {
    @Test
    public void interpolatesPositionWhilePlayingAtReportedSpeed() {
        PlaybackSnapshot snapshot = new PlaybackSnapshot(1200, 4000, true, 1.5f, 10000);

        assertEquals(1800, PlaybackSyncEngine.positionAt(snapshot, 4400));
    }

    @Test
    public void keepsPausedPositionAndClampsToDuration() {
        PlaybackSnapshot paused = new PlaybackSnapshot(2500, 4000, false, 1.0f, 10000);
        PlaybackSnapshot playing = new PlaybackSnapshot(9500, 0, true, 2.0f, 10000);

        assertEquals(2500, PlaybackSyncEngine.positionAt(paused, 9000));
        assertEquals(10000, PlaybackSyncEngine.positionAt(playing, 1000));
    }

    @Test
    public void reanchorsReceivedPlaybackToTheReceiverClockDomain() {
        PlaybackSnapshot receivedFromPhone = new PlaybackSnapshot(4200, 50000, true, 1.5f, 10000);

        PlaybackSnapshot local = PlaybackSyncEngine.reanchorReceived(receivedFromPhone, 800);

        assertEquals(4200, local.getPositionMs());
        assertEquals(800, local.getSampledAtElapsedRealtimeMs());
        assertEquals(5700, PlaybackSyncEngine.positionAt(local, 1800));
    }

    @Test
    public void findsLastLineAtOrBeforePosition() {
        List<LyricLine> lines = Arrays.asList(
                new LyricLine(1000, "first"),
                new LyricLine(2000, "second"));

        assertEquals(-1, PlaybackSyncEngine.lineIndexAt(lines, 999));
        assertEquals(0, PlaybackSyncEngine.lineIndexAt(lines, 1000));
        assertEquals(0, PlaybackSyncEngine.lineIndexAt(lines, 1999));
        assertEquals(1, PlaybackSyncEngine.lineIndexAt(lines, 2000));
    }
}
