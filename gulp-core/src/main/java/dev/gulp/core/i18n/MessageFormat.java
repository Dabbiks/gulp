package dev.gulp.core.i18n;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A subset of ICU message format: positional and named arguments, {@code number} and {@code date} formats, {@code
 * plural} with CLDR plural categories and {@code select}. Written by hand because TeaVM has no {@code
 * java.text.MessageFormat}; unknown or broken patterns come out as written instead of failing.
 *
 * <pre>{@code
 * MessageFormat.format("{0, plural, one {# moneta} few {# monety} many {# monet} other {# monety}}", "pl", 5);
 * }</pre>
 */
public final class MessageFormat {

    private MessageFormat() {}

    /**
     * Formats a pattern.
     *
     * @param pattern the pattern
     * @param locale the locale, such as {@code pl_pl}
     * @param arguments positional arguments, or one {@code Map} of named ones
     * @return the text
     */
    public static String format(String pattern, String locale, Object... arguments) {
        if (pattern.indexOf('{') < 0) {
            return pattern;
        }
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        append(out, pattern, 0, pattern.length(), locale, arguments, null);
        return out.toString();
    }

    private static void append(
            StringBuilder out,
            String pattern,
            int from,
            int to,
            String locale,
            Object[] arguments,
            @Nullable String number) {
        int i = from;
        while (i < to) {
            char c = pattern.charAt(i);
            if (c == '#' && number != null) {
                out.append(number);
                i++;
                continue;
            }
            if (c != '{') {
                out.append(c);
                i++;
                continue;
            }
            int close = matching(pattern, i, to);
            if (close < 0) {
                out.append(pattern, i, to);
                return;
            }
            if (!placeholder(out, pattern.substring(i + 1, close), locale, arguments)) {
                out.append(pattern, i, close + 1);
            }
            i = close + 1;
        }
    }

