package dev.gulp.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/** {@code checkApiUsage} and the helpers of {@code packageDesktop}. */
class PackagingTest {

    /** Compiles sources in memory and returns class files by internal name. */
    private static Map<String, byte[]> compile(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager standard = compiler.getStandardFileManager(null, null, null);
        Map<String, ByteArrayOutputStream> outputs = new HashMap<>();
        var manager = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(
                    Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + ".class"), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        outputs.put(className.replace('.', '/'), out);
                        return out;
                    }
                };
            }
        };
        List<JavaFileObject> units = sources.entrySet().stream()
                .map(e -> (JavaFileObject)
                        new SimpleJavaFileObject(
                                URI.create("string:///" + e.getKey().replace('.', '/') + ".java"),
                                JavaFileObject.Kind.SOURCE) {
                            @Override
                            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                                return e.getValue();
                            }
                        })
                .toList();
        assertThat(compiler.getTask(null, manager, null, List.of("-proc:none"), null, units)
                        .call())
                .isTrue();
        Map<String, byte[]> classes = new HashMap<>();
        outputs.forEach((name, out) -> classes.put(name, out.toByteArray()));
        return classes;
    }

    @Test
    void engineInternalsAreFoundButApiAndOwnClassesAreNot() {
        Map<String, byte[]> classes = compile(Map.of(
                "dev.gulp.core.Internal",
                "package dev.gulp.core; public class Internal { public static int x; }",
                "dev.gulp.api.Engine",
                "package dev.gulp.api; public interface Engine {}",
                "dev.gulp.examples.demo.Helper",
                "package dev.gulp.examples.demo; public class Helper {}",
                "dev.gulp.examples.demo.Game",
                """
                package dev.gulp.examples.demo;
                public class Game {
                    dev.gulp.api.Engine engine;
                    Helper helper;
                    String note = "dev/gulp/core/Mentioned";
                    int peek() { return dev.gulp.core.Internal.x; }
                    static class Nested {}
                }
                """));
        Set<String> own = Set.of(
                "dev/gulp/examples/demo/Game", "dev/gulp/examples/demo/Helper", "dev/gulp/examples/demo/Game$Nested");
        byte[] game = classes.get("dev/gulp/examples/demo/Game");
        assertThat(CheckApiUsage.className(game)).isEqualTo("dev/gulp/examples/demo/Game");
        assertThat(CheckApiUsage.forbidden(game, own)).containsExactly("dev/gulp/core/Internal");
        assertThat(CheckApiUsage.forbidden(classes.get("dev/gulp/examples/demo/Helper"), own))
                .isEmpty();
        assertThat(CheckApiUsage.utf8Constants(game)).doesNotContain("dev/gulp/core/Mentioned");
    }

    @Test
    void packagingKeepsTheNativesOfTheSystemAndNumericVersions() {
        assertThat(PackageDesktop.keepNatives("lwjgl-3.3.6.jar", "windows")).isTrue();
        assertThat(PackageDesktop.keepNatives("lwjgl-3.3.6-natives-windows.jar", "windows"))
                .isTrue();
        assertThat(PackageDesktop.keepNatives("lwjgl-3.3.6-natives-windows-arm64.jar", "windows"))
                .isTrue();
        assertThat(PackageDesktop.keepNatives("lwjgl-3.3.6-natives-macos-arm64.jar", "windows"))
                .isFalse();
        assertThat(PackageDesktop.keepNatives("lwjgl-3.3.6-natives-linux.jar", "linux"))
                .isTrue();

        assertThat(PackageDesktop.appVersion("0.1.0-SNAPSHOT")).isEqualTo("1.1.0");
        assertThat(PackageDesktop.appVersion("2.4")).isEqualTo("2.4.0");
        assertThat(PackageDesktop.appVersion("3.2.1.7")).isEqualTo("3.2.1");
        assertThat(PackageDesktop.appVersion("unspecified")).isEqualTo("1.0.0");

        assertThat(PackageDesktop.defaultType("windows", name -> false)).isEqualTo("app-image");
        assertThat(PackageDesktop.defaultType("windows", name -> name.equals("wix")))
                .isEqualTo("msi");
        assertThat(PackageDesktop.defaultType("macos", name -> false)).isEqualTo("dmg");
        assertThat(PackageDesktop.defaultType("linux", name -> name.equals("dpkg-deb")))
                .isEqualTo("deb");
        assertThat(PackageDesktop.defaultType("linux", name -> name.equals("rpmbuild")))
                .isEqualTo("rpm");
        assertThat(PackageDesktop.defaultType("linux", name -> false)).isEqualTo("app-image");
        assertThat(PackageDesktop.currentOs()).isIn("windows", "macos", "linux");
        assertThat(PackageDesktop.onPath("surely-not-a-program-" + System.nanoTime()))
                .isFalse();

        assertThat(PackageDesktop.parseModules("Warning: something\njava.base,java.net.http,jdk.unsupported\n"))
                .containsExactly("java.base", "java.net.http", "jdk.unsupported");
        assertThat(PackageDesktop.parseModules("Error: not a module list")).isEmpty();
    }
}
