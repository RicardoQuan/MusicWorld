package com.univ.lyricsbridge.ui;

public final class CarLayoutPolicy {
    private CarLayoutPolicy() {
    }

    public static boolean useTwoColumns(int availableWidthDp, boolean landscape) {
        return landscape && availableWidthDp >= 720;
    }
}
