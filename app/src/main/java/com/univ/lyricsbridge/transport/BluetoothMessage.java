package com.univ.lyricsbridge.transport;

import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.model.TrackInfo;

public final class BluetoothMessage {
    public enum Type { HELLO, TRACK, LYRICS, PLAYBACK, STATUS }

    private final Type type;
    private final String deviceName;
    private final TrackInfo track;
    private final String rawLrc;
    private final PlaybackSnapshot playback;
    private final String statusCode;
    private final String statusMessage;

    private BluetoothMessage(Type type, String deviceName, TrackInfo track, String rawLrc,
                             PlaybackSnapshot playback, String statusCode, String statusMessage) {
        this.type = type;
        this.deviceName = clean(deviceName);
        this.track = track;
        this.rawLrc = rawLrc == null ? "" : rawLrc;
        this.playback = playback;
        this.statusCode = clean(statusCode);
        this.statusMessage = clean(statusMessage);
    }

    public static BluetoothMessage hello(String deviceName) {
        return new BluetoothMessage(Type.HELLO, deviceName, null, null, null, null, null);
    }

    public static BluetoothMessage track(TrackInfo track) {
        return new BluetoothMessage(Type.TRACK, null, track, null, null, null, null);
    }

    public static BluetoothMessage lyrics(String rawLrc) {
        return new BluetoothMessage(Type.LYRICS, null, null, rawLrc, null, null, null);
    }

    public static BluetoothMessage playback(PlaybackSnapshot playback) {
        return new BluetoothMessage(Type.PLAYBACK, null, null, null, playback, null, null);
    }

    public static BluetoothMessage status(String code, String message) {
        return new BluetoothMessage(Type.STATUS, null, null, null, null, code, message);
    }

    public Type getType() {
        return type;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public TrackInfo getTrack() {
        return track;
    }

    public String getRawLrc() {
        return rawLrc;
    }

    public PlaybackSnapshot getPlayback() {
        return playback;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
