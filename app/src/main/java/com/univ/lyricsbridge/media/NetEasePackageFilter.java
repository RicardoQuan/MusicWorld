package com.univ.lyricsbridge.media;

public final class NetEasePackageFilter {
    private static final String NETEASE_PACKAGE = "com.netease.cloudmusic";

    private NetEasePackageFilter() {
    }

    public static boolean accepts(String packageName) {
        return NETEASE_PACKAGE.equals(packageName);
    }
}
