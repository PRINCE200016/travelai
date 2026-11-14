package com.tripmind.controller;

import java.util.Map;
import java.util.regex.Pattern;

/** Small input helpers shared by the public form endpoints. */
final class Validate {
    private Validate() {}

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,24}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()-]{7,20}$");

    static String str(Map<String, Object> body, String key, int max) {
        Object v = body.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    static Integer integer(Map<String, Object> body, String key, int min, int max) {
        Object v = body.get(key);
        if (v == null) return null;
        try {
            long n = v instanceof Number num ? num.longValue() : Long.parseLong(String.valueOf(v).replaceAll("[^0-9-]", ""));
            return (int) Math.max(min, Math.min(max, n));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean email(String s) { return s != null && EMAIL.matcher(s).matches(); }
    static boolean phone(String s) { return s == null || PHONE.matcher(s).matches(); }

    static Map<String, String> error(String message) { return Map.of("error", message); }
}
