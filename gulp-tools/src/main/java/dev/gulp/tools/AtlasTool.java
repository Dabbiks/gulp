package dev.gulp.tools;

import dev.gulp.api.graphics.Pixmap;
import dev.gulp.core.asset.AtlasBuilder;
import dev.gulp.core.data.JsonWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Packs every PNG, JPG or WebP under a folder into an atlas: {@code <name>.atlas.json} plus {@code <name>_<page>.png}.
 * Region names are the file paths relative to the folder, without extension.
 */
public final class AtlasTool {

    private AtlasTool() {}

    /**
     * Packs a folder.
     *
     * @param folder the images
     * @param output directory for the atlas
     * @param name base name of the files
     * @param pageSize largest page side
     * @param padding pixels between images
     * @return the number of regions
     * @throws IOException if a file cannot be read or written
     */
    public static int pack(Path folder, Path output, String name, int pageSize, int padding) throws IOException {
        List<AtlasBuilder.Input> inputs = new ArrayList<>();
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = folder.relativize(file).toString().replace(File.separatorChar, '/');
                int dot = relative.lastIndexOf('.');
                String extension = dot < 0 ? "" : relative.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
                    continue;
                }
                BufferedImage image = ImageIO.read(file.toFile());
                if (image == null) {
                    throw new IOException("Cannot read image " + file);
                }
                inputs.add(new AtlasBuilder.Input(relative.substring(0, dot), toPixmap(image)));
            }
        }
        AtlasBuilder.Result atlas = AtlasBuilder.build(inputs, pageSize, padding);
        Files.createDirectories(output);
        for (int i = 0; i < atlas.pages().size(); i++) {
            ImageIO.write(
                    toImage(atlas.pages().get(i)),
                    "png",
                    output.resolve(name + "_" + i + ".png").toFile());
        }
        Files.writeString(
                output.resolve(name + ".atlas.json"),
                JsonWriter.write(atlas.json(name), false),
                StandardCharsets.UTF_8);
        return inputs.size();
    }

    static Pixmap toPixmap(BufferedImage image) {
        Pixmap pixmap = new Pixmap(image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                pixmap.setPixel(x, y, (argb << 8) | (argb >>> 24));
            }
        }
        return pixmap;
    }

    static BufferedImage toImage(Pixmap pixmap) {
        BufferedImage image = new BufferedImage(pixmap.width(), pixmap.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixmap.height(); y++) {
            for (int x = 0; x < pixmap.width(); x++) {
                int rgba = pixmap.getPixel(x, y);
                image.setRGB(x, y, (rgba >>> 8) | (rgba << 24));
            }
        }
        return image;
    }
}
