package dev.gulp.core.i18n;

/**
 * CLDR plural categories ({@code zero}, {@code one}, {@code two}, {@code few}, {@code many}, {@code other}) for common
 * languages, written from the published rules.
 *
 * <pre>{@code
 * PluralRules.category("pl_pl", 5);  // "many"
 * PluralRules.category("pl_pl", 22); // "few"
 * }</pre>
 */
public final class PluralRules {

    private PluralRules() {}

    /**
     * Returns the plural category of a number in a language.
     *
     * @param locale the locale, such as {@code pl_pl}
     * @param number the number
     * @return the category name
     */
    public static String category(String locale, double number) {
        boolean integer = number == Math.rint(number) && !Double.isInfinite(number);
        long n = (long) Math.abs(number);
        long mod10 = n % 10;
        long mod100 = n % 100;
        return switch (MessageFormat.language(locale)) {
            case "ja", "zh", "ko", "vi", "th", "id" -> "other";
            case "pl" -> {
                if (!integer) {
                    yield "other";
                }
                if (n == 1) {
                    yield "one";
                }
                if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
                    yield "few";
                }
                yield "many";
            }
            case "ru", "uk", "be" -> {
                if (!integer) {
                    yield "other";
                }
                if (mod10 == 1 && mod100 != 11) {
                    yield "one";
                }
                if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
                    yield "few";
                }
                yield "many";
            }
            case "cs", "sk" -> {
                if (!integer) {
                    yield "many";
                }
                if (n == 1) {
                    yield "one";
                }
                yield n >= 2 && n <= 4 ? "few" : "other";
            }
            case "fr" -> Math.abs(number) < 2 ? "one" : "other";
            default -> integer && n == 1 ? "one" : "other";
        };
    }
}
