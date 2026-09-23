package dev.gulp.core.command;

import dev.gulp.api.Owner;
import dev.gulp.api.command.ArgumentType;
import dev.gulp.api.command.Arguments;
import dev.gulp.api.command.Command;
import dev.gulp.api.command.CommandException;
import dev.gulp.core.GulpEngine;
import dev.gulp.core.data.ConfigImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Commands every game has: {@code /help}, {@code /tps}, {@code /modules}, {@code /module enable|disable <id>},
 * {@code /reload config} and {@code /timescale <x>}.
 */
public final class BuiltinCommands {

    private BuiltinCommands() {}

    /**
     * Registers the built-in commands.
     *
     * @param engine the engine they control
     * @param commands the registry
     * @param owner the engine owner
     */
    public static void register(GulpEngine engine, CommandsImpl commands, Owner owner) {
        commands.register(
                owner,
                Command.builder("help")
                        .aliases("?")
                        .description("Lists commands or shows the usage of one")
                        .argument(Arguments.string("command").optional())
                        .executes(ctx -> {
                            String name = ctx.argOrNull("command");
                            if (name != null) {
                                Command command = commands.get(name.startsWith("/") ? name.substring(1) : name);
                                if (command == null) {
                                    throw new CommandException("Unknown command /" + name);
                                }
                                ctx.reply(command.usage() + describe(command));
                                return;
                            }
                            for (Command command : commands.all()) {
                                ctx.reply(command.usage() + describe(command));
                            }
                        })
                        .build());

        commands.register(
                owner,
                Command.builder("tps")
                        .description("Shows ticks per second and time settings")
                        .executes(ctx -> ctx.reply("TPS " + format(engine.tps()) + " (target " + engine.targetTps()
                                + "), tick " + engine.tick() + ", time scale " + format(engine.timeScale())
                                + (engine.isPaused() ? ", paused" : "")))
                        .build());

        commands.register(
                owner,
                Command.builder("modules")
                        .description("Lists modules and their states")
                        .executes(ctx -> {
                            List<String> ids = engine.modules().ids();
                            if (ids.isEmpty()) {
                                ctx.reply("No modules");
                                return;
                            }
                            List<String> parts = new ArrayList<>();
                            for (String id : ids) {
                                parts.add(id + " [" + engine.modules().state(id) + "]");
                            }
                            ctx.reply("Modules: " + String.join(", ", parts));
                        })
                        .build());

        ArgumentType<String> moduleId = new ArgumentType<>() {
            @Override
            public String parse(String token) {
                if (!engine.modules().ids().contains(token)) {
                    throw new CommandException("No module '" + token + "'");
                }
                return token;
            }

            @Override
            public List<String> suggest(String partial) {
                List<String> matches = new ArrayList<>();
                for (String id : engine.modules().ids()) {
                    if (id.startsWith(partial)) {
                        matches.add(id);
                    }
                }
                return matches;
            }

            @Override
            public String description() {
                return "module id";
            }
        };
        commands.register(
                owner,
                Command.builder("module")
                        .description("Enables or disables a module while the game runs")
                        .subcommand(Command.builder("enable")
                                .argument(Arguments.of("id", moduleId))
                                .executes(ctx -> {
                                    String id = ctx.arg("id");
                                    ctx.reply(
                                            engine.modules().enable(id)
                                                    ? "Enabled " + id
                                                    : "Could not enable " + id + " ("
                                                            + engine.modules().state(id) + ")");
                                })
                                .build())
                        .subcommand(Command.builder("disable")
                                .argument(Arguments.of("id", moduleId))
                                .executes(ctx -> {
                                    String id = ctx.arg("id");
                                    engine.modules().disable(id);
                                    ctx.reply("Disabled " + id);
                                })
                                .build())
                        .build());

        commands.register(
                owner,
                Command.builder("reload")
                        .description("Reloads configuration files")
                        .subcommand(Command.builder("config")
                                .executes(ctx -> {
                                    List<ConfigImpl> configs = engine.allConfigs();
                                    for (ConfigImpl config : configs) {
                                        config.reload();
                                    }
                                    ctx.reply("Reloading " + configs.size() + " config file(s)");
                                })
                                .build())
                        .build());

        commands.register(
                owner,
                Command.builder("timescale")
                        .description("Sets the speed of game time (1 = normal)")
                        .argument(Arguments.floating("scale", 0f, 10f))
                        .executes(ctx -> {
                            float scale = ctx.arg("scale");
                            engine.setTimeScale(scale);
                            ctx.reply("Time scale set to " + format(scale));
                        })
                        .build());
    }

    private static String describe(Command command) {
        return command.description().isEmpty() ? "" : " - " + command.description();
    }

    private static String format(float value) {
        float rounded = Math.round(value * 10f) / 10f;
        return rounded == (long) rounded
                ? Long.toString((long) rounded)
                : Float.toString(rounded).toLowerCase(Locale.ROOT);
    }
}
