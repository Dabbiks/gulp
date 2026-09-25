package dev.gulp.core.asset;

import dev.gulp.api.anim.AnimationSet;
import dev.gulp.api.anim.PlayMode;
import dev.gulp.api.anim.SpriteAnimation;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Gradient;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.FloatCurve;
import dev.gulp.api.particle.EmitterConfig;
import dev.gulp.api.particle.EmitterShape;
import dev.gulp.api.particle.ParticleCollision;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.registry.Key;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.data.JsonReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Loaders of frame animations exported from Aseprite and of particle effects described in JSON. Both reload when
 * their file changes.
 */
public final class AnimationLoaders {

    private AnimationLoaders() {}

    /**
     * Registers the loaders.
     *
     * @param assets the asset manager
     */
    public static void register(AssetsImpl assets) {
        assets.registerLoader(
                AssetType.ANIMATIONS,
                context -> context.text().flatMap(text -> {
                    JsonObject json = JsonReader.parse(text).asObject();
                    JsonObject meta = json.getOrThrow("meta").asObject();
                    String image = meta.getOrThrow("image").asString();
                    String file = image.substring(image.lastIndexOf('/') + 1);
                    return context.dependency(AssetKey.texture(BuiltinLoaders.sibling(context.path(), file)))
                            .map(texture -> {
                                texture.setFilter(TextureFilter.NEAREST);
                                return aseprite(json, texture);
                            });
                }));
        assets.registerLoader(
                AssetType.PARTICLES,
                context -> context.text().flatMap(text -> {
                    JsonObject json = JsonReader.parse(text).asObject();
                    String namespace = context.key().key().namespace();
                    Set<String> references = new LinkedHashSet<>();
                    collectReferences(json, references);
                    List<String> keys = new ArrayList<>(references);
                    List<Promise<ParticleEffect>> loads = new ArrayList<>();
                    for (String reference : keys) {
                        loads.add(context.dependency(AssetKey.of(AssetType.PARTICLES, qualify(reference, namespace))));
                    }
                    return assets.all(loads).map(loaded -> {
                        Map<String, ParticleEffect> resolved = new HashMap<>();
                        for (int i = 0; i < keys.size(); i++) {
                            resolved.put(keys.get(i), loaded.get(i));
                        }
                        return particles(context.key().key(), json, namespace, resolved);
                    });
                }));
    }

    // ------------------------------------------------------------------ Aseprite

    /**
     * Builds animations from an Aseprite export: every frame tag becomes an animation with the frame times and the
     * tag direction; without tags, all frames form one looping animation named {@code default}.
     *
     * @param json the exported JSON, array or hash of frames
     * @param sheet the sprite sheet
     * @return the animations
     */
    public static AnimationSet aseprite(JsonObject json, Texture sheet) {
        List<TextureRegion> regions = new ArrayList<>();
        List<Float> durations = new ArrayList<>();
        JsonValue frames = json.getOrThrow("frames");
        if (frames instanceof JsonObject hash) {
            for (String name : hash.names()) {
                addFrame(hash.getOrThrow(name).asObject(), sheet, regions, durations);
            }
        } else {
            for (JsonValue frame : frames.asArray()) {
                addFrame(frame.asObject(), sheet, regions, durations);
            }
        }
        JsonValue meta = json.get("meta");
        JsonValue tags = meta == null ? null : meta.asObject().get("frameTags");
        List<SpriteAnimation> animations = new ArrayList<>();
        if (tags == null || tags.asArray().size() == 0) {
            animations.add(animation("default", regions, durations, 0, regions.size() - 1, "forward", null));
        } else {
            for (JsonValue tag : tags.asArray()) {
                JsonObject object = tag.asObject();
                JsonValue repeat = object.get("repeat");
                animations.add(animation(
                        object.getOrThrow("name").asString(),
                        regions,
                        durations,
                        object.getOrThrow("from").asInt(),
                        object.getOrThrow("to").asInt(),
                        object.has("direction") ? object.getOrThrow("direction").asString() : "forward",
                        repeat == null ? null : repeat.asString()));
            }
        }
        return AnimationSet.of(animations);
    }

    private static void addFrame(JsonObject frame, Texture sheet, List<TextureRegion> regions, List<Float> durations) {
        regions.add(TextureAtlasImpl.frameRegion(frame, sheet));
        JsonValue duration = frame.get("duration");
        durations.add(duration == null ? 0.1f : duration.asFloat() / 1000f);
    }

