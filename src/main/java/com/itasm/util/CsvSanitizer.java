package com.itasm.util;

/**
 * Utility for sanitizing CSV cell values to prevent CSV formula injection (DDE attacks).
 * Prepends a single quote (') if untrusted text starts with =, +, -, @, \t, or \r.
 */
public final class CsvSanitizer {

    private CsvSanitizer() {
    }

    public static String sanitize(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof java.time.temporal.TemporalAccessor || value instanceof Boolean) {
            return value.toString();
        }
        String str = value.toString();
        if (str.isEmpty()) {
            return "";
        }
        char rawFirst = str.charAt(0);
        if (rawFirst == '=' || rawFirst == '+' || rawFirst == '-' || rawFirst == '@' || rawFirst == '\t' || rawFirst == '\r') {
            return "'" + str;
        }
        String leadingTrimmed = str.stripLeading();
        if (!leadingTrimmed.isEmpty()) {
            char first = leadingTrimmed.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                return "'" + str;
            }
        }
        return str;
    }
}
