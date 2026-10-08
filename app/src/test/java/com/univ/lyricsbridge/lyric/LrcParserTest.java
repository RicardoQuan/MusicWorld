package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;

import com.univ.lyricsbridge.model.LyricLine;
import com.univ.lyricsbridge.model.LyricWord;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class LrcParserTest {
    @Test
    public void parsesMultipleTimestampsAndIgnoresMetadata() {
        String lrc = "[ti:Song]\n[00:02.00]Second\n[00:01.250][00:04.00]First";

        List<LyricLine> lines = LrcParser.parse(lrc);

        assertEquals(Arrays.asList(
                new LyricLine(1250, "First"),
                new LyricLine(2000, "Second"),
                new LyricLine(4000, "First")
        ), lines);
    }

    @Test
    public void stripsBomAppliesOffsetAndKeepsInputOrderForTies() {
        String lrc = "\uFEFF[offset:+500]\n[00:01.00]A\n[00:01.50]B\n"
                + "[00:01.00]Tie\n[bad]ignored\n[00:02.00]   ";

        List<LyricLine> lines = LrcParser.parse(lrc);

        assertEquals(Arrays.asList(
                new LyricLine(1500, "A"),
                new LyricLine(1500, "Tie"),
                new LyricLine(2000, "B")
        ), lines);
    }

    @Test
    public void parsesAbsoluteWordTimingsFromNetEaseYrc() {
        List<LyricLine> lines = LrcParser.parse(
                "[190871,1984](190871,361,0)你(191232,172,0)好");

        assertEquals(1, lines.size());
        assertEquals("你好", lines.get(0).getText());
        assertEquals(190871, lines.get(0).getTimestampMs());
        assertEquals(Arrays.asList(new LyricWord("你", 190871, 361),
                new LyricWord("好", 191232, 172)), lines.get(0).getWords());
    }
}
