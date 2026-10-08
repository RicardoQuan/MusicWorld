package com.univ.lyricsbridge.transport;

import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

public final class BluetoothMessageCodec {
    private static final int PROTOCOL_VERSION = 1;

    private BluetoothMessageCodec() {
    }

    public static byte[] encode(BluetoothMessage message) throws IOException {
        if (message == null) throw new IOException("Bluetooth message is required");
        JSONObject json = new JSONObject();
        try {
            json.put("v", PROTOCOL_VERSION);
            json.put("type", message.getType().name());
            switch (message.getType()) {
                case HELLO:
                    json.put("deviceName", message.getDeviceName());
                    break;
                case TRACK:
                    TrackInfo track = requireTrack(message.getTrack());
                    json.put("title", track.getTitle());
                    json.put("artist", track.getArtist());
                    json.put("album", track.getAlbum());
                    json.put("packageName", track.getPackageName());
                    json.put("durationMs", track.getDurationMs());
                    break;
                case LYRICS:
                    json.put("rawLrc", message.getRawLrc());
                    break;
                case PLAYBACK:
                    PlaybackSnapshot playback = requirePlayback(message.getPlayback());
                    json.put("positionMs", playback.getPositionMs());
                    json.put("sampledAtElapsedRealtimeMs", playback.getSampledAtElapsedRealtimeMs());
                    json.put("playing", playback.isPlaying());
                    json.put("speed", playback.getSpeed());
                    json.put("durationMs", playback.getDurationMs());
                    break;
                case STATUS:
                    json.put("code", message.getStatusCode());
                    json.put("message", message.getStatusMessage());
                    break;
                default:
                    throw new IOException("Unsupported message type");
            }
            byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
            if (payload.length == 0 || payload.length > BluetoothFrameCodec.MAX_FRAME_BYTES) {
                throw new IOException("Bluetooth message exceeds the frame size limit");
            }
            return payload;
        } catch (JSONException exception) {
            throw new IOException("Could not encode Bluetooth message", exception);
        }
    }

    public static BluetoothMessage decode(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0 || payload.length > BluetoothFrameCodec.MAX_FRAME_BYTES) {
            throw new IOException("Invalid Bluetooth message size");
        }
        try {
            JSONObject json = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            if (json.getInt("v") != PROTOCOL_VERSION) throw new IOException("Unsupported protocol version");
            BluetoothMessage.Type type = BluetoothMessage.Type.valueOf(json.getString("type"));
            switch (type) {
                case HELLO:
                    return BluetoothMessage.hello(json.optString("deviceName", ""));
                case TRACK:
                    return BluetoothMessage.track(new TrackInfo(
                            json.getString("title"), json.getString("artist"),
                            json.optString("album", ""), json.optString("packageName", ""),
                            json.getLong("durationMs")));
                case LYRICS:
                    return BluetoothMessage.lyrics(json.getString("rawLrc"));
                case PLAYBACK:
                    return BluetoothMessage.playback(new PlaybackSnapshot(
                            json.getLong("positionMs"), json.getLong("sampledAtElapsedRealtimeMs"),
                            json.getBoolean("playing"), (float) json.getDouble("speed"),
                            json.getLong("durationMs")));
                case STATUS:
                    return BluetoothMessage.status(json.getString("code"), json.optString("message", ""));
                default:
                    throw new IOException("Unsupported message type");
            }
        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException("Invalid Bluetooth message", exception);
        }
    }

    private static TrackInfo requireTrack(TrackInfo track) throws IOException {
        if (track == null) throw new IOException("TRACK message has no track metadata");
        return track;
    }

    private static PlaybackSnapshot requirePlayback(PlaybackSnapshot playback) throws IOException {
        if (playback == null) throw new IOException("PLAYBACK message has no playback state");
        return playback;
    }
}
