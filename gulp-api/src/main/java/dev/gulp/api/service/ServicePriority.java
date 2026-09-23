package dev.gulp.api.service;

/**
 * Priority of a service provider; {@code Services.get} returns the highest one.
 *
 * <pre>{@code
 * services().register(Leaderboard.class, new SteamLeaderboard(), this, ServicePriority.HIGH);
 * }</pre>
 */
public enum ServicePriority {
    /** Fallback provider. */
    LOWEST,
    /** Below normal. */
    LOW,
    /** The usual choice. */
    NORMAL,
    /** Preferred over normal providers. */
    HIGH,
    /** Always preferred. */
    HIGHEST
}
