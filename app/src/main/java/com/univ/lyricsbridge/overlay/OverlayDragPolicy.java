package com.univ.lyricsbridge.overlay;

/** Keeps a draggable overlay within the visible display bounds. */
public final class OverlayDragPolicy {
    private OverlayDragPolicy() {
    }

    public static int centerX(int screenWidth, int overlayWidth) {
        return clampX((screenWidth - overlayWidth) / 2, screenWidth, overlayWidth);
    }

    public static int centerY(int screenHeight, int overlayHeight) {
        return clampY((screenHeight - overlayHeight) / 2, screenHeight, overlayHeight);
    }

    public static Position resetToCenter(int screenWidth, int screenHeight, int overlayWidth, int overlayHeight) {
        return new Position(centerX(screenWidth, overlayWidth), centerY(screenHeight, overlayHeight));
    }

    public static int clampX(int x, int screenWidth, int overlayWidth) {
        return clamp(x, screenWidth, overlayWidth);
    }

    public static int clampY(int y, int screenHeight, int overlayHeight) {
        return clamp(y, screenHeight, overlayHeight);
    }

    private static int clamp(int position, int screenSize, int overlaySize) {
        int max = Math.max(0, screenSize - Math.max(0, overlaySize));
        return Math.max(0, Math.min(position, max));
    }

    public static final class Position {
        public final int x;
        public final int y;

        private Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
