package com.switchbase.sounds;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 유튜브 주소와 시작 시간을 해석한다 (sound-compare-design §4).
 * 받는 주소: youtube.com/watch?v=, youtu.be/, youtube.com/shorts/ 와 11자 영상 id.
 * 시작 시간: 83, 1:23, 1:02:03 형식. 음수와 99:59:59를 넘는 값은 거부한다.
 */
final class YoutubeRef {

    static final int MAX_START_SECONDS = 99 * 3600 + 59 * 60 + 59;

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern SECONDS = Pattern.compile("\\d{1,6}");
    private static final Pattern MM_SS = Pattern.compile("(\\d{1,4}):([0-5]?\\d)");
    private static final Pattern H_MM_SS = Pattern.compile("(\\d{1,2}):([0-5]\\d):([0-5]\\d)");
    private static final String BAD_URL = "유튜브 주소를 확인해 주세요. 예: https://youtu.be/영상id";
    private static final String BAD_START = "시작 시간은 1:23 또는 83처럼 적어 주세요.";

    private YoutubeRef() {}

    /** 주소나 영상 id에서 11자 영상 id를 꺼낸다. 맞지 않으면 IllegalArgumentException. */
    static String videoId(String input) {
        String s = input == null ? "" : input.strip();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("유튜브 주소를 적어 주세요.");
        }
        if (VIDEO_ID.matcher(s).matches()) {
            return s;
        }
        URI uri = parse(s);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String id;
        if (host.equals("youtu.be")) {
            id = firstSegment(path);
        } else if (host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
            if (path.equals("/watch")) {
                id = queryParam(uri.getRawQuery(), "v");
            } else if (path.startsWith("/shorts/")) {
                id = firstSegment(path.substring("/shorts/".length()));
            } else {
                id = null;
            }
        } else {
            id = null;
        }
        if (id == null || !VIDEO_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(BAD_URL);
        }
        return id;
    }

    /** 시작 시간을 초로. 비어 있으면 0. */
    static int startSeconds(String input) {
        String s = input == null ? "" : input.strip();
        if (s.isEmpty()) {
            return 0;
        }
        long value;
        Matcher m;
        if (SECONDS.matcher(s).matches()) {
            value = Long.parseLong(s);
        } else if ((m = MM_SS.matcher(s)).matches()) {
            value = Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2));
        } else if ((m = H_MM_SS.matcher(s)).matches()) {
            value = Long.parseLong(m.group(1)) * 3600 + Long.parseLong(m.group(2)) * 60 + Long.parseLong(m.group(3));
        } else {
            throw new IllegalArgumentException(BAD_START);
        }
        if (value > MAX_START_SECONDS) {
            throw new IllegalArgumentException(BAD_START);
        }
        return (int) value;
    }

    private static URI parse(String s) {
        String withScheme = s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") ? s : "https://" + s;
        try {
            return URI.create(withScheme);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(BAD_URL, e);
        }
    }

    private static String firstSegment(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int slash = p.indexOf('/');
        String seg = slash < 0 ? p : p.substring(0, slash);
        return seg.isEmpty() ? null : seg;
    }

    private static String queryParam(String rawQuery, String key) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
