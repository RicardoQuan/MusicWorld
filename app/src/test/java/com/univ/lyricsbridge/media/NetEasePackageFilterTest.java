package com.univ.lyricsbridge.media;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NetEasePackageFilterTest {
    @Test
    public void acceptsOnlyTheNetEaseCloudMusicPackage() {
        assertTrue(NetEasePackageFilter.accepts("com.netease.cloudmusic"));
        assertFalse(NetEasePackageFilter.accepts("com.netease.cloudmusic.lite"));
        assertFalse(NetEasePackageFilter.accepts("com.example.netease.cloudmusic"));
        assertFalse(NetEasePackageFilter.accepts(null));
    }
}
