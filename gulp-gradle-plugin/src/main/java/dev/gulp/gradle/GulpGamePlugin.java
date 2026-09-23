package dev.gulp.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.teavm.gradle.api.TeaVMExtension;

/**
 * Plugin {@code dev.gulp.game}: runs and packages a Gulp game.
 *
 * <ul>
 *   <li>{@code runDesktop} starts the game in a window;
 *   <li>{@code buildWeb} compiles it with TeaVM (Wasm GC plus a JavaScript fallback) into {@code build/web}, with the
 *       HTML page, the loading screen, the assets and their manifest;
 *   <li>{@code runWeb} serves {@code build/web} on {@code localhost:8080}; with {@code --continuous} Gradle rebuilds
 *       on every change and the page reloads itself;
 *   <li>{@code packageWeb} zips {@code build/web} for itch.io and similar hosts.
 * </ul>
 *
 * <pre>{@code
 * plugins { id("dev.gulp.game") version "0.1.0" }
 * dependencies { implementation("dev.gulp:gulp-api:0.1.0") }
 * gulp { mainClass = "com.example.coins.CoinGame"; title = "Coin Hunter" }
 * }</pre>
 *
 * <p>The desktop and web backends of the plugin's own version are added automatically; declare {@code desktopRuntime}
 * or {@code webRuntime} dependencies to use others.
 */
public final class GulpGamePlugin implements Plugin<Project> {

    /** Task group of the plugin's tasks. */
    public static final String GROUP = "gulp";

    /** Base name of the compiled web files. */
    static final String WEB_NAME = "game";

    private static final String ENTRY_PACKAGE = "dev.gulp.generated";
    private static final String ENTRY_CLASS = "GulpWebMain";
    private static final String TEMPLATE = "dev/gulp/backend/web/template/";

    /** Creates the plugin; instantiated by Gradle. */
    public GulpGamePlugin() {}

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply("org.teavm");
        GulpExtension gulp = project.getExtensions().create("gulp", GulpExtension.class);
        gulp.getTitle().convention(project.getName());
        String version = frameworkVersion();

        // TeaVM adds its annotation processor to every source set; games do not write TeaVM extensions.
        project.getConfigurations().named("annotationProcessor", c -> c.exclude(excludeTeaVm()));
        project.getConfigurations().named("teavmAnnotationProcessor", c -> c.exclude(excludeTeaVm()));

        Configuration desktopRuntime = project.getConfigurations().create("desktopRuntime", c -> {
            c.setDescription("Desktop backend for runDesktop.");
            c.defaultDependencies(
                    d -> d.add(project.getDependencies().create("dev.gulp:gulp-backend-desktop:" + version)));
        });
        Configuration webRuntime = project.getConfigurations().create("webRuntime", c -> {
            c.setDescription("Web backend compiled by TeaVM.");
            c.defaultDependencies(d -> d.add(project.getDependencies().create("dev.gulp:gulp-backend-web:" + version)));
        });
        project.getConfigurations().named("teavmImplementation", c -> c.extendsFrom(webRuntime));

        SourceSet main = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet teavmSources = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName("teavm");

