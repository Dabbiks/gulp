package dev.gulp.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * The {@code gulp { ... }} block of a game build.
 *
 * <pre>{@code
 * gulp {
 *     mainClass = "com.example.coins.CoinGame"
 *     title = "Coin Hunter"
 *     web {
 *         target = WebTarget.WASM_GC
 *         jsFallback = true
 *     }
 * }
 * }</pre>
 */
public abstract class GulpExtension {

    private final Web web;
    private final Desktop desktop;
    private final GulpAssets assets = new GulpAssets();

    /**
     * Creates the extension; instantiated by Gradle.
     *
     * @param objects creates nested objects
     */
    @javax.inject.Inject
    public GulpExtension(ObjectFactory objects) {
        this.web = objects.newInstance(Web.class);
        web.getTarget().convention(WebTarget.WASM_GC);
        web.getJsFallback().convention(true);
        web.getPort().convention(8080);
        this.desktop = objects.newInstance(Desktop.class);
    }

    /**
     * Returns the fully qualified name of the game's {@code Game} subclass. It needs a public no-argument constructor
     * for the web entry point.
     *
     * @return the main class property
     */
    public abstract Property<String> getMainClass();

    /**
     * Returns the game title used for windows, web pages and installers.
     *
     * @return the title property
     */
    public abstract Property<String> getTitle();

    /**
     * Returns the package of the generated {@code GameAssets} class.
     *
     * @return the package, by default the package of {@link #getMainClass()}
     */
    public abstract Property<String> getAssetKeysPackage();

    /**
     * Returns the asset build settings.
     *
     * @return the assets block
     */
    public GulpAssets getAssets() {
        return assets;
    }

    /**
     * Configures the asset build.
     *
     * @param action the configuration
     */
    public void assets(Action<? super GulpAssets> action) {
        action.execute(assets);
    }

    /**
     * Returns the web settings.
     *
     * @return the web settings
     */
    public Web getWeb() {
        return web;
    }

    /**
     * Configures the web settings.
     *
     * @param action the configuration
     */
    public void web(Action<? super Web> action) {
        action.execute(web);
    }

    /** Web build settings. */
    public abstract static class Web {

        /** Creates the settings; instantiated by Gradle. */
        public Web() {}

        /**
         * Returns the main output: Wasm GC (default) or JavaScript.
         *
         * @return the target property
         */
        public abstract Property<WebTarget> getTarget();

        /**
         * Returns whether a JavaScript build is added for browsers without Wasm GC; the page picks one automatically.
         *
         * @return {@code true} by default
         */
        public abstract Property<Boolean> getJsFallback();

        /**
         * Returns the port of the {@code runWeb} server.
         *
         * @return {@code 8080} by default
         */
        public abstract Property<Integer> getPort();
    }

    /**
     * Returns the desktop packaging settings.
     *
     * @return the settings
     */
    public Desktop getDesktop() {
        return desktop;
    }

    /**
     * Configures desktop packaging.
     *
     * <pre>{@code
     * gulp { desktop { installerType = "msi"; vendor = "Example Games"; icon = file("icon.ico") } }
     * }</pre>
     *
     * @param action the configuration
     */
    public void desktop(Action<? super Desktop> action) {
        action.execute(desktop);
    }

    /** Settings of {@code packageDesktop}. */
    public abstract static class Desktop {

        /** Creates the settings; instantiated by Gradle. */
        public Desktop() {}

        /**
         * Returns the {@code jpackage} type; by default MSI with WiX on Windows, DMG on macOS, DEB or RPM on Linux, or
         * a zipped application folder when those tools are missing.
         *
         * @return {@code app-image}, {@code msi}, {@code exe}, {@code dmg}, {@code pkg}, {@code deb} or {@code rpm}
         */
        public abstract Property<String> getInstallerType();

        /**
         * Returns the application version; by default the project version reduced to numbers.
         *
         * @return such as {@code 1.2.0}
         */
        public abstract Property<String> getAppVersion();

        /**
         * Returns the vendor shown by installers.
         *
         * @return the vendor
         */
        public abstract Property<String> getVendor();

        /**
         * Returns the icon: {@code .ico} on Windows, {@code .icns} on macOS, {@code .png} on Linux.
         *
         * @return the file
         */
        public abstract org.gradle.api.file.RegularFileProperty getIcon();

        /**
         * Returns extra JVM options of the packaged game, such as {@code -Xmx1g}.
         *
         * @return the options
         */
        public abstract org.gradle.api.provider.ListProperty<String> getJavaOptions();
    }
}
