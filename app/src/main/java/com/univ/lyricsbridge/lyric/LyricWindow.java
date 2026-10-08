package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.LyricLine;
import java.util.List;

public final class LyricWindow {
    private static final LyricWindow EMPTY = new LyricWindow("", "", "");

    private final String previous;
    private final String current;
    private final String next;

    private LyricWindow(String previous, String current, String next) {
        this.previous = previous;
        this.current = current;
        this.next = next;
    }

    public static LyricWindow at(List<LyricLine> lines, int index) {
        if (lines == null || index < 0 || index >= lines.size()) return EMPTY;
        String previous = index > 0 ? lines.get(index - 1).getText() : "";
        String current = lines.get(index).getText();
        String next = index + 1 < lines.size() ? lines.get(index + 1).getText() : "";
        return new LyricWindow(previous, current, next);
    }

    public String getPrevious() {
        return previous;
    }

    public String getCurrent() {
        return current;
    }

    public String getNext() {
        return next;
    }
}
