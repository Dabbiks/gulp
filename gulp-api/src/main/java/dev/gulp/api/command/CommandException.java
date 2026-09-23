package dev.gulp.api.command;

/**
 * A user error in a command (bad argument, wrong state). Its message is shown to the user as the reply, without a
 * stack trace.
 *
 * <pre>{@code
 * if (!world.exists(name)) throw new CommandException("No world named " + name);
 * }</pre>
 */
public final class CommandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message shown to the user
     */
    public CommandException(String message) {
        super(message);
    }
}
