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
}
