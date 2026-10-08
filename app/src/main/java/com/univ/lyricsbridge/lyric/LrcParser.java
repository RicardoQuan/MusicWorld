package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.LyricLine;
import com.univ.lyricsbridge.model.LyricWord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d+):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?i)\\[offset:([+-]?\\d+)\\]");
    private static final Pattern METADATA = Pattern.compile("\\[[A-Za-z][A-Za-z0-9_-]*:[^\\]]*\\]");
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),0\\)");

    private LrcParser() {
    }

    public static List<LyricLine> parse(String source) {
        List<LyricLine> result = new ArrayList<>();
        if (source == null || source.isEmpty()) return result;

        String input = source.charAt(0) == '\uFEFF' ? source.substring(1) : source;
        long offsetMs = parseOffset(input);
        for (String rawLine : input.split("\\R")) {
            LyricLine yrcLine = parseYrcLine(rawLine);
            if (yrcLine != null) {
                result.add(yrcLine);
                continue;
            }
            Matcher matcher = TIMESTAMP.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            int lastTimestampEnd = -1;
            while (matcher.find()) {
                Long timestamp = parseTimestamp(matcher);
                if (timestamp != null) timestamps.add(timestamp);
                lastTimestampEnd = matcher.end();
            }
            if (timestamps.isEmpty() || lastTimestampEnd < 0) continue;

            String text = METADATA.matcher(rawLine.substring(lastTimestampEnd)).replaceAll("").trim();
            if (text.isEmpty()) continue;
            for (long timestamp : timestamps) {
                long shifted;
                try {
                    shifted = Math.addExact(timestamp, offsetMs);
                } catch (ArithmeticException ignored) {
                    shifted = offsetMs > 0 ? Long.MAX_VALUE : 0;
                }
                result.add(new LyricLine(Math.max(0, shifted), text));
            }
        }
        result.sort(Comparator.comparingLong(LyricLine::getTimestampMs));
        return result;
    }

    private static LyricLine parseYrcLine(String rawLine) {
        Matcher header = YRC_LINE.matcher(rawLine);
        if (!header.matches()) return null;
        try {
            long lineStart = Long.parseLong(header.group(1));
            Long.parseLong(header.group(2)); // Validate duration even though word timings drive rendering.
            String body = header.group(3);
            Matcher wordMatcher = YRC_WORD.matcher(body);
            List<LyricWord> words = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            int previousEnd = 0;
            long pendingStart = -1;
            long pendingDuration = 0;
            while (wordMatcher.find()) {
                if (pendingStart >= 0) {
                    String segment = body.substring(previousEnd, wordMatcher.start());
                    if (!segment.isEmpty()) {
                        words.add(new LyricWord(segment, pendingStart, pendingDuration));
                        text.append(segment);
                    }
                }
                pendingStart = Long.parseLong(wordMatcher.group(1));
                pendingDuration = Long.parseLong(wordMatcher.group(2));
                previousEnd = wordMatcher.end();
            }
            if (pendingStart >= 0) {
                String segment = body.substring(previousEnd);
                if (!segment.isEmpty()) {
                    words.add(new LyricWord(segment, pendingStart, pendingDuration));
                    text.append(segment);
                }
            }
            if (words.isEmpty()) return null;
            return text.toString().trim().isEmpty() ? null : new LyricLine(lineStart, text.toString(), words);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long parseOffset(String input) {
        Matcher matcher = OFFSET.matcher(input);
        if (!matcher.find()) return 0;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Long parseTimestamp(Matcher matcher) {
        try {
            long minutes = Long.parseLong(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            if (seconds > 59) return null;
            String fraction = matcher.group(3);
            long fractionMs = 0;
            if (fraction != null) {
                fractionMs = Long.parseLong(fraction);
                if (fraction.length() == 1) fractionMs *= 100;
                if (fraction.length() == 2) fractionMs *= 10;
            }
            return Math.addExact(Math.addExact(Math.multiplyExact(minutes, 60000L), seconds * 1000L), fractionMs);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }
}
