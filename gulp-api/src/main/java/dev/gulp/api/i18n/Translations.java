package dev.gulp.api.i18n;

import dev.gulp.api.scheduler.Promise;
import java.util.List;

/**
 * Translated strings from {@code assets/<namespace>/lang/<locale>.json} (flat keys or nested objects). A key is looked
 * up in the current locale ({@code pl_pl}), then its language ({@code pl}), then the game's default locale, and
 * finally shown as itself with a warning. Arguments replace {@code {0}}, {@code {1}}, and so on; full formatting with
 * plurals arrives in stage 10.
 *
 * <pre>{@code
 * // assets/coins/lang/pl_pl.json: {"hud": {"coins": "Monety: {0}"}}
 * String label = tr("hud.coins", 10); // "Monety: 10"
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
     * Translates a key.
     *
     * @param key the key, for example {@code menu.play}
     * @param arguments values for {@code {0}}, {@code {1}}, and so on
     * @return the translation, or the key if none exists
     */
    String tr(String key, Object... arguments);

    /**
     * Returns whether a key has a translation in the current chain of locales.
     *
     * @param key the key
     * @return {@code true} if translated
     */
    boolean has(String key);

    /**
     * Switches the language; loads its files and then fires {@link LocaleChangeEvent}.
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
