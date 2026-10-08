package com.univ.lyricsbridge.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Test;

public final class BluetoothFrameCodecTest {
    @Test
    public void writesAndReadsNormalFrames() throws Exception {
        byte[] payload = "{\"v\":1,\"type\":\"HELLO\"}".getBytes("UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        BluetoothFrameCodec.writeFrame(output, payload);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        assertArrayEquals(payload, BluetoothFrameCodec.readFrame(input));
        assertNull(BluetoothFrameCodec.readFrame(input));
    }

    @Test
    public void readsFragmentedHeadersAndPayloads() throws Exception {
        byte[] payload = "fragmented payload".getBytes("UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BluetoothFrameCodec.writeFrame(output, payload);
        InputStream fragmented = new FragmentedInputStream(output.toByteArray());

        assertArrayEquals(payload, BluetoothFrameCodec.readFrame(fragmented));
    }

    @Test
    public void rejectsEmptyPayloadsAndOversizedFrames() {
        assertThrows(IOException.class,
                () -> BluetoothFrameCodec.writeFrame(new ByteArrayOutputStream(), new byte[0]));
        byte[] oversizedLength = ByteBuffer.allocate(4)
                .putInt(BluetoothFrameCodec.MAX_FRAME_BYTES + 1).array();
        assertThrows(IOException.class,
                () -> BluetoothFrameCodec.readFrame(new ByteArrayInputStream(oversizedLength)));
    }

    @Test
    public void rejectsEndOfStreamInTheMiddleOfAFrame() {
        byte[] truncated = ByteBuffer.allocate(6).putInt(4).put((byte) 1).put((byte) 2).array();

        assertThrows(EOFException.class,
                () -> BluetoothFrameCodec.readFrame(new ByteArrayInputStream(truncated)));
    }

    private static final class FragmentedInputStream extends InputStream {
        private final ByteArrayInputStream delegate;

        FragmentedInputStream(byte[] value) {
            delegate = new ByteArrayInputStream(value);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, Math.min(1, length));
        }
    }
}
