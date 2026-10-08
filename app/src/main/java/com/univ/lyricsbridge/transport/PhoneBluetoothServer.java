package com.univ.lyricsbridge.transport;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public final class PhoneBluetoothServer implements AutoCloseable {
    public static final UUID SERVICE_UUID = UUID.fromString("70a8c13e-7629-4a49-8d59-f26467371519");
    private static final String SERVICE_NAME = "UNI-V Lyrics Bridge";

    private final BluetoothAdapter adapter;
    private final Listener listener;
    private final Object writeLock = new Object();
    private volatile boolean running;
    private volatile BluetoothServerSocket serverSocket;
    private volatile BluetoothSocket activeSocket;
    private Thread acceptThread;

    public PhoneBluetoothServer(BluetoothAdapter adapter, Listener listener) {
        this.adapter = adapter;
        this.listener = listener;
    }

    public synchronized void start() {
        if (adapter == null) {
            listener.onError("此设备没有蓝牙适配器");
            return;
        }
        if (running && acceptThread != null && acceptThread.isAlive()) return;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "lyrics-rfcomm-server");
        acceptThread.start();
    }

    public boolean send(BluetoothMessage message) {
        BluetoothSocket socket = activeSocket;
        if (!running || socket == null || !socket.isConnected()) return false;
        try {
            byte[] payload = BluetoothMessageCodec.encode(message);
            synchronized (writeLock) {
                if (activeSocket != socket || !socket.isConnected()) return false;
                BluetoothFrameCodec.writeFrame(socket.getOutputStream(), payload);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            closeActiveSocket(socket);
            return false;
        }
    }

    private void acceptLoop() {
        while (running) {
            BluetoothServerSocket server = null;
            try {
                server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
                serverSocket = server;
                BluetoothSocket socket = server.accept();
                if (!running) {
                    closeQuietly(socket);
                    break;
                }
                BluetoothSocket previous = activeSocket;
                if (previous != null) closeActiveSocket(previous);
                activeSocket = socket;
                listener.onConnected(remoteName(socket));
                readMessages(socket);
            } catch (IOException | SecurityException exception) {
                if (running) {
                    listener.onError("蓝牙等待连接失败，请确认蓝牙已开启并授予附近设备权限");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                if (serverSocket == server) serverSocket = null;
                closeQuietly(server);
                BluetoothSocket socket = activeSocket;
                if (socket != null) closeActiveSocket(socket);
            }
        }
    }

    private void readMessages(BluetoothSocket socket) {
        try {
            InputStream input = socket.getInputStream();
            while (running && socket == activeSocket) {
                byte[] frame = BluetoothFrameCodec.readFrame(input);
                if (frame == null) break;
                listener.onMessage(BluetoothMessageCodec.decode(frame));
            }
        } catch (IOException | RuntimeException exception) {
            if (running && socket == activeSocket) listener.onError("车机连接中断");
        } finally {
            if (socket == activeSocket) closeActiveSocket(socket);
        }
    }

    private void closeActiveSocket(BluetoothSocket socket) {
        if (socket == null) return;
        if (activeSocket == socket) activeSocket = null;
        closeQuietly(socket);
        listener.onDisconnected();
    }

    private static String remoteName(BluetoothSocket socket) {
        try {
            String name = socket.getRemoteDevice().getName();
            return name == null ? "已配对设备" : name;
        } catch (SecurityException ignored) {
            return "已配对设备";
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Closing a Bluetooth socket is best effort during shutdown/reconnect.
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        closeQuietly(serverSocket);
        BluetoothSocket socket = activeSocket;
        if (socket != null) closeActiveSocket(socket);
        Thread thread = acceptThread;
        if (thread != null) thread.interrupt();
        acceptThread = null;
    }

    public interface Listener {
        void onConnected(String remoteDeviceName);
        void onDisconnected();
        void onMessage(BluetoothMessage message);
        void onError(String message);
    }
}
