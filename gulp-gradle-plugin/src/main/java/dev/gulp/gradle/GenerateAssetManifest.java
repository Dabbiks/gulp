package dev.gulp.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes {@code assets/assets.manifest.json}: every file under the {@code assets} folders of the game's resources,
 * with its size. Browsers cannot list directories, so the engine reads the manifest to resolve asset folders.
 *
 * <pre>{@code
 * {"files":[{"path":"coins/sprites/player.png","size":1234}]}
 * }</pre>
 */
@CacheableTask
public abstract class GenerateAssetManifest extends DefaultTask {

    /** Name of the manifest inside the assets folder. */
    public static final String MANIFEST = "assets.manifest.json";

    /** Creates the task; instantiated by Gradle. */
    public GenerateAssetManifest() {}

    /**
     * Returns the resource directories to scan; only their {@code assets} subfolders count.
     *
     * @return the directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceDirectories();

    /**
     * Returns libraries whose {@code assets} are listed too, such as the engine's built-in font.
     *
     * @return jars and folders
     */
    @org.gradle.api.tasks.Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Returns the directory that receives {@code assets/assets.manifest.json}.
     *
     * @return the output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Scans the assets and writes the manifest.
     *
     * @throws IOException if the manifest cannot be written
     */
    @TaskAction
    public void generate() throws IOException {
        List<String> entries = new ArrayList<>();
        for (File root : getResourceDirectories().getFiles()) {
            File assets = new File(root, "assets");
            if (!assets.isDirectory()) {
                continue;
            }
            try (Stream<Path> files = Files.walk(assets.toPath())) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String path = assets.toPath().relativize(file).toString().replace(File.separatorChar, '/');
                    if (!path.equals(MANIFEST)) {
                        entries.add("{\"path\":\"" + escape(path) + "\",\"size\":" + Files.size(file) + "}");
                    }
                }
            }
        }
        for (File library : getClasspath().getFiles()) {
            if (library.isFile() && library.getName().endsWith(".jar")) {
                try (java.util.zip.ZipFile jar = new java.util.zip.ZipFile(library)) {
                    for (java.util.zip.ZipEntry entry : java.util.Collections.list(jar.entries())) {
                        String name = entry.getName();
                        if (!entry.isDirectory() && name.startsWith("assets/") && !name.endsWith(MANIFEST)) {
                            entries.add("{\"path\":\"" + escape(name.substring(7)) + "\",\"size\":" + entry.getSize()
                                    + "}");
                        }
                    }
                }
            } else if (library.isDirectory() && new File(library, "assets").isDirectory()) {
                Path assets = new File(library, "assets").toPath();
                try (Stream<Path> files = Files.walk(assets)) {
                    for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                        String path = assets.relativize(file).toString().replace(File.separatorChar, '/');
                        if (!path.equals(MANIFEST)) {
                            entries.add("{\"path\":\"" + escape(path) + "\",\"size\":" + Files.size(file) + "}");
                        }
                    }
                }
            }
        }
        entries = new ArrayList<>(new java.util.TreeSet<>(entries));
        entries.sort(null);
        File output = new File(getOutputDirectory().get().getAsFile(), "assets/" + MANIFEST);
        Files.createDirectories(output.getParentFile().toPath());
        Files.writeString(output.toPath(), "{\"files\":[" + String.join(",", entries) + "]}\n", StandardCharsets.UTF_8);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
