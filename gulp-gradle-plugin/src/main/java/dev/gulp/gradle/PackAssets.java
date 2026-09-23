package dev.gulp.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/**
 * Packs every {@code assets/<namespace>/sprites/} folder into {@code assets/<namespace>/sprites.atlas.json} and
 * generates the declared fonts, by running the {@code gulp-tools} command line.
 *
 * <pre>{@code
 * ./gradlew packAssets
 * }</pre>
 */
@CacheableTask
public abstract class PackAssets extends DefaultTask {

    /** Creates the task; instantiated by Gradle. */
    public PackAssets() {}

    /**
     * Returns the resource folders whose {@code assets} subfolders are scanned.
     *
     * @return the folders
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceDirectories();

    /**
     * Returns the font files to convert.
     *
     * @return the source fonts
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getFontSources();

    /**
     * Returns the fonts to generate.
     *
     * @return the font specifications
     */
    @Input
    public abstract ListProperty<GulpAssets.FontSpec> getFonts();

    /**
     * Returns the classpath of {@code gulp-tools}.
     *
     * @return the classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getToolsClasspath();

    /**
     * Returns the folder that receives {@code assets/...}.
     *
     * @return the output folder
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Runs external programs.
     *
     * @return the exec operations
     */
    @Inject
    protected abstract ExecOperations getExec();

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    /**
     * Packs and generates.
     *
     * @throws IOException if folders cannot be listed
     */
    @TaskAction
    public void pack() throws IOException {
        File output = getOutputDirectory().get().getAsFile();
        deleteRecursively(output.toPath());
        List<List<String>> commands = new ArrayList<>();
        for (File root : getResourceDirectories().getFiles()) {
            Path assets = root.toPath().resolve("assets");
            if (!Files.isDirectory(assets)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(assets)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    Path sprites = namespace.resolve("sprites");
                    if (Files.isDirectory(sprites)) {
                        commands.add(List.of(
                                "atlas",
                                sprites.toString(),
                                new File(output, "assets/" + namespace.getFileName()).getPath(),
                                "sprites"));
                    }
                }
            }
        }
        for (GulpAssets.FontSpec font : getFonts().get()) {
            int colon = font.key().indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("Font key '" + font.key() + "' needs a namespace");
            }
            String path = font.key().substring(colon + 1);
            int slash = path.lastIndexOf('/');
            File folder = new File(
                    output,
                    "assets/" + font.key().substring(0, colon) + (slash < 0 ? "" : "/" + path.substring(0, slash)));
            String name = path.substring(slash + 1);
            if (font.kind().equals("msdf")) {
                commands.add(List.of(
                        "msdf", font.source().getPath(), folder.getPath(), name, Integer.toString(font.size()), "8"));
            } else {
                commands.add(List.of(
                        "bitmap",
                        font.source().getPath(),
                        folder.getPath(),
                        name,
                        Integer.toString(font.size()),
                        Boolean.toString(font.antialias())));
            }
        }
        for (List<String> command : commands) {
            getExec().javaexec(spec -> {
                spec.classpath(getToolsClasspath());
                spec.getMainClass().set("dev.gulp.tools.GulpTools");
                spec.jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true");
                spec.args(command);
            });
        }
    }
}
