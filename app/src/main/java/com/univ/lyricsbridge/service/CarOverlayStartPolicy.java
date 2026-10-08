package com.univ.lyricsbridge.service;

public final class CarOverlayStartPolicy {
    private CarOverlayStartPolicy() {
    }

    public static boolean shouldShowOnConnectRequest(boolean autoShowRequested, String deviceAddress) {
        return autoShowRequested && deviceAddress != null && !deviceAddress.trim().isEmpty();
    }

    public static boolean shouldShowAfterConnection(
            boolean autoShowRequested, String connectedAddress, String selectedAddress) {
        return autoShowRequested
                && connectedAddress != null
                && !connectedAddress.trim().isEmpty()
                && connectedAddress.equals(selectedAddress);
    }
}