    private static SpriteAnimation animation(
            String name,
            List<TextureRegion> regions,
            List<Float> durations,
            int from,
            int to,
            String direction,
            @Nullable String repeat) {
        boolean reverse = direction.startsWith("reverse") || direction.equals("pingpong_reverse");
        boolean pingPong = direction.startsWith("pingpong");
        boolean once = "1".equals(repeat);
        SpriteAnimation.Builder builder = SpriteAnimation.builder(name);
        int last = Math.min(to, regions.size() - 1);
        int first = Math.max(0, from);
        for (int i = 0; i <= last - first; i++) {
            int index = reverse ? last - i : first + i;
            builder.frame(regions.get(index), durations.get(index));
        }
        builder.mode(pingPong ? PlayMode.PING_PONG : once ? PlayMode.ONCE : PlayMode.LOOP);
        return builder.build();
    }

    // ------------------------------------------------------------------ particles

    private static void collectReferences(JsonObject effect, Set<String> into) {
        JsonValue emitters = effect.get("emitters");
        if (emitters == null) {
            return;
        }
        for (JsonValue emitter : emitters.asArray()) {
            JsonValue death = emitter.asObject().get("onDeath");
            if (death instanceof JsonString s) {
                into.add(s.value());
            } else if (death instanceof JsonObject nested) {
                collectReferences(nested, into);
            }
        }
    }

    private static String qualify(String reference, String namespace) {
        return reference.indexOf(':') >= 0 ? reference : namespace + ":" + reference;
    }

    /**
     * Builds a particle effect from JSON: {@code {"emitters": [ ... ]}}, each emitter an object whose keys match the
     * {@link EmitterConfig.Builder} methods. Ranges are {@code [min, max]} or one number, curves {@code [[t, v], ...]}
     * or one number, gradients a list of colours, {@code [[t, "#hex"], ...]} or one colour.
     *
     * @param key the key of the effect
     * @param json the description
     * @param namespace namespace of region and effect references without one
     * @param references effects named by {@code onDeath} strings, already loaded
     * @return the effect
     * @throws IllegalArgumentException for unknown shapes, blend modes or collision modes
     */
    public static ParticleEffect particles(
            Key key, JsonObject json, String namespace, Map<String, ParticleEffect> references) {
        ParticleEffect.Builder effect = ParticleEffect.builder(key);
        JsonValue emitters = json.get("emitters");
        if (emitters != null) {
            int index = 0;
            for (JsonValue value : emitters.asArray()) {
                effect.emitter(emitter(key, index++, value.asObject(), namespace, references));
            }
        }
        return effect.build();
    }

