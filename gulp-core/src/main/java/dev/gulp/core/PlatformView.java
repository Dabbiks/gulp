package dev.gulp.core;

import dev.gulp.api.Platform;
import dev.gulp.platform.PlatformBackend;

/** Read-only {@link Platform} view of a backend. */
final class PlatformView implements Platform {

    private final PlatformBackend backend;

    PlatformView(PlatformBackend backend) {
        this.backend = backend;
    }

    @Override
    public String backend() {
        return backend.name();
    }

    @Override
    public String osName() {
        return backend.info().osName();
    }

    @Override
    public String architecture() {
        return backend.info().architecture();
    }

    @Override
    public boolean isWeb() {
        return backend.info().isWeb();
    }

    @Override
    public boolean isDevelopment() {
        return backend.info().isDevelopment();
    }

    @Override
    public String systemLocale() {
        return backend.info().systemLocale();
    }
}
