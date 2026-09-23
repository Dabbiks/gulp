package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.command.Arguments;
import dev.gulp.api.command.Command;
import dev.gulp.api.command.CommandException;
import dev.gulp.api.command.Commands;
import dev.gulp.api.command.Console;
import dev.gulp.api.registry.Key;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.BaseModule;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CommandsTest {

    enum Difficulty {
        EASY,
        HARD
    }

    private HeadlessRunner runner;
    private final List<String> output = new ArrayList<>();

    @AfterEach
    void stop() {
        if (runner != null) {
            runner.stop();
        }
    }

    private Commands commands(TestGame game) {
        runner = started(game);
        return runner.engine().commands();
    }

    private boolean run(Commands commands, String line) {
        return commands.dispatch(line, output::add);
    }

    @Test
    void parsesTypedArgumentsAndOptionalOnes() {
        TestGame game = new TestGame();
        Commands commands = commands(game);
        commands.register(
                game,
                Command.builder("give")
                        .aliases("g")
                        .description("Gives items")
                        .argument(Arguments.key("item"))
                        .argument(Arguments.integer("count", 1, 64).optional())
                        .executes(ctx -> {
                            Key item = ctx.arg("item");
                            int count = ctx.has("count") ? ctx.arg("count") : 1;
                            Integer maybe = ctx.argOrNull("count");
                            ctx.reply(count + " x " + item + " (" + maybe + ") via "
                                    + ctx.command().name() + ": " + ctx.input());
                        })
                        .build());

        assertThat(run(commands, "/give test:sword 3")).isTrue();
        assertThat(run(commands, "G   test:axe")).isTrue();
        assertThat(output)
                .containsExactly(
                        "3 x test:sword (3) via give: give test:sword 3", "1 x test:axe (null) via give: G   test:axe");
        assertThat(commands.get("g")).isSameAs(commands.get("GIVE"));
    }

    @Test
    void reportsParseErrorsWithUsage() {
        TestGame game = new TestGame();
        Commands commands = commands(game);
        commands.register(
                game,
                Command.builder("tp")
                        .argument(Arguments.floating("x", -100f, 100f))
                        .argument(Arguments.floating("y"))
                        .executes(ctx -> ctx.reply("ok"))
                        .build());

        assertThat(run(commands, "/tp 1")).isFalse();
        assertThat(run(commands, "/tp 1 abc")).isFalse();
        assertThat(run(commands, "/tp 500 1")).isFalse();
        assertThat(run(commands, "/tp 1 2 3")).isFalse();
        assertThat(run(commands, "/nope")).isFalse();
        assertThat(run(commands, "   ")).isFalse();

        assertThat(output)
                .containsExactly(
                        "Missing <y>. Usage: /tp <x> <y>",
                        "Invalid <y>: 'abc' is not a number",
                        "Invalid <x>: 500 is outside -100.0..100.0",
                        "Too many arguments. Usage: /tp <x> <y>",
                        "Unknown command /nope. Type /help for a list.");
    }

    @Test
    void subcommandsGreedyEnumsAndChoices() {
        TestGame game = new TestGame();
        Commands commands = commands(game);
        commands.register(
                game,
                Command.builder("set")
                        .subcommand(Command.builder("difficulty")
                                .argument(Arguments.enumOf("level", Difficulty.values()))
                                .executes(ctx -> ctx.reply("difficulty " + ctx.<Difficulty>arg("level")))
                                .build())
                        .subcommand(Command.builder("mode")
                                .argument(Arguments.choice("mode", "day", "night"))
                                .executes(ctx -> ctx.reply("mode " + ctx.arg("mode")))
                                .build())
                        .subcommand(Command.builder("motd")
                                .argument(Arguments.greedy("text"))
                                .executes(ctx -> ctx.reply("motd '" + ctx.arg("text") + "'"))
                                .build())
                        .build());
        commands.register(
                game,
                Command.builder("status")
                        .subcommand(Command.builder("full")
                                .executes(ctx -> ctx.reply("full"))
                                .build())
                        .executes(ctx -> ctx.reply("short"))
                        .build());

        run(commands, "/set difficulty hard");
        run(commands, "/set mode night");
        run(commands, "/set motd  Hello,   world ");
        run(commands, "/set difficulty impossible");
        run(commands, "/set mode dusk");
        run(commands, "/set colour red");
        run(commands, "/set");
        run(commands, "/status");
        run(commands, "/status full");

        assertThat(output)
                .containsExactly(
                        "difficulty HARD",
                        "mode night",
                        "motd 'Hello,   world '",
                        "Invalid <level>: Expected one of easy, hard, got 'impossible'",
                        "Invalid <mode>: Expected one of day, night, got 'dusk'",
                        "Unknown subcommand 'colour'. Usage: /set difficulty|mode|motd",
                        "Usage: /set difficulty|mode|motd",
                        "short",
                        "full");
    }

    @Test
    void executorErrorsAreReportedNotThrown() {
        TestGame game = new TestGame();
        Commands commands = commands(game);
        game.command("fail", ctx -> {
            throw new CommandException("You cannot do that");
        });
        game.command("crash", ctx -> {
            throw new IllegalStateException("bug");
        });
        game.command("echo", ctx -> ctx.reply("[" + ctx.arg("args") + "]"));

        assertThat(run(commands, "/fail")).isFalse();
        assertThat(run(commands, "/crash")).isFalse();
        assertThat(run(commands, "/echo a b")).isTrue();
        assertThat(run(commands, "/echo")).isFalse();

        assertThat(output.get(0)).isEqualTo("You cannot do that");
        assertThat(output.get(1)).contains("internal error");
        assertThat(output.get(2)).isEqualTo("[a b]");
        assertThat(output.get(3)).as("a bug in the executor, not a user error").contains("internal error");
    }

    @Test
    void registrationConflictsAndUnregistering() {
        TestGame game = new TestGame();
        Commands commands = commands(game);
        commands.register(
                game, Command.builder("dup").aliases("d").executes(ctx -> {}).build());

        assertThatThrownBy(() -> commands.register(
                        game, Command.builder("d").executes(ctx -> {}).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/d is already registered by 'test'");
        assertThat(commands.unregister("D")).isTrue();
        assertThat(commands.unregister("dup")).isFalse();
        assertThat(commands.get("d")).isNull();
    }

    @Test
    void completesNamesSubcommandsAndArguments() {
        BaseModule base = new BaseModule();
        TestGame game = new TestGame().modules(base);
        Commands commands = commands(game);

        assertThat(commands.complete("/mo")).containsExactly("/module", "/modules");
        assertThat(commands.complete("/module ")).containsExactly("/module enable", "/module disable");
        assertThat(commands.complete("/module d")).containsExactly("/module disable");
        assertThat(commands.complete("/module disable b")).containsExactly("/module disable base");
        assertThat(commands.complete("/timescale 1 ")).isEmpty();
        assertThat(commands.complete("/nothing ")).isEmpty();
        assertThat(commands.complete("/module nothing x")).isEmpty();
        assertThat(commands.complete("")).contains("/help", "/tps");
    }

    @Test
    void builtInCommandsControlTheEngine() {
        BaseModule base = new BaseModule();
        TestGame game = new TestGame().modules(base);
        Commands commands = commands(game);
        GulpEngine engine = runner.engine();

        run(commands, "/timescale 0.5");
        assertThat(engine.timeScale()).isEqualTo(0.5f);
        run(commands, "/tps");
        run(commands, "/modules");
        run(commands, "/module disable base");
        assertThat(engine.modules().isEnabled("base")).isFalse();
        run(commands, "/module enable base");
        assertThat(engine.modules().isEnabled("base")).isTrue();
        run(commands, "/module enable nope");
        run(commands, "/help tps");
        run(commands, "/help nope");
        run(commands, "/reload config");
        engine.pause();
        run(commands, "/tps");

        assertThat(output)
                .containsSequence(
                        "Time scale set to 0.5",
                        "TPS 60 (target 60), tick 0, time scale 0.5",
                        "Modules: base [ENABLED]",
                        "Disabled base",
                        "Enabled base",
                        "Invalid <id>: No module 'nope'",
                        "/tps - Shows ticks per second and time settings",
                        "Unknown command /nope",
                        "Reloading 2 config file(s)",
                        "TPS 60 (target 60), tick 0, time scale 0.5, paused");

        output.clear();
        run(commands, "/help");
        assertThat(output).anyMatch(line -> line.startsWith("/module enable|disable"));
    }

    @Test
    void consoleRunsTerminalInputAndKeepsHistory() {
        TestGame game = new TestGame();
        runner = started(game);
        Console console = runner.engine().commands().console();

        runner.backend().console().type("/tps");
        runner.backend().console().type("modules");
        runner.step(1);
        console.submit("   ");

        assertThat(console.isAvailable()).isTrue();
        assertThat(console.history()).containsExactly("/tps", "modules");
        assertThat(console.output()).contains("> /tps", "No modules");
        assertThat(runner.backend().console().printed()).contains("> modules", "No modules");
        assertThat(console.complete("/ti")).containsExactly("/timescale");

        for (int i = 0; i < 300; i++) {
            console.submit("/tps");
        }
        assertThat(console.history()).hasSize(100);
        assertThat(console.output()).hasSize(500);
    }

    @Test
    void consoleCanBeDisabled() {
        TestGame game = new TestGame();
        game.configure = s -> s.developerConsole(false);
        runner = started(game);
        Console console = runner.engine().commands().console();

        runner.backend().console().type("/tps");
        runner.step(1);
        console.submit("/tps");

        assertThat(console.isAvailable()).isFalse();
        assertThat(console.history()).isEmpty();
    }
}
