package com.univ.lyricsbridge.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CarOverlayStartPolicyTest {
    @Test
    public void connectRequestShowsOverlayEvenWhenServiceIsAlreadyConnected() {
        assertTrue(CarOverlayStartPolicy.shouldShowOnConnectRequest(true, "AA:BB:CC:DD:EE:FF"));
    }

    @Test
    public void connectRequestDoesNotShowOverlayWhenAutoShowIsDisabledOrDeviceMissing() {
        assertFalse(CarOverlayStartPolicy.shouldShowOnConnectRequest(false, "AA:BB:CC:DD:EE:FF"));
        assertFalse(CarOverlayStartPolicy.shouldShowOnConnectRequest(true, ""));
    }
}
