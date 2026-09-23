package dev.gulp.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command line of the Gulp tools, also run by the Gradle plugin.
 *
 * <pre>
 * msdf   &lt;font.ttf&gt; &lt;outputDir&gt; &lt;name&gt; [emSize=40] [range=4]
 * bitmap &lt;font.ttf&gt; &lt;outputDir&gt; &lt;name&gt; &lt;size&gt; [aa=true]
 * atlas  &lt;imagesDir&gt; &lt;outputDir&gt; &lt;name&gt; [pageSize=2048] [padding=2]
 * </pre>
 */
public final class GulpTools {

    private GulpTools() {}

    /**
     * Runs a command.
     *
     * @param args the command and its arguments
     * @throws Exception if the command fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            usage();
            return;
        }
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(4, args.length));
        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        String name = args[3];
        switch (args[0]) {
            case "msdf" -> {
                int count = FontGenerator.msdf(
                        input,
                        output,
                        name,
                        FontGenerator.codePoints(FontGenerator.DEFAULT_CHARSET),
                        intArg(rest, 0, 40),
                        intArg(rest, 1, 4));
                System.out.println("MSDF font " + name + ": " + count + " glyphs");
            }
            case "bitmap" -> {
                int count = FontGenerator.bitmap(
                        input,
                        output,
                        name,
                        FontGenerator.codePoints(FontGenerator.DEFAULT_CHARSET),
                        intArg(rest, 0, 16),
                        rest.size() < 2 || Boolean.parseBoolean(rest.get(1)));
                System.out.println("Bitmap font " + name + ": " + count + " glyphs");
            }
            case "atlas" -> {
                int count = AtlasTool.pack(input, output, name, intArg(rest, 0, 2048), intArg(rest, 1, 2));
                System.out.println("Atlas " + name + ": " + count + " regions");
            }
            default -> usage();
        }
    }

    private static int intArg(List<String> args, int index, int fallback) {
        return index < args.size() ? Integer.parseInt(args.get(index)) : fallback;
    }

    private static void usage() {
        System.err.println("""
                Usage:
                  msdf   <font.ttf> <outputDir> <name> [emSize=40] [range=4]
                  bitmap <font.ttf> <outputDir> <name> <size> [antialias=true]
                  atlas  <imagesDir> <outputDir> <name> [pageSize=2048] [padding=2]""");
    }
}
