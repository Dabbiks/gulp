package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformInfo;

/**
 * Fixed system information, so tests do not depend on the machine they run on.
 *
 * <pre>{@code
 * assertThat(backend.info().systemLocale()).isEqualTo("en-US");
 * }</pre>
 */
public final class HeadlessInfo implements PlatformInfo {

    private String systemLocale = "en-US";

    HeadlessInfo() {}

    /**
     * Changes the reported system language, for localisation tests.
     *
     * @param systemLocale a BCP 47 tag
     */
    public void setSystemLocale(String systemLocale) {
        this.systemLocale = systemLocale;
    }

    @Override
    public String osName() {
        return "headless";
    }

    @Override
    public String architecture() {
        return "headless";
    }

    @Override
    public boolean isWeb() {
        return false;
    }

    @Override
    public boolean isDevelopment() {
        return true;
    }

    @Override
    public String systemLocale() {
        return systemLocale;
    }

    @Override
    public int screenWidth() {
        return 1920;
    }

    @Override
    public int screenHeight() {
        return 1080;
    }

    @Override
    public String gpuDescription() {
        return "none (headless)";
    }
}
