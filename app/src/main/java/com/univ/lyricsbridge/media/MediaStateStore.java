package com.univ.lyricsbridge.media;

import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.model.TrackInfo;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MediaStateStore {
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile State current = State.noPlayer();

    private MediaStateStore() {
    }

    public static State current() {
        return current;
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        LISTENERS.addIfAbsent(listener);
        listener.onMediaState(current);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void publish(State state) {
        current = state == null ? State.noPlayer() : state;
        for (Listener listener : LISTENERS) listener.onMediaState(current);
    }

    public interface Listener {
        void onMediaState(State state);
    }

    public static final class State {
        private final boolean hasSession;
        private final TrackInfo track;
        private final PlaybackSnapshot playback;

        public State(boolean hasSession, TrackInfo track, PlaybackSnapshot playback) {
            this.hasSession = hasSession;
            this.track = track;
            this.playback = playback;
        }

        public static State noPlayer() {
            return new State(false, null, null);
        }

        public boolean hasSession() {
            return hasSession;
        }

        public TrackInfo getTrack() {
            return track;
        }

        public PlaybackSnapshot getPlayback() {
            return playback;
        }
    }
}
