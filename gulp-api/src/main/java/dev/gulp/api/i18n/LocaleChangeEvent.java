package dev.gulp.api.i18n;

import dev.gulp.api.event.Event;

/**
 * Fired after the language changed; texts built with {@code Text.translatable} follow on the next frame.
 *
 * <pre>{@code
 * on(LocaleChangeEvent.class, e -> logger().info("Language: " + e.locale()));
 * }</pre>
 */
public final class LocaleChangeEvent extends Event {

    private final String locale;

    /**
     * Creates the event; fired by the engine.
     *
     * @param locale the new locale
     */
    public LocaleChangeEvent(String locale) {
        this.locale = locale;
    }

    /**
     * Returns the new locale.
     *
     * @return the locale, for example {@code pl_pl}
     */
    public String locale() {
        return locale;
    }
}
