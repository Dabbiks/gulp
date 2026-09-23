package dev.gulp.api.command;

import dev.gulp.api.registry.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Factories for typed command arguments.
 *
 * <pre>{@code
 * Command.builder("timescale").argument(Arguments.floating("scale", 0f, 10f)).executes(...).build();
 * Command.builder("difficulty").argument(Arguments.enumOf("level", Difficulty.values())).executes(...).build();
 * Command.builder("say").argument(Arguments.greedy("message")).executes(...).build();
 * }</pre>
 */
public final class Arguments {

    private Arguments() {}

    /**
     * An integer.
     *
     * @param name the argument name
     * @return the argument
     */
    public static Argument<Integer> integer(String name) {
        return integer(name, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * An integer in a range.
     *
     * @param name the argument name
     * @param min smallest allowed value
     * @param max largest allowed value
     * @return the argument
     */
    public static Argument<Integer> integer(String name, int min, int max) {
        String description = min == Integer.MIN_VALUE && max == Integer.MAX_VALUE ? "int" : "int " + min + ".." + max;
        return new Argument<>(
                name,
                new SimpleType<>(description) {
                    @Override
                    public Integer parse(String token) {
                        int value;
                        try {
                            value = Integer.parseInt(token);
                        } catch (NumberFormatException e) {
                            throw new CommandException("'" + token + "' is not a whole number");
                        }
                        if (value < min || value > max) {
                            throw new CommandException(value + " is outside " + min + ".." + max);
                        }
                        return value;
                    }
                },
                false);
    }

    /**
     * A decimal number.
     *
     * @param name the argument name
     * @return the argument
     */
    public static Argument<Float> floating(String name) {
        return floating(name, -Float.MAX_VALUE, Float.MAX_VALUE);
    }

    /**
     * A decimal number in a range.
     *
     * @param name the argument name
     * @param min smallest allowed value
     * @param max largest allowed value
     * @return the argument
     */
    public static Argument<Float> floating(String name, float min, float max) {
        String description =
                min == -Float.MAX_VALUE && max == Float.MAX_VALUE ? "number" : "number " + min + ".." + max;
        return new Argument<>(
                name,
                new SimpleType<>(description) {
                    @Override
                    public Float parse(String token) {
                        float value;
                        try {
                            value = Float.parseFloat(token);
                        } catch (NumberFormatException e) {
                            throw new CommandException("'" + token + "' is not a number");
                        }
                        if (!(value >= min && value <= max)) {
                            throw new CommandException(token + " is outside " + min + ".." + max);
                        }
                        return value;
                    }
                },
                false);
    }

    /**
     * A single word.
     *
     * @param name the argument name
     * @return the argument
     */
    public static Argument<String> string(String name) {
        return new Argument<>(
                name,
                new SimpleType<>("word") {
                    @Override
                    public String parse(String token) {
                        return token;
                    }
                },
                false);
    }

    /**
     * The rest of the line, spaces included. Must be the last argument.
     *
     * @param name the argument name
     * @return the argument
     */
    public static Argument<String> greedy(String name) {
        return new Argument<>(
                name,
                new SimpleType<>("text") {
                    @Override
                    public String parse(String token) {
                        return token;
                    }

                    @Override
                    public boolean isGreedy() {
                        return true;
                    }
                },
                false);
    }

    /**
     * One of fixed words.
     *
     * @param name the argument name
     * @param options the allowed words
     * @return the argument
     */
    public static Argument<String> choice(String name, String... options) {
        List<String> allowed = List.of(options);
        return new Argument<>(
                name,
                new SuggestingType<>(String.join("|", allowed), () -> allowed) {
                    @Override
                    public String parse(String token) {
                        if (!allowed.contains(token)) {
                            throw new CommandException(
                                    "Expected one of " + String.join(", ", allowed) + ", got '" + token + "'");
                        }
                        return token;
                    }
                },
                false);
    }

    /**
     * A key {@code namespace:path}.
     *
     * @param name the argument name
     * @return the argument
     */
    public static Argument<Key> key(String name) {
        return key(name, List::of);
    }

    /**
     * A key with completions, for example the keys of a registry.
     *
     * @param name the argument name
     * @param suggestions supplies the keys to suggest
     * @return the argument
     */
    public static Argument<Key> key(String name, Supplier<? extends Collection<Key>> suggestions) {
        return new Argument<>(
                name,
                new SuggestingType<>("key", () -> {
                    List<String> texts = new ArrayList<>();
                    for (Key key : suggestions.get()) {
                        texts.add(key.toString());
                    }
                    return texts;
                }) {
                    @Override
                    public Key parse(String token) {
                        try {
                            return Key.parse(token);
                        } catch (IllegalArgumentException e) {
                            throw new CommandException(e.getMessage() == null ? "Invalid key" : e.getMessage());
                        }
                    }
                },
                false);
    }

    /**
     * An enum constant, matched ignoring case.
     *
     * @param <E> the enum type
     * @param name the argument name
     * @param values all constants, usually {@code MyEnum.values()}
     * @return the argument
     */
    public static <E extends Enum<E>> Argument<E> enumOf(String name, E[] values) {
        E[] constants = values.clone();
        List<String> names = new ArrayList<>();
        for (E constant : constants) {
            names.add(constant.name().toLowerCase(Locale.ROOT));
        }
        return new Argument<>(
                name,
                new SuggestingType<>(String.join("|", names), () -> names) {
                    @Override
                    public E parse(String token) {
                        for (E constant : constants) {
                            if (constant.name().equalsIgnoreCase(token)) {
                                return constant;
                            }
                        }
                        throw new CommandException(
                                "Expected one of " + String.join(", ", names) + ", got '" + token + "'");
                    }
                },
                false);
    }

    /**
     * An argument of a custom type.
     *
     * @param <T> the parsed type
     * @param name the argument name
     * @param type the parser
     * @return the argument
     */
    public static <T> Argument<T> of(String name, ArgumentType<T> type) {
        return new Argument<>(name, type, false);
    }

    private abstract static class SimpleType<T> implements ArgumentType<T> {
        private final String description;

        SimpleType(String description) {
            this.description = description;
        }

        @Override
        public List<String> suggest(String partial) {
            return List.of();
        }

        @Override
        public String description() {
            return description;
        }
    }

    private abstract static class SuggestingType<T> extends SimpleType<T> {
        private final Supplier<? extends Collection<String>> options;

        SuggestingType(String description, Supplier<? extends Collection<String>> options) {
            super(description);
            this.options = options;
        }

        @Override
        public List<String> suggest(String partial) {
            List<String> matches = new ArrayList<>();
            for (String option : options.get()) {
                if (option.startsWith(partial)) {
                    matches.add(option);
                }
            }
            return matches;
        }
    }
}