        // Asset manifest, on the classpath next to the assets so desktop and web read it the same way.
        TaskProvider<GenerateAssetManifest> manifest = project.getTasks()
                .register("generateAssetManifest", GenerateAssetManifest.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Lists the game's assets in assets/assets.manifest.json.");
                    task.getResourceDirectories().from(project.file("src/main/resources"));
                    task.getOutputDirectory()
                            .set(project.getLayout().getBuildDirectory().dir("generated/gulp/manifest"));
                });
        main.getResources().srcDir(manifest.flatMap(GenerateAssetManifest::getOutputDirectory));

        // Web entry point: TeaVM needs a static main that creates the game.
        Provider<Directory> entryDir = project.getLayout().getBuildDirectory().dir("generated/gulp/web-entry");
        TaskProvider<?> entry = project.getTasks().register("generateWebEntry", task -> {
            task.setGroup(GROUP);
            task.setDescription("Generates the web entry point that launches gulp.mainClass.");
            Provider<String> mainClass = gulp.getMainClass();
            task.getInputs().property("mainClass", mainClass);
            task.getOutputs().dir(entryDir);
            task.doLast(t -> writeEntry(entryDir.get(), mainClass.get()));
        });
        teavmSources.getJava().srcDir(entry.map(t -> entryDir.get()));

        TeaVMExtension teavm = project.getExtensions().getByType(TeaVMExtension.class);
        teavm.getAll().getMainClass().set(ENTRY_PACKAGE + "." + ENTRY_CLASS);
        teavm.getJs().getAddedToWebApp().set(false);
        teavm.getJs().getTargetFileName().set(WEB_NAME + ".js");
        teavm.getWasmGC().getAddedToWebApp().set(false);
        teavm.getWasmGC().getTargetFileName().set(WEB_NAME + ".wasm");

        project.getTasks().register("runDesktop", JavaExec.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Runs the game in a desktop window.");
            task.setClasspath(main.getRuntimeClasspath().plus(desktopRuntime));
            task.getMainClass().set(gulp.getMainClass());
            task.setWorkingDir(project.getProjectDir());
            task.jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");
            // -Pgulp.exitAfterFrames=120 closes the window by itself (CI smoke tests).
            String exitAfter = project.getProviders()
                    .gradleProperty("gulp.exitAfterFrames")
                    .getOrNull();
            if (exitAfter != null) {
                task.systemProperty("gulp.desktop.exitAfterFrames", exitAfter);
            }
            if (System.getProperty("os.name", "").startsWith("Mac")) {
                task.jvmArgs("-XstartOnFirstThread");
            }
        });

        Provider<Directory> webDir = project.getLayout().getBuildDirectory().dir("web");
        Provider<Boolean> wasm = gulp.getWeb().getTarget().map(t -> t == WebTarget.WASM_GC);
        Provider<Boolean> js =
                gulp.getWeb().getTarget().zip(gulp.getWeb().getJsFallback(), (t, f) -> t == WebTarget.JS || f);
        Provider<String> targets = wasm.zip(js, (w, j) -> (w ? "wasm" : "") + (w && j ? "," : "") + (j ? "js" : ""));
        Provider<String> title = gulp.getTitle();

        TaskProvider<Sync> buildWeb = project.getTasks().register("buildWeb", Sync.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Builds the game for browsers into build/web.");
            task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            task.into(webDir);
            // Outputs by directory and tasks by name: task objects cannot be stored in the configuration cache.
            task.dependsOn(wasm.map(w -> w ? List.of("generateWasmGC", "copyWasmGCRuntime") : List.of()));
            task.dependsOn(js.map(j -> j ? List.of("generateJavaScript") : List.of()));
            task.from(teavm.getWasmGC().getOutputDir().dir("wasm-gc"), spec -> {
                spec.include(WEB_NAME + ".*");
                spec.exclude(wasm.get() ? List.of() : List.of("**"));
            });
            task.from(teavm.getJs().getOutputDir().dir("js"), spec -> {
                spec.include(WEB_NAME + ".*");
                spec.exclude(js.get() ? List.of() : List.of("**"));
            });
            task.from(main.getOutput().getResourcesDir(), spec -> spec.include("assets/**"));
            task.dependsOn(project.getTasks().named(main.getProcessResourcesTaskName()));
            task.from(project.provider(() -> templates(project, webRuntime)), spec -> {
                spec.include(TEMPLATE + "**");
                spec.eachFile(file -> file.setRelativePath(new RelativePath(true, file.getName())));
                spec.setIncludeEmptyDirs(false);
                spec.filesMatching(
                        "**/index.html",
                        file -> file.filter(line -> line.replace("{{title}}", escapeHtml(title.get()))
                                .replace("{{name}}", WEB_NAME)
                                .replace("{{targets}}", targets.get())));
            });
            task.getInputs().property("title", title);
            task.getInputs().property("targets", targets);
        });

        project.getTasks().register("runWeb", task -> {
            task.setGroup(GROUP);
            task.setDescription("Serves build/web on localhost; use --continuous to rebuild and reload on changes.");
            task.dependsOn(buildWeb);
            Provider<Integer> port = gulp.getWeb().getPort();
            boolean continuous = project.getGradle().getStartParameter().isContinuous();
            task.doLast(t -> {
                java.io.File root = webDir.get().getAsFile();
                String url = "http://localhost:" + port.get() + "/";
                try {
                    boolean started = WebDevServer.serve(port.get(), root.toPath());
                    t.getLogger()
                            .lifecycle((started ? "Serving " : "Still serving ") + WebDevServer.describe(root) + " at "
                                    + url);
                } catch (IOException e) {
                    throw new GradleException(
                            "Cannot start the web server on port " + port.get() + ": " + e.getMessage(), e);
                }
                if (!continuous) {
                    t.getLogger()
                            .lifecycle("Press Ctrl+C to stop. Run with --continuous to rebuild and reload on changes.");
                    waitForever();
                }
            });
        });

        project.getTasks().register("stopWeb", task -> {
            task.setGroup(GROUP);
            task.setDescription("Stops the runWeb server.");
            Provider<Integer> port = gulp.getWeb().getPort();
            task.doLast(
                    t -> t.getLogger().lifecycle(WebDevServer.stop(port.get()) ? "Stopped" : "No server was running"));
        });

        project.getTasks().register("packageWeb", Zip.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Zips build/web for upload to itch.io and similar hosts.");
            task.from(buildWeb);
            task.getArchiveFileName().set(project.getName() + "-web.zip");
            task.getDestinationDirectory()
                    .set(project.getLayout().getBuildDirectory().dir("distributions"));
        });
    }

    /** Script the runWeb server adds to the page: reloads it when a rebuild changes the served files. */
    static final String DEV_RELOAD = """
            <script>
            (function () {
              let last = null;
              async function poll() {
                try {
                  const response = await fetch("__gulp/stamp", { cache: "no-store" });
                  if (!response.ok) return;
                  const stamp = await response.text();
                  if (last !== null && stamp !== last) location.reload();
                  last = stamp;
                } catch (e) { /* server restarting */ }
                setTimeout(poll, 1000);
              }
              poll();
            })();
            </script>""";

    private static java.util.Map<String, String> excludeTeaVm() {
        return java.util.Map.of("group", "org.teavm");
    }

    private static List<FileTree> templates(Project project, Configuration webRuntime) {
        List<FileTree> trees = new ArrayList<>();
        for (java.io.File file : webRuntime.getFiles()) {
            if (file.getName().endsWith(".jar")) {
                trees.add(project.zipTree(file));
            } else if (file.isDirectory()) {
                trees.add(project.fileTree(file));
            }
        }
        return trees;
    }

    private static void writeEntry(Directory directory, String mainClass) {
        String source = "package " + ENTRY_PACKAGE + ";\n\n"
                + "/** Generated by the Gulp Gradle plugin: the web entry point. */\n"
                + "public final class " + ENTRY_CLASS + " {\n"
                + "    private " + ENTRY_CLASS + "() {}\n\n"
                + "    /**\n     * Starts the game.\n     *\n     * @param args ignored\n     */\n"
                + "    public static void main(String[] args) {\n"
                + "        dev.gulp.api.Gulp.launch(new " + mainClass + "());\n"
                + "    }\n"
                + "}\n";
        java.io.File file = directory
                .file(ENTRY_PACKAGE.replace('.', '/') + "/" + ENTRY_CLASS + ".java")
                .getAsFile();
        try {
            java.nio.file.Files.createDirectories(file.getParentFile().toPath());
            java.nio.file.Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void waitForever() {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String frameworkVersion() {
        try (InputStream in = GulpGamePlugin.class.getResourceAsStream("version.txt")) {
            return new String(Objects.requireNonNull(in, "version.txt").readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
