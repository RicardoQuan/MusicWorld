package com.univ.lyricsbridge.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CarBootStartPolicyTest {
    @Test
    public void startsOnlyForEnabledCarRoleWithSavedDevice() {
        assertTrue(CarBootStartPolicy.shouldStart("car", true, "AA:BB:CC:DD:EE:FF"));
    }

    @Test
    public void doesNotStartForPhoneRoleDisabledSettingOrMissingDevice() {
        assertFalse(CarBootStartPolicy.shouldStart("phone", true, "AA:BB:CC:DD:EE:FF"));
        assertFalse(CarBootStartPolicy.shouldStart("car", false, "AA:BB:CC:DD:EE:FF"));
        assertFalse(CarBootStartPolicy.shouldStart("car", true, " "));
    }
}
