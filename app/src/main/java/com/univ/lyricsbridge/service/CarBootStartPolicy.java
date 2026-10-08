package com.univ.lyricsbridge.service;

public final class CarBootStartPolicy {
    private CarBootStartPolicy() {
    }

    public static boolean shouldStart(String role, boolean enabled, String deviceAddress) {
        return "car".equals(role) && enabled
                && deviceAddress != null && !deviceAddress.trim().isEmpty();
    }
}
