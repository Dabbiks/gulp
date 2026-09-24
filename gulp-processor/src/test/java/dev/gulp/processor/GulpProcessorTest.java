package dev.gulp.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GulpProcessorTest {

    @TempDir
    Path output;

    private record Result(boolean success, List<String> errors, Path output) {
        String generated(String relativePath) throws IOException {
            return Files.readString(output.resolve(relativePath), StandardCharsets.UTF_8);
        }
    }

    private Result compile(Map<String, String> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            files.add(
                    new SimpleJavaFileObject(
                            URI.create("string:///" + source.getKey().replace('.', '/') + ".java"),
                            JavaFileObject.Kind.SOURCE) {
                        @Override
                        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                            return source.getValue();
                        }
                    });
        }
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    GulpProcessor.class.getName(),
                    "-d",
                    output.toString(),
                    "-s",
                    output.toString(),
                    "-Xlint:all,-processing",
                    "-Werror");
            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, files)
                    .call();
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR || diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                    errors.add(diagnostic.getMessage(null));
                }
            }
            return new Result(success, errors, output);
        }
    }

    @Test
    void generatesDispatchersDescriptorsCodecsAndIndex() throws IOException {
        Result result = compile(Map.of("demo.CombatModule", """
                package demo;

                import dev.gulp.api.event.*;
                import dev.gulp.api.event.lifecycle.TickEndEvent;
                import dev.gulp.api.module.*;

                @ModuleInfo(id = "combat", dependsOn = {"world"}, enabledByDefault = false)
                final class CombatModule extends GameModule {
                    final class DamageListener implements Listener {
                        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
                        void onTick(TickEndEvent event) {}
                    }
                }
                """, "demo.Loot", """
                package demo;

                import dev.gulp.api.data.Serializable;
                import java.util.*;

                @Serializable
                record Loot(String name, int count, Map<String, List<Long>> odds, Kind kind, @org.jspecify.annotations.Nullable Loot inner) {}
                """, "demo.Kind", """
                package demo;

                enum Kind { COMMON, RARE }
                """));

        assertThat(result.errors()).isEmpty();
        assertThat(result.success()).isTrue();
        String handlers = result.generated("demo/CombatModule$DamageListener$Handlers.java");
        assertThat(handlers)
                .contains("implements dev.gulp.api.spi.ListenerHandlers<demo.CombatModule.DamageListener>")
                .contains("dev.gulp.api.event.EventPriority.HIGH, true, event -> listener.onTick(event)");
        assertThat(result.generated("demo/Loot$Codec.java"))
                .contains("Codec.mapOf(dev.gulp.api.data.Codec.listOf(dev.gulp.api.data.Codec.LONG))")
                .contains("Codec.enumOf(demo.Kind.values())")
                .contains("Codec.lazy(() -> dev.gulp.api.data.Codec.of(demo.Loot.class)).nullable()");
        String services = result.generated("META-INF/services/dev.gulp.api.spi.GeneratedIndex");
        assertThat(services.trim()).startsWith("demo.GulpIndex_");
        String index = result.generated(services.trim().replace('.', '/') + ".java");
        assertThat(index)
                .contains("new dev.gulp.api.spi.ModuleDescriptor(\"combat\", java.util.List.of(\"world\"),"
                        + " java.util.List.of(), false)")
                .contains("sink.listener(demo.CombatModule.DamageListener.class")
                .contains("sink.codec(demo.Loot.class");
    }

    @Test
    void writesIndexForModuleWithoutListeners() throws IOException {
        // A lone @ModuleInfo generates no other source, so javac starts no second round for the index to wait for.
        Result result = compile(Map.of("solo.LevelModule", """
                package solo;

                import dev.gulp.api.module.*;

                @ModuleInfo(id = "level")
                final class LevelModule extends GameModule {}
                """));

        assertThat(result.errors()).isEmpty();
        assertThat(result.success()).isTrue();
        String services = result.generated("META-INF/services/dev.gulp.api.spi.GeneratedIndex");
        assertThat(result.generated(services.trim().replace('.', '/') + ".java"))
                .contains("sink.module(solo.LevelModule.class");
    }

    @Test
    void rejectsInvalidListeners() throws IOException {
        Result result = compile(Map.of("demo.Bad", """
                package demo;

                import dev.gulp.api.event.*;
                import dev.gulp.api.event.lifecycle.TickEndEvent;

                class NotAListener {
                    @EventHandler void onTick(TickEndEvent event) {}
                }

                class Wrong implements Listener {
                    @EventHandler private void hidden(TickEndEvent event) {}
                    @EventHandler static void shared(TickEndEvent event) {}
                    @EventHandler void two(TickEndEvent a, TickEndEvent b) {}
                    @EventHandler void notAnEvent(String text) {}
                }

                class Outer {
                    private static class Secret implements Listener {
                        @EventHandler void onTick(TickEndEvent event) {}
                    }
                }
                """));

        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .anyMatch(e -> e.contains("must implement dev.gulp.api.event.Listener"))
                .anyMatch(e -> e.contains("neither private nor static"))
                .anyMatch(e -> e.contains("exactly one parameter"))
                .anyMatch(e -> e.contains("must be a subclass of dev.gulp.api.event.Event"))
                .anyMatch(e -> e.contains("must not be private"));
    }

    @Test
    void rejectsInvalidModules() throws IOException {
        Result result = compile(Map.of("demo.Modules", """
                package demo;

                import dev.gulp.api.module.*;

                @ModuleInfo(id = "Bad Id") final class BadId extends GameModule {}
                @ModuleInfo(id = "self", dependsOn = "self") final class Self extends GameModule {}
                @ModuleInfo(id = "dep", softDependsOn = "Not Valid") final class BadDependency extends GameModule {}
                @ModuleInfo(id = "twice") final class First extends GameModule {}
                @ModuleInfo(id = "twice") final class Second extends GameModule {}
                @ModuleInfo(id = "abstract") abstract class Abstract extends GameModule {}
                @ModuleInfo(id = "plain") final class NotAModule {}
                @ModuleInfo(id = "a", dependsOn = "b") final class A extends GameModule {}
                @ModuleInfo(id = "b", softDependsOn = "c") final class B extends GameModule {}
                @ModuleInfo(id = "c", dependsOn = "a") final class C extends GameModule {}
                """));

        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .anyMatch(e -> e.contains("Invalid module id 'Bad Id'"))
                .anyMatch(e -> e.contains("cannot depend on itself"))
                .anyMatch(e -> e.contains("Invalid dependency id 'Not Valid'"))
                .anyMatch(e -> e.contains("Duplicate module id 'twice'"))
                .anyMatch(e -> e.contains("must not be abstract"))
                .anyMatch(e -> e.contains("subclasses of dev.gulp.api.module.GameModule"))
                .anyMatch(e -> e.contains("Module dependency cycle: a -> b -> c -> a"));
    }

    @Test
    void rejectsUnsupportedSerializableTypes() throws IOException {
        Result result = compile(Map.of("demo.Data", """
                package demo;

                import dev.gulp.api.data.Serializable;
                import java.util.*;

                @Serializable final class NotARecord {}
                @Serializable record Generic<T>(T value) {}
                @Serializable record BadMap(Map<Integer, String> map) {}
                @Serializable record BadType(Object anything, char letter) {}
                @Serializable record Plain(Other other) {}
                record Other(int x) {}
                class Outer { @Serializable private record Hidden(int x) {} }
                """));

        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .anyMatch(e -> e.contains("supported on records only"))
                .anyMatch(e -> e.contains("must not be generic"))
                .anyMatch(e -> e.contains("must have String keys"))
                .anyMatch(e -> e.contains("Unsupported type in @Serializable record: java.lang.Object"))
                .anyMatch(e -> e.contains("Unsupported type in @Serializable record: char"))
                .anyMatch(e -> e.contains("Unsupported type in @Serializable record: demo.Other"))
                .anyMatch(e -> e.contains("must not be private"));
    }

    @Test
    void moduleIdsAndCycles() {
        assertThat(ModuleIds.isValid("combat.v2-x_1")).isTrue();
        assertThat(ModuleIds.isValid("")).isFalse();
        assertThat(ModuleIds.isValid("Upper")).isFalse();
        assertThat(ModuleIds.findCycle(Map.of("a", List.of("b"), "b", List.of("missing"))))
                .isNull();
        assertThat(ModuleIds.findCycle(Map.of("a", List.of("a")))).containsExactly("a", "a");
    }
}
