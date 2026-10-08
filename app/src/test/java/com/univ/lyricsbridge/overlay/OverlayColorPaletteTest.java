package com.univ.lyricsbridge.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OverlayColorPaletteTest {
    @Test
    public void defaultsAreSelectableAndPaletteArraysAreDefensiveCopies() {
        assertTrue(OverlayColorPalette.indexOfColor(OverlayColorPalette.DEFAULT_SUNG_COLOR) >= 0);
        assertTrue(OverlayColorPalette.indexOfColor(OverlayColorPalette.DEFAULT_UNSUNG_COLOR) >= 0);

        String originalLabel = OverlayColorPalette.labelAt(0);
        String[] labels = OverlayColorPalette.labels();
        labels[0] = "changed";
        assertEquals(originalLabel, OverlayColorPalette.labelAt(0));
    }

    @Test
    public void unknownCustomColorDoesNotPretendToBeAPreset() {
        assertEquals(-1, OverlayColorPalette.indexOfColor(0xFF123456));
    }
}
