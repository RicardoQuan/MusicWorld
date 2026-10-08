package com.univ.lyricsbridge.overlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TrackMetadataFormatterTest {
    @Test
    public void combinesTitleAndArtistForOverlayHeader() {
        assertEquals("掉了 · 张惠妹", TrackMetadataFormatter.format("掉了", "张惠妹"));
    }

    @Test
    public void formatsAvailableMetadataWithoutEmptySeparators() {
        assertEquals("掉了", TrackMetadataFormatter.format("掉了", ""));
        assertEquals("张惠妹", TrackMetadataFormatter.format("", "张惠妹"));
        assertEquals("", TrackMetadataFormatter.format(null, null));
    }
}
