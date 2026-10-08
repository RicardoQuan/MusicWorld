package com.univ.lyricsbridge.overlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OverlayDragPolicyTest {
    @Test
    public void centersOverlayOn1280By720Display() {
        assertEquals(540, OverlayDragPolicy.centerX(1280, 200));
        assertEquals(310, OverlayDragPolicy.centerY(720, 100));
    }

    @Test
    public void resetPositionReturnsTheCenterForTheCurrentScreenAndOverlaySize() {
        OverlayDragPolicy.Position position = OverlayDragPolicy.resetToCenter(1280, 720, 200, 100);

        assertEquals(540, position.x);
        assertEquals(310, position.y);
    }

    @Test
    public void clampsDraggedOverlayInsideEveryScreenEdge() {
        assertEquals(0, OverlayDragPolicy.clampX(-20, 1280, 200));
        assertEquals(1080, OverlayDragPolicy.clampX(1200, 1280, 200));
        assertEquals(0, OverlayDragPolicy.clampY(-40, 720, 100));
        assertEquals(620, OverlayDragPolicy.clampY(700, 720, 100));
    }

    @Test
    public void oversizedOverlayRemainsAnchoredAtTheScreenOrigin() {
        assertEquals(0, OverlayDragPolicy.clampX(20, 320, 400));
        assertEquals(0, OverlayDragPolicy.clampY(20, 240, 300));
    }
}