    /** Index of the brace closing the one at {@code open}, or -1. */
    private static int matching(String pattern, int open, int to) {
        int depth = 0;
        for (int i = open; i < to; i++) {
            char c = pattern.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean placeholder(StringBuilder out, String inside, String locale, Object[] arguments) {
        int comma = inside.indexOf(',');
        String name = (comma < 0 ? inside : inside.substring(0, comma)).trim();
        Object value = argument(name, arguments);
        if (value == MISSING) {
            return false;
        }
        if (comma < 0) {
            out.append(
                    value instanceof Number n && !(value instanceof Integer || value instanceof Long)
                            ? formatNumber(n.doubleValue(), locale)
                            : String.valueOf(value));
            return true;
        }
        String rest = inside.substring(comma + 1);
        int second = rest.indexOf(',');
        String kind = (second < 0 ? rest : rest.substring(0, second)).trim();
        String options = second < 0 ? "" : rest.substring(second + 1);
        switch (kind) {
            case "number" -> {
                out.append(value instanceof Number n ? formatNumber(n.doubleValue(), locale) : String.valueOf(value));
                return true;
            }
            case "date" -> {
                out.append(value instanceof Number n ? formatDate(n.longValue(), locale) : String.valueOf(value));
                return true;
            }
            case "plural" -> {
                if (!(value instanceof Number n)) {
                    return false;
                }
                double amount = n.doubleValue();
                String selector = "=" + (amount == Math.rint(amount) ? Long.toString((long) amount) : amount);
                String chosen = option(options, selector);
                if (chosen == null) {
                    chosen = option(options, PluralRules.category(locale, amount));
                }
                if (chosen == null) {
                    chosen = option(options, "other");
                }
                if (chosen == null) {
                    return false;
                }
                append(out, chosen, 0, chosen.length(), locale, arguments, formatNumber(amount, locale));
                return true;
            }
            case "select" -> {
                String chosen = option(options, String.valueOf(value));
                if (chosen == null) {
                    chosen = option(options, "other");
                }
                if (chosen == null) {
                    return false;
                }
                append(out, chosen, 0, chosen.length(), locale, arguments, null);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static final Object MISSING = new Object();

    private static Object argument(String name, Object[] arguments) {
        if (arguments.length == 1 && arguments[0] instanceof Map<?, ?> named) {
            Object value = named.get(name);
            if (!named.containsKey(name)) {
                return MISSING;
            }
            return value == null ? "null" : value;
        }
        if (name.isEmpty() || name.length() > 3) {
            return MISSING;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return MISSING;
            }
        }
        int index = Integer.parseInt(name);
        return index < arguments.length ? arguments[index] : MISSING;
    }

    /** The text of option {@code selector} in {@code a {..} b {..}}, or {@code null}. */
    private static @Nullable String option(String options, String selector) {
        int i = 0;
        int length = options.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            int nameStart = i;
            while (i < length && options.charAt(i) != '{' && !Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            String name = options.substring(nameStart, i);
            while (i < length && options.charAt(i) != '{') {
                i++;
            }
            if (i >= length) {
                return null;
            }
            int close = matching(options, i, length);
            if (close < 0) {
                return null;
            }
            if (name.equals(selector)) {
                return options.substring(i + 1, close);
            }
            i = close + 1;
        }
        return null;
    }

    // ------------------------------------------------------------------ numbers and dates

    /**
     * Formats a number with the separators of a language; up to two decimals, trailing zeros dropped.
     *
     * @param value the number
     * @param locale the locale
     * @return the text
     */
    public static String formatNumber(double value, String locale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        String language = language(locale);
        char decimal;
        String group;
        int minimumGrouping = 1;
        switch (language) {
            case "pl", "cs", "sk", "ru", "uk", "fr", "sv", "nb", "fi" -> {
                decimal = ',';
                group = "\u00a0"; // no-break space, as in CLDR
                minimumGrouping = language.equals("pl") ? 2 : 1;
            }
            case "de", "es", "it", "pt", "nl", "da", "tr", "id" -> {
                decimal = ',';
                group = ".";
                minimumGrouping = language.equals("es") ? 2 : 1;
            }
            default -> {
                decimal = '.';
                group = ",";
            }
        }
        boolean negative = value < 0;
        long hundredths = Math.round(Math.abs(value) * 100.0);
        long whole = hundredths / 100;
        long fraction = hundredths % 100;
        String digits = Long.toString(whole);
        StringBuilder out = new StringBuilder();
        if (negative && hundredths != 0) {
            out.append('-');
        }
        boolean grouped = digits.length() > 3 && digits.length() >= 4 + (minimumGrouping - 1);
        if (grouped) {
            int first = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
            out.append(digits, 0, first);
            for (int i = first; i < digits.length(); i += 3) {
                out.append(group).append(digits, i, i + 3);
            }
        } else {
            out.append(digits);
        }
        if (fraction != 0) {
            out.append(decimal).append(fraction / 10);
            if (fraction % 10 != 0) {
                out.append(fraction % 10);
            }
        }
        return out.toString();
    }

    /**
     * Formats a date (UTC) in the usual order of a language.
     *
     * @param epochMillis milliseconds since 1970
     * @param locale the locale
     * @return the text
     */
    public static String formatDate(long epochMillis, String locale) {
        long days = Math.floorDiv(epochMillis, 86_400_000L);
        // Civil date from days since 1970 (Howard Hinnant's algorithm).
        long z = days + 719_468;
        long era = Math.floorDiv(z, 146_097);
        long doe = z - era * 146_097;
        long yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int month = (int) (mp < 10 ? mp + 3 : mp - 9);
        long year = yoe + era * 400 + (month <= 2 ? 1 : 0);
        String language = language(locale);
        return switch (language) {
            case "pl", "de", "ru", "uk", "cs", "sk", "fi", "nb", "da", "tr" -> two(day) + "." + two(month) + "." + year;
            case "fr", "es", "it", "pt", "nl" -> two(day) + "/" + two(month) + "/" + year;
            case "en" ->
                locale.equals("en_us") || locale.equals("en")
                        ? month + "/" + day + "/" + year
                        : two(day) + "/" + two(month) + "/" + year;
            default -> year + "-" + two(month) + "-" + two(day);
        };
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    static String language(String locale) {
        int underscore = locale.indexOf('_');
        return underscore < 0 ? locale : locale.substring(0, underscore);
    }
}
