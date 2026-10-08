package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;

import com.univ.lyricsbridge.model.LyricLine;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class LyricWindowTest {
    private final List<LyricLine> lines = Arrays.asList(
            new LyricLine(0, "one"),
            new LyricLine(1000, "two"),
            new LyricLine(2000, "three"));

    @Test
    public void firstLineHasNoPreviousLine() {
        LyricWindow window = LyricWindow.at(lines, 0);

        assertEquals("", window.getPrevious());
        assertEquals("one", window.getCurrent());
        assertEquals("two", window.getNext());
    }

    @Test
    public void middleLineShowsPreviousCurrentAndNext() {
        LyricWindow window = LyricWindow.at(lines, 1);

        assertEquals("one", window.getPrevious());
        assertEquals("two", window.getCurrent());
        assertEquals("three", window.getNext());
    }

    @Test
    public void lastLineHasNoNextLine() {
        LyricWindow window = LyricWindow.at(lines, 2);

        assertEquals("two", window.getPrevious());
        assertEquals("three", window.getCurrent());
        assertEquals("", window.getNext());
    }

    @Test
    public void invalidIndexReturnsAnEmptyWindow() {
        LyricWindow window = LyricWindow.at(lines, -1);

        assertEquals("", window.getPrevious());
        assertEquals("", window.getCurrent());
        assertEquals("", window.getNext());
    }
}