    private static EmitterConfig emitter(
            Key key, int index, JsonObject e, String namespace, Map<String, ParticleEffect> references) {
        EmitterConfig.Builder b = EmitterConfig.builder();
        if (e.has("rate")) {
            b.rate(e.getOrThrow("rate").asFloat());
        }
        JsonValue bursts = e.get("bursts");
        if (bursts != null) {
            for (JsonValue burst : bursts.asArray()) {
                JsonObject o = burst.asObject();
                b.burst(
                        o.getOrThrow("count").asInt(),
                        o.has("at") ? o.getOrThrow("at").asFloat() : 0f);
            }
        }
        if (e.has("duration")) {
            b.duration(e.getOrThrow("duration").asFloat());
        }
        if (e.has("loop")) {
            b.loop(e.getOrThrow("loop").asBoolean());
        }
        JsonValue shape = e.get("shape");
        if (shape != null) {
            b.shape(shape(shape.asObject()));
        }
        if (e.has("direction")) {
            b.direction(e.getOrThrow("direction").asFloat());
        }
        if (e.has("spread")) {
            b.spread(e.getOrThrow("spread").asFloat());
        }
        float[] range = range(e.get("speed"));
        if (range != null) {
            b.speed(range[0], range[1]);
        }
        JsonValue gravity = e.get("gravity");
        if (gravity != null) {
            JsonArray g = gravity.asArray();
            b.gravity(g.get(0).asFloat(), g.get(1).asFloat());
        }
        if (e.has("drag")) {
            b.drag(e.getOrThrow("drag").asFloat());
        }
        range = range(e.get("spin"));
        if (range != null) {
            b.spin(range[0], range[1]);
        }
        range = range(e.get("rotation"));
        if (range != null) {
            b.rotation(range[0], range[1]);
        }
        range = range(e.get("lifetime"));
        if (range != null) {
            b.lifetime(range[0], range[1]);
        }
        JsonValue size = e.get("size");
        if (size != null) {
            b.size(curve(size));
        }
        JsonValue color = e.get("color");
        if (color != null) {
            b.color(gradient(color));
        }
        JsonValue alpha = e.get("alpha");
        if (alpha != null) {
            b.alpha(curve(alpha));
        }
        JsonValue region = e.get("region");
        if (region != null) {
            b.region(AssetKey.region(qualify(region.asString(), namespace)));
        }
        JsonValue frames = e.get("frames");
        if (frames != null) {
            List<AssetKey<TextureRegion>> keys = new ArrayList<>();
            for (JsonValue frame : frames.asArray()) {
                keys.add(AssetKey.region(qualify(frame.asString(), namespace)));
            }
            b.frames(keys);
        }
        if (e.has("blend")) {
            b.blend(BlendMode.valueOf(e.getOrThrow("blend").asString().toUpperCase(Locale.ROOT)));
        }
        if (e.has("local")) {
            b.local(e.getOrThrow("local").asBoolean());
        }
        JsonValue death = e.get("onDeath");
        if (death instanceof JsonString s) {
            b.onDeath(references.get(s.value()));
        } else if (death instanceof JsonObject nested) {
            b.onDeath(particles(Key.of(key.namespace(), key.path() + "/death" + index), nested, namespace, references));
        }
        if (e.has("collision")) {
            ParticleCollision mode = ParticleCollision.valueOf(
                    e.getOrThrow("collision").asString().toUpperCase(Locale.ROOT));
            b.collision(mode, e.has("bounce") ? e.getOrThrow("bounce").asFloat() : 0.5f);
        }
        if (e.has("max")) {
            b.maxParticles(e.getOrThrow("max").asInt());
        }
        if (e.has("layer")) {
            b.layer(e.getOrThrow("layer").asString());
        }
        return b.build();
    }

    private static EmitterShape shape(JsonObject o) {
        String type = o.has("type") ? o.getOrThrow("type").asString() : "point";
        return switch (type) {
            case "point" -> EmitterShape.point();
            case "circle" -> EmitterShape.circle(number(o, "radius", 1f));
            case "ring" -> EmitterShape.ring(number(o, "radius", 1f), number(o, "thickness", 0f));
            case "rect" -> EmitterShape.rect(number(o, "width", 1f), number(o, "height", 1f));
            case "line" -> EmitterShape.line(number(o, "length", 1f));
            default -> throw new IllegalArgumentException("Unknown emitter shape: " + type);
        };
    }

    private static float number(JsonObject o, String name, float fallback) {
        JsonValue value = o.get(name);
        return value == null ? fallback : value.asFloat();
    }

    private static float @Nullable [] range(@Nullable JsonValue value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNumber n) {
            float v = n.asFloat();
            return new float[] {v, v};
        }
        JsonArray a = value.asArray();
        return new float[] {a.get(0).asFloat(), a.get(a.size() - 1).asFloat()};
    }

    private static FloatCurve curve(JsonValue value) {
        if (value instanceof JsonNumber n) {
            return FloatCurve.constant(n.asFloat());
        }
        JsonArray keys = value.asArray();
        float[] pairs = new float[keys.size() * 2];
        for (int i = 0; i < keys.size(); i++) {
            JsonArray key = keys.get(i).asArray();
            pairs[i * 2] = key.get(0).asFloat();
            pairs[i * 2 + 1] = key.get(1).asFloat();
        }
        return FloatCurve.of(pairs);
    }

    private static Gradient gradient(JsonValue value) {
        if (value instanceof JsonString s) {
            return Gradient.constant(Color.hex(s.value()));
        }
        JsonArray stops = value.asArray();
        if (stops.size() > 0 && stops.get(0) instanceof JsonString) {
            Color[] colors = new Color[stops.size()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = Color.hex(stops.get(i).asString());
            }
            return Gradient.of(colors);
        }
        Gradient gradient = null;
        for (JsonValue stop : stops) {
            JsonArray pair = stop.asArray();
            float t = pair.get(0).asFloat();
            Color color = Color.hex(pair.get(1).asString());
            gradient = gradient == null ? Gradient.constant(color).with(t, color) : gradient.with(t, color);
        }
        return gradient == null ? Gradient.WHITE : gradient;
    }
}
