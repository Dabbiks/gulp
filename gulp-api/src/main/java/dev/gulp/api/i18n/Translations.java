package dev.gulp.api.i18n;

import dev.gulp.api.scheduler.Promise;
import java.util.List;

/**
 * Translated strings from {@code assets/<namespace>/lang/<locale>.json} (flat keys or nested objects). A key is looked
 * up in the current locale ({@code pl_pl}), then its language ({@code pl}), then the game's default locale, and
 * finally shown as itself with a warning in the log. The chosen locale is kept in preferences; at first start it
 * comes from {@code GameSettings.locale} or the system.
 *
 * <p>Translations use a subset of ICU message format:
 * <ul>
 *   <li>{@code {0}}, {@code {1}}: positional arguments; {@code {name}}: a named argument when the only argument is a
 *       {@code Map};
 *   <li>{@code {0, number}}: a number with the language's grouping and decimal separators; {@code {0, date}}: a date
 *       from milliseconds since 1970 in the language's usual order;
 *   <li>{@code {n, plural, =0 {none} one {# coin} few {# coins} many {# coins} other {# coins}}}: plural forms with
 *       built-in rules for English, Polish, Czech, Slovak, Russian, Ukrainian, German, French, Spanish, Italian,
 *       Portuguese, Dutch, Swedish, Japanese, Chinese and Korean; {@code #} is the formatted number;
 *   <li>{@code {g, select, a {...} other {...}}}: a choice by the argument's text.
 * </ul>
 * {@code Text.translatable} keys are formatted when drawn, so UI labels change language by themselves.
 *
 * <pre>{@code
 * // assets/coins/lang/pl_pl.json:
 * // {"hud": {"coins": "{0, plural, one {# moneta} few {# monety} many {# monet} other {# monety}}"},
 * //  "greet": "Cześć, {name}!"}
 * tr("hud.coins", 5);                        // "5 monet"
 * tr("greet", Map.of("name", "Ania"));      // "Cześć, Ania!"
 * translations().setLocale("en_us");
 * }</pre>
 */
public interface Translations {

    /**
     * Returns the current locale.
     *
     * @return a lower-case locale such as {@code pl_pl}
     */
    String locale();

    /**
     * Translates and formats a key.
     *
     * @param key the key, for example {@code menu.play}
     * @param arguments values for {@code {0}}, {@code {1}} and so on, or one {@code Map} for named arguments
     * @return the translation, or the key if none exists
     */
    String tr(String key, Object... arguments);

    /**
     * Formats a message pattern directly, without looking up a key.
     *
     * @param pattern a pattern in the format above
     * @param arguments the arguments
     * @return the formatted text
     */
    String format(String pattern, Object... arguments);

    /**
     * Formats a number the way the current language writes it (grouping and decimal separators).
     *
     * @param value the number
     * @return for example {@code "12 345,5"} in Polish, {@code "12,345.5"} in English
     */
    String formatNumber(double value);

    /**
     * Formats a date the way the current language writes it.
     *
     * @param epochMillis milliseconds since 1970 (UTC)
     * @return for example {@code "25.09.2026"} in Polish, {@code "9/25/2026"} in American English
     */
    String formatDate(long epochMillis);

    /**
     * Returns whether a key has a translation in the current chain of locales.
     *
     * @param key the key
     * @return {@code true} if translated
     */
    boolean has(String key);

    /**
     * Switches the language, remembers it in preferences, loads its files and then fires {@link LocaleChangeEvent}.
     *
     * @param locale a locale such as {@code en_us} or {@code pl_pl}
     * @return completes after the switch
     */
    Promise<Void> setLocale(String locale);

    /**
     * Returns the locales the game has files for, from the asset manifest.
     *
     * @return the locales
     */
    List<String> availableLocales();
}
