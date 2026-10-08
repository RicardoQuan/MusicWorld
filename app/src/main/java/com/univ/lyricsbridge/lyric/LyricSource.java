package com.univ.lyricsbridge.lyric;

/** The service that supplied a matched lyric. */
public enum LyricSource {
    LOCAL_LRC("本地 LRC"),
    NETEASE("网易云音乐"),
    LRCLIB("LRCLIB"),
    UNKNOWN("");

    private final String displayName;

    LyricSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
