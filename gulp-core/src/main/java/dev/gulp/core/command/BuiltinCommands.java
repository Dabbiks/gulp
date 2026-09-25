package dev.gulp.core.command;

import dev.gulp.api.Owner;
import dev.gulp.api.command.ArgumentType;
import dev.gulp.api.command.Arguments;
import dev.gulp.api.command.Command;
import dev.gulp.api.command.CommandException;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.world.World;
import dev.gulp.core.GulpEngine;
import dev.gulp.core.data.ConfigImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Commands every game has: {@code /help}, {@code /tps}, {@code /modules}, {@code /module enable|disable <id>},
 * {@code /reload config|assets}, {@code /timescale <x>}, {@code /spawn <type> [x y]} and {@code /tp <x> <y>}.
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
                        .subcommand(Command.builder("assets")
                                .executes(ctx -> {
                                    ctx.reply("Reloading loaded assets");
                                    engine.assets()
                                            .reloadAll()
                                            .thenSync(done -> ctx.reply("Assets reloaded"))
                                            .onFailure(error -> ctx.reply("Reload failed: " + error.getMessage()));
                                })
                                .build())
                        .build());

        commands.register(
                owner,
                Command.builder("spawn")
                        .description("Spawns an entity in the active world, at the camera or at x y")
                        .argument(Arguments.entityType("type"))
                        .argument(Arguments.floating("x").optional())
                        .argument(Arguments.floating("y").optional())
                        .executes(ctx -> {
                            World world = activeWorld(engine);
                            EntityType type = ctx.arg("type");
                            Vec2 at = ctx.has("x") && ctx.has("y")
                                    ? new Vec2(ctx.<Float>arg("x"), ctx.<Float>arg("y"))
                                    : world.camera().position();
                            Entity entity = world.spawn(type, at.x(), at.y());
                            ctx.reply("Spawned " + type.key() + " #" + entity.runtimeId() + " at " + format(at.x())
                                    + " " + format(at.y()));
                        })
                        .build());

        commands.register(
                owner,
                Command.builder("tp")
                        .description("Teleports the player (the entity tagged 'player'), or the camera if none")
                        .argument(Arguments.floating("x"))
                        .argument(Arguments.floating("y"))
                        .executes(ctx -> {
                            World world = activeWorld(engine);
                            float x = ctx.arg("x");
                            float y = ctx.arg("y");
                            Entity player = world.query().tag("player").first();
                            if (player != null) {
                                player.teleport(x, y);
                                ctx.reply("Teleported " + player + " to " + format(x) + " " + format(y));
                            } else {
                                world.camera().follow(null);
                                world.camera().setPosition(new Vec2(x, y));
                                ctx.reply("Moved the camera to " + format(x) + " " + format(y));
                            }
                        })
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

    private static World activeWorld(GulpEngine engine) {
        World world = engine.worlds().active();
        if (world == null) {
            throw new CommandException("No active world");
        }
        return world;
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
