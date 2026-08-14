package com.gitsilence.musicbox.client.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricTimeline {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    public record Line(long millis, String text) { }
    private final List<Line> lines;
    private LyricTimeline(List<Line> lines) { this.lines = List.copyOf(lines); }

    public static LyricTimeline parse(String lrc) {
        List<Line> lines = new ArrayList<>();
        if (lrc == null) return new LyricTimeline(lines);
        for (String raw : lrc.lines().toList()) {
            Matcher matcher = TIMESTAMP.matcher(raw);
            int textStart = 0;
            List<Long> times = new ArrayList<>();
            while (matcher.find()) {
                long fraction = matcher.group(3) == null ? 0 : fractionMillis(matcher.group(3));
                times.add(Long.parseLong(matcher.group(1)) * 60_000L
                        + Long.parseLong(matcher.group(2)) * 1_000L + fraction);
                textStart = matcher.end();
            }
            String text = raw.substring(textStart).strip();
            if (!text.isEmpty()) times.forEach(time -> lines.add(new Line(time, text)));
        }
        lines.sort(Comparator.comparingLong(Line::millis));
        return new LyricTimeline(lines);
    }

    private static long fractionMillis(String value) {
        long fraction = Long.parseLong(value);
        return value.length() == 1 ? fraction * 100 : value.length() == 2 ? fraction * 10 : fraction;
    }

    public String at(long millis) {
        String current = "";
        for (Line line : lines) {
            if (line.millis() > millis) break;
            current = line.text();
        }
        return current;
    }

    public List<Line> lines() { return lines; }
}
