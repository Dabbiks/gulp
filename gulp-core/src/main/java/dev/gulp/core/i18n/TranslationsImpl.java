package dev.gulp.core.i18n;

import dev.gulp.api.GameSettings;
import dev.gulp.api.Logger;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.i18n.LocaleChangeEvent;
import dev.gulp.api.i18n.Translations;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.asset.AssetsImpl;
import dev.gulp.core.data.JsonReader;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.scheduler.PromiseImpl;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * {@link Translations}: {@code <namespace>/lang/<locale>.json} of the game's namespace and the engine's, read through
 * the asset system so resource packs can override them. Lookup goes from the locale to its language to the default
 * locale; arguments replace {@code {0}}, {@code {1}}, and so on.
 */
public final class TranslationsImpl implements Translations {

    private final AssetsImpl assets;
    private final List<String> namespaces;
    private final String defaultLocale;
    private final EventBus events;
    private final Logger logger;
    private final Supplier<PromiseImpl<Void>> promises;
    private String locale;
    private List<Map<String, String>> chain = List.of();
    private final Set<String> warned = new HashSet<>();
    private int revision;

    /**
     * Creates the translations.
     *
     * @param assets reads the language files
     * @param namespaces namespaces searched, highest priority first
     * @param locale the starting locale
     * @param defaultLocale the fallback locale
     * @param events where {@link LocaleChangeEvent} goes
     * @param logger where missing keys are reported
     * @param promises creates promises owned by the game
     */
    public TranslationsImpl(
            AssetsImpl assets,
            List<String> namespaces,
            String locale,
            String defaultLocale,
            EventBus events,
            Logger logger,
            Supplier<PromiseImpl<Void>> promises) {
        this.assets = assets;
        this.namespaces = List.copyOf(namespaces);
        this.locale = GameSettings.normalizeLocale(locale);
        this.defaultLocale = GameSettings.normalizeLocale(defaultLocale);
        this.events = events;
        this.logger = logger;
        this.promises = promises;
    }

    /**
     * Reads the files of the current locale.
     *
     * @param done runs on the main thread when they were read
     */
    public void load(Runnable done) {
        read(locale, maps -> {
            chain = maps;
            revision++;
            done.run();
        });
    }

    /** Reads the files again after one changed (hot reload). */
    public void reload() {
        load(() -> logger.info("Reloaded translations for " + locale));
    }

    /**
     * Returns a number that changes whenever translations change.
     *
     * @return the revision
     */
    public int revision() {
        return revision;
    }

    private void read(String wanted, java.util.function.Consumer<List<Map<String, String>>> done) {
        List<String> locales = new ArrayList<>(
                new LinkedHashSet<>(List.of(wanted, language(wanted), defaultLocale, language(defaultLocale))));
        List<Map<String, String>> maps = new ArrayList<>();
        List<Map<String, Integer>> owners = new ArrayList<>();
        for (int i = 0; i < locales.size(); i++) {
            maps.add(new HashMap<>());
            owners.add(new HashMap<>());
        }
        int[] remaining = {locales.size() * namespaces.size()};
        for (int l = 0; l < locales.size(); l++) {
            Map<String, String> target = maps.get(l);
            Map<String, Integer> targetOwners = owners.get(l);
            for (int n = namespaces.size() - 1; n >= 0; n--) {
                String path = namespaces.get(n) + "/lang/" + locales.get(l) + ".json";
                Map<String, String> part = new HashMap<>();
                int order = n;
                assets.readBytes(path)
                        .thenSync(bytes -> {
                            try {
                                flatten("", JsonReader.parse(new String(bytes, StandardCharsets.UTF_8)), part);
                            } catch (RuntimeException e) {
                                logger.error("Cannot read " + path + ": " + e.getMessage());
                            }
                            merge(target, targetOwners, part, order);
                            if (--remaining[0] == 0) {
                                done.accept(maps);
                            }
                        })
                        .onFailure(error -> {
                            if (!(error instanceof FileNotFoundException)) {
                                logger.warn("Cannot read " + path + ": " + error.getMessage());
                            }
                            if (--remaining[0] == 0) {
                                done.accept(maps);
                            }
                        });
            }
        }
    }

    /** Game keys win over engine keys: namespaces earlier in the list have priority. */
    private static void merge(
            Map<String, String> target, Map<String, Integer> owners, Map<String, String> part, int order) {
        for (Map.Entry<String, String> entry : part.entrySet()) {
            Integer current = owners.get(entry.getKey());
            if (current == null || order < current) {
                target.put(entry.getKey(), entry.getValue());
                owners.put(entry.getKey(), order);
            }
        }
    }

    private static void flatten(String prefix, JsonValue value, Map<String, String> out) {
        if (value instanceof JsonObject object) {
            for (String name : object.names()) {
                flatten(prefix.isEmpty() ? name : prefix + "." + name, object.getOrThrow(name), out);
            }
        } else if (!prefix.isEmpty()) {
            out.put(prefix, value instanceof dev.gulp.api.data.JsonString text ? text.value() : value.toString());
        }
    }

    private static String language(String locale) {
        int underscore = locale.indexOf('_');
        return underscore < 0 ? locale : locale.substring(0, underscore);
    }

    @Override
    public String locale() {
        return locale;
    }

    @Override
    public String tr(String key, Object... arguments) {
        for (Map<String, String> map : chain) {
            String value = map.get(key);
            if (value != null) {
                return format(value, arguments);
            }
        }
        if (warned.add(key)) {
            logger.warn("No translation for '" + key + "' in " + locale);
        }
        return key;
    }

    /**
     * Replaces {@code {0}}, {@code {1}}, and so on; other braces stay.
     *
     * @param pattern the translation
     * @param arguments the values
     * @return the formatted string
     */
    static String format(String pattern, Object[] arguments) {
        if (arguments.length == 0 || pattern.indexOf('{') < 0) {
            return pattern;
        }
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int close = pattern.indexOf('}', i);
                if (close > i + 1) {
                    String inside = pattern.substring(i + 1, close);
                    boolean digits = inside.chars().allMatch(Character::isDigit);
                    if (digits && inside.length() < 4) {
                        int index = Integer.parseInt(inside);
                        if (index < arguments.length) {
                            out.append(arguments[index]);
                            i = close + 1;
                            continue;
                        }
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    @Override
    public boolean has(String key) {
        for (Map<String, String> map : chain) {
            if (map.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Promise<Void> setLocale(String newLocale) {
        String normalized = GameSettings.normalizeLocale(newLocale);
        PromiseImpl<Void> promise = promises.get();
        read(normalized, maps -> {
            locale = normalized;
            chain = maps;
            warned.clear();
            revision++;
            if (events.hasListeners(LocaleChangeEvent.class)) {
                events.call(new LocaleChangeEvent(normalized));
            }
            promise.complete(null);
        });
        return promise;
    }

    @Override
    public List<String> availableLocales() {
        Set<String> found = new TreeSet<>();
        for (String namespace : namespaces) {
            for (String path : assets.filesUnder(namespace + "/lang/")) {
                String file = path.substring(path.lastIndexOf('/') + 1);
                if (file.endsWith(".json")) {
                    found.add(file.substring(0, file.length() - 5));
                }
            }
        }
        return List.copyOf(found);
    }
}
