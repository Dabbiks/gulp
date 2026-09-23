package dev.gulp.api.text;

/**
 * Animated effects of rich text, from markup tags such as {@code [wave]} or {@link Text#effect(TextEffect)}.
 *
 * <pre>{@code
 * Text.of("Nowy rekord!").effect(TextEffect.RAINBOW);
 * }</pre>
 */
public enum TextEffect {
    /** Characters bob up and down in a wave. */
    WAVE,
    /** Characters jitter. */
    SHAKE,
    /** Colors cycle through the hues. */
    RAINBOW,
    /** Characters grow and shrink. */
    PULSE,
    /** Characters fade in and out. */
    FADE
}
