package com.univ.lyricsbridge.model;

public final class PlaybackSnapshot {
    private final long positionMs;
    private final long sampledAtElapsedRealtimeMs;
    private final boolean playing;
    private final float speed;
    private final long durationMs;

    public PlaybackSnapshot(long positionMs, long sampledAtElapsedRealtimeMs,
                            boolean playing, float speed, long durationMs) {
        this.positionMs = Math.max(0, positionMs);
        this.sampledAtElapsedRealtimeMs = Math.max(0, sampledAtElapsedRealtimeMs);
        this.playing = playing;
        this.speed = Float.isFinite(speed) && speed > 0 ? speed : 1.0f;
        this.durationMs = Math.max(0, durationMs);
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getSampledAtElapsedRealtimeMs() {
        return sampledAtElapsedRealtimeMs;
    }

    public boolean isPlaying() {
        return playing;
    }

    public float getSpeed() {
        return speed;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
