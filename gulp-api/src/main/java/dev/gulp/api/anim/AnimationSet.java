package dev.gulp.api.anim;

import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureRegion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Named frame animations of one character or object. Loaded from an Aseprite export ({@code AssetKey.animations}; tags
 * become names, with their frame times and direction) or built from atlas regions named {@code prefix + name + "_N"}.
 * Immutable.
 *
 * <pre>{@code
 * AnimationSet hero = assets().get(GameAssets.Animations.HERO);
 * AnimationSet slime = AnimationSet.fromAtlas(sprites, "slime/", 8f, "idle", "hop");
 * entity.add(new Animator(hero)).play("idle");
 * }</pre>
 */
public final class AnimationSet {

    private final Map<String, SpriteAnimation> animations;

    private AnimationSet(Map<String, SpriteAnimation> animations) {
        this.animations = animations;
    }

    /**
     * Returns a set of animations.
     *
     * @param animations the animations, by their names
     * @return the set
     */
    public static AnimationSet of(SpriteAnimation... animations) {
        return of(List.of(animations));
    }

    /**
     * Returns a set of animations.
     *
     * @param animations the animations, by their names
     * @return the set
     */
    public static AnimationSet of(List<SpriteAnimation> animations) {
        Map<String, SpriteAnimation> map = new LinkedHashMap<>();
        for (SpriteAnimation animation : animations) {
            map.put(animation.name(), animation);
        }
        return new AnimationSet(map);
    }

    /**
     * Builds looping animations from atlas regions: {@code player/run_0}, {@code player/run_1}, ... become animation
     * {@code run} for prefix {@code player/}.
     *
     * @param atlas the atlas
     * @param prefix the start of the region names
     * @param fps frames per second
     * @param names the animation names
     * @return the set
     * @throws IllegalArgumentException if an animation has no regions
     */
    public static AnimationSet fromAtlas(TextureAtlas atlas, String prefix, float fps, String... names) {
        List<SpriteAnimation> list = new ArrayList<>();
        for (String name : names) {
            List<TextureRegion> frames = atlas.regions(prefix + name + "_");
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("No regions named " + prefix + name + "_N in the atlas");
            }
            list.add(SpriteAnimation.builder(name).frames(frames, fps).build());
        }
        return of(list);
    }

    /**
     * Returns an animation.
     *
     * @param name its name
     * @return the animation, or {@code null}
     */
    public @Nullable SpriteAnimation get(String name) {
        return animations.get(name);
    }

    /**
     * Returns an animation that must exist.
     *
     * @param name its name
     * @return the animation
     * @throws IllegalArgumentException if there is none
     */
    public SpriteAnimation getOrThrow(String name) {
        SpriteAnimation animation = animations.get(name);
        if (animation == null) {
            throw new IllegalArgumentException("No animation named " + name + "; there are " + animations.keySet());
        }
        return animation;
    }

    /**
     * Returns whether an animation exists.
     *
     * @param name its name
     * @return {@code true} if present
     */
    public boolean has(String name) {
        return animations.containsKey(name);
    }

    /**
     * Returns the names, in the order they were added.
     *
     * @return the names
     */
    public List<String> names() {
        return List.copyOf(animations.keySet());
    }

    /**
     * Returns a copy with an animation added or replaced, for example one with frame events.
     *
     * @param animation the animation
     * @return the copy
     */
    public AnimationSet with(SpriteAnimation animation) {
        Map<String, SpriteAnimation> map = new LinkedHashMap<>(animations);
        map.put(animation.name(), animation);
        return new AnimationSet(map);
    }

    @Override
    public String toString() {
        return "AnimationSet" + animations.keySet();
    }
}
