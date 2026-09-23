package dev.gulp.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Bundles the folders in {@code resourcepacks/} of the game project for the web, which cannot list files: every
 * {@code resourcepacks/<id>/assets/...} is copied to {@code resourcepacks/<id>/...} and listed in
 * {@code resourcepacks/index.json} with the first line of its {@code pack.txt}.
 *
 * <pre>{@code
 * {"packs":[{"id":"hd","description":"HD textures","files":["coins/textures/logo.png"]}]}
 * }</pre>
 */
@CacheableTask
public abstract class BundleResourcePacks extends DefaultTask {

    /** Creates the task; instantiated by Gradle. */
    public BundleResourcePacks() {}

    /**
     * Returns the {@code resourcepacks} folder of the project, if any.
     *
     * @return the folder
     */
    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getPacksDirectory();

    /**
     * Returns the folder receiving {@code resourcepacks/}.
     *
     * @return the output folder
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Copies the packs and writes the index.
     *
     * @throws IOException if files cannot be copied
     */
    @TaskAction
    public void bundle() throws IOException {
        Path output = getOutputDirectory().get().getAsFile().toPath().resolve("resourcepacks");
        if (Files.exists(output)) {
            try (Stream<Path> files = Files.walk(output)) {
                for (Path file :
                        files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        Files.createDirectories(output);
        List<String> packs = new ArrayList<>();
        if (getPacksDirectory().isPresent()
                && getPacksDirectory().get().getAsFile().isDirectory()) {
            Path root = getPacksDirectory().get().getAsFile().toPath();
            try (Stream<Path> entries = Files.list(root)) {
                for (Path pack : entries.filter(Files::isDirectory).sorted().toList()) {
                    packs.add(copy(pack, output.resolve(pack.getFileName().toString())));
                }
            }
        }
        Files.writeString(
                output.resolve("index.json"),
                "{\"packs\":[" + String.join(",", packs) + "]}\n",
                StandardCharsets.UTF_8);
    }

    private static String copy(Path pack, Path target) throws IOException {
        List<String> files = new ArrayList<>();
        Path assets = pack.resolve("assets");
        if (Files.isDirectory(assets)) {
            try (Stream<Path> walk = Files.walk(assets)) {
                for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                    String path = assets.relativize(file).toString().replace(File.separatorChar, '/');
                    Path destination = target.resolve(path);
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                    files.add("\"" + escape(path) + "\"");
                }
            }
        }
        Path description = pack.resolve("pack.txt");
        String text = "";
        if (Files.isRegularFile(description)) {
            text = Files.readString(description).lines().findFirst().orElse("").strip();
        }
        return "{\"id\":\"" + escape(pack.getFileName().toString()) + "\",\"description\":\"" + escape(text)
                + "\",\"files\":[" + String.join(",", files) + "]}";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
