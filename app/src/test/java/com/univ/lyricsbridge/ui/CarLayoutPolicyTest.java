package com.univ.lyricsbridge.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CarLayoutPolicyTest {
    @Test
    public void usesTwoColumnsForWideLandscapeHeadUnit() {
        assertTrue(CarLayoutPolicy.useTwoColumns(1280, true));
        assertTrue(CarLayoutPolicy.useTwoColumns(720, true));
    }

    @Test
    public void stacksControlsOnNarrowOrPortraitScreens() {
        assertFalse(CarLayoutPolicy.useTwoColumns(719, true));
        assertFalse(CarLayoutPolicy.useTwoColumns(900, false));
    }
}
