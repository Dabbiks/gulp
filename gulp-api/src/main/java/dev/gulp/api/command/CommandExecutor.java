package dev.gulp.api.command;

/**
 * What a command does when run.
 *
 * <pre>{@code
 * CommandExecutor heal = ctx -> {
 *     int amount = ctx.arg("amount");
 *     player.heal(amount);
 *     ctx.reply("Healed " + amount);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface CommandExecutor {

    /**
     * Runs the command on the main thread.
     *
     * @param context parsed arguments and a way to reply
     * @throws CommandException to report a user error; the message is shown without a stack trace
     */
    void execute(CommandContext context);
}
