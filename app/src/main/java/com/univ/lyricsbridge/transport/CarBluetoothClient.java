package com.univ.lyricsbridge.transport;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.util.UUID;

public final class CarBluetoothClient implements AutoCloseable {
    private final BluetoothAdapter adapter;
    private BluetoothSocket socket;

    public CarBluetoothClient(BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    public String connect(String deviceAddress) throws IOException {
        if (adapter == null) throw new IOException("此设备没有蓝牙适配器");
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(deviceAddress);
            socket = device.createRfcommSocketToServiceRecord(PhoneBluetoothServer.SERVICE_UUID);
            socket.connect();
            String name = device.getName();
            return name == null ? deviceAddress : name;
        } catch (IllegalArgumentException | SecurityException exception) {
            close();
            throw new IOException("无法访问已配对蓝牙设备", exception);
        } catch (IOException exception) {
            close();
            throw exception;
        }
    }

    public void send(BluetoothMessage message) throws IOException {
        BluetoothSocket current = socket;
        if (current == null || !current.isConnected()) throw new IOException("蓝牙尚未连接");
        BluetoothFrameCodec.writeFrame(current.getOutputStream(), BluetoothMessageCodec.encode(message));
    }

    public BluetoothMessage read() throws IOException {
        BluetoothSocket current = socket;
        if (current == null || !current.isConnected()) throw new IOException("蓝牙尚未连接");
        byte[] frame = BluetoothFrameCodec.readFrame(current.getInputStream());
        return frame == null ? null : BluetoothMessageCodec.decode(frame);
    }

    @Override
    public void close() {
        BluetoothSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // The service reconnect loop will create a new socket.
            }
        }
    }
}
