package com.univ.lyricsbridge.transport;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class BluetoothFrameCodec {
    public static final int MAX_FRAME_BYTES = 512 * 1024;

    private BluetoothFrameCodec() {
    }

    public static void writeFrame(OutputStream output, byte[] payload) throws IOException {
        if (output == null) throw new IOException("Output stream is unavailable");
        if (payload == null || payload.length == 0) throw new IOException("Frame payload must not be empty");
        if (payload.length > MAX_FRAME_BYTES) throw new IOException("Frame exceeds the size limit");
        byte[] header = ByteBuffer.allocate(Integer.BYTES).putInt(payload.length).array();
        output.write(header);
        output.write(payload);
        output.flush();
    }

    /** Returns null only when the stream ends cleanly before the start of a frame. */
    public static byte[] readFrame(InputStream input) throws IOException {
        if (input == null) throw new IOException("Input stream is unavailable");
        int first = input.read();
        if (first < 0) return null;
        byte[] header = new byte[Integer.BYTES];
        header[0] = (byte) first;
        readFully(input, header, 1, Integer.BYTES - 1);
        int length = ByteBuffer.wrap(header).getInt();
        if (length <= 0) throw new IOException("Invalid empty frame length");
        if (length > MAX_FRAME_BYTES) throw new IOException("Frame exceeds the size limit");
        byte[] payload = new byte[length];
        readFully(input, payload, 0, length);
        return payload;
    }

    private static void readFully(InputStream input, byte[] target, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int count = input.read(target, offset + total, length - total);
            if (count < 0) throw new EOFException("Stream ended in the middle of a Bluetooth frame");
            if (count == 0) {
                int singleByte = input.read();
                if (singleByte < 0) throw new EOFException("Stream ended in the middle of a Bluetooth frame");
                target[offset + total] = (byte) singleByte;
                total++;
            } else {
                total += count;
            }
        }
    }
}
