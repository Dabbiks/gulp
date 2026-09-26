package dev.gulp.core.util;

/**
 * UTC time stamps for file names and reports, without {@code java.time} formatting (heavy under TeaVM).
 *
 * <pre>{@code
 * String file = "crash-reports/crash-" + Timestamps.fileStamp(System.currentTimeMillis()) + ".txt";
 * }</pre>
 */
public final class Timestamps {

    private Timestamps() {}

    /**
     * Formats a time for a file name: {@code 2026-09-25_14-03-07}.
     *
     * @param epochMillis milliseconds since 1970, UTC
     * @return the stamp
     */
    public static String fileStamp(long epochMillis) {
        return date(epochMillis) + "_" + time(epochMillis, '-');
    }

    /**
     * Formats a time for people: {@code 2026-09-25 14:03:07 UTC}.
     *
     * @param epochMillis milliseconds since 1970, UTC
     * @return the text
     */
    public static String readable(long epochMillis) {
        return date(epochMillis) + " " + time(epochMillis, ':') + " UTC";
    }

    /**
     * Formats the date part: {@code 2026-09-25}.
     *
     * @param epochMillis milliseconds since 1970, UTC
     * @return the date
     */
    public static String date(long epochMillis) {
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
        return year + "-" + two(month) + "-" + two(day);
    }

    private static String time(long epochMillis, char separator) {
        long seconds = Math.floorMod(epochMillis, 86_400_000L) / 1000;
        return two((int) (seconds / 3600))
                + separator
                + two((int) (seconds / 60 % 60))
                + separator
                + two((int) (seconds % 60));
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
