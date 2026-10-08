package com.univ.lyricsbridge.data;

import android.os.Handler;
import android.os.Looper;
import com.univ.lyricsbridge.model.LyricLine;
import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.model.TrackInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CarStateStore {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile State current = new State("未连接", null,
            Collections.emptyList(), null, "");

    private CarStateStore() {
    }

    public static State current() {
        return current;
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        LISTENERS.addIfAbsent(listener);
        dispatch(listener, current);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void setStatus(String status, boolean clearMedia) {
        State old = current;
        publish(new State(status, clearMedia ? null : old.track,
                clearMedia ? Collections.emptyList() : old.lines,
                clearMedia ? null : old.playback, old.senderName));
    }

    public static void setSenderName(String senderName) {
        State old = current;
        publish(new State(old.connectionStatus, old.track, old.lines, old.playback, senderName));
    }

    public static void setTrack(TrackInfo track) {
        State old = current;
        publish(new State(old.connectionStatus, track, Collections.emptyList(), null, old.senderName));
    }

    public static void setLyrics(List<LyricLine> lines) {
        State old = current;
        List<LyricLine> safe = lines == null ? Collections.emptyList() : new ArrayList<>(lines);
        publish(new State(old.connectionStatus, old.track, safe, old.playback, old.senderName));
    }

    public static void setPlayback(PlaybackSnapshot playback) {
        State old = current;
        publish(new State(old.connectionStatus, old.track, old.lines, playback, old.senderName));
    }

    private static void publish(State state) {
        current = state;
        for (Listener listener : LISTENERS) dispatch(listener, state);
    }

    private static void dispatch(Listener listener, State state) {
        if (Looper.myLooper() == Looper.getMainLooper()) listener.onCarState(state);
        else MAIN.post(() -> listener.onCarState(state));
    }

    public interface Listener {
        void onCarState(State state);
    }

    public static final class State {
        private final String connectionStatus;
        private final TrackInfo track;
        private final List<LyricLine> lines;
        private final PlaybackSnapshot playback;
        private final String senderName;

        State(String connectionStatus, TrackInfo track, List<LyricLine> lines,
              PlaybackSnapshot playback, String senderName) {
            this.connectionStatus = connectionStatus == null ? "" : connectionStatus;
            this.track = track;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            this.playback = playback;
            this.senderName = senderName == null ? "" : senderName;
        }

        public String getConnectionStatus() {
            return connectionStatus;
        }

        public TrackInfo getTrack() {
            return track;
        }

        public List<LyricLine> getLines() {
            return lines;
        }

        public PlaybackSnapshot getPlayback() {
            return playback;
        }

        public String getSenderName() {
            return senderName;
        }
    }
}
