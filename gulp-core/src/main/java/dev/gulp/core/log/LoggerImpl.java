package dev.gulp.core.log;

import dev.gulp.api.LogLevel;
import dev.gulp.api.Logger;
import dev.gulp.platform.PlatformLog;
import org.jspecify.annotations.Nullable;

/** {@link Logger} writing to a {@link PlatformLog} above a minimum level. Thread-safe if the sink is. */
public final class LoggerImpl implements Logger {

    private final String name;
    private final PlatformLog sink;
    private final LogLevel minimum;

    /**
     * Creates a logger.
     *
     * @param name the logger name
     * @param sink where lines go
     * @param minimum the lowest level written
     */
    public LoggerImpl(String name, PlatformLog sink, LogLevel minimum) {
        this.name = name;
        this.sink = sink;
        this.minimum = minimum;
    }

    /**
     * Returns the minimum level from the system property {@code gulp.logLevel}, or {@code INFO}.
     *
     * @return the configured level
     */
    public static LogLevel configuredLevel() {
        String configured = System.getProperty("gulp.logLevel");
        if (configured != null) {
            for (LogLevel level : LogLevel.values()) {
                if (level.name().equalsIgnoreCase(configured.trim())) {
                    return level;
                }
            }
        }
        return LogLevel.INFO;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return level.compareTo(minimum) >= 0;
    }

    @Override
    public void log(LogLevel level, String message, @Nullable Throwable error) {
        if (isEnabled(level)) {
            sink.write(level.ordinal(), name, message, error);
        }
    }
}
