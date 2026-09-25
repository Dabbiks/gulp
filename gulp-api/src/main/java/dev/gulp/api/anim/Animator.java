package dev.gulp.api.anim;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.component.SpriteComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Plays frame animations from an {@link AnimationSet} on the {@link SpriteComponent} of its entity, advancing every
 * tick. Rules from {@link #auto()} pick the animation from game state; {@link #playOnce} interrupts them for one play.
 *
 * <pre>{@code
 * Animator animator = player.add(new Animator(GameAssets.Animations.HERO));
 * animator.auto()
 *         .when(() -> !mover.isOnFloor(), "jump")
 *         .when(() -> mover.velocity().x() != 0, "run")
 *         .otherwise("idle");
 * animator.playOnce("attack").then("idle");
 * }</pre>
 */
public final class Animator extends Component {

    private @Nullable AnimationSet set;
    private final @Nullable AssetKey<AnimationSet> key;
    private @Nullable SpriteAnimation current;
    private @Nullable String requested;
    private boolean requestedOnce;
    private int frame;
    private float frameTime;
    private int direction = 1;
    private boolean finished;
    private boolean once;
    private @Nullable String after;
    private float speed = 1f;
    private @Nullable Auto auto;
    private final Then then = new Then();

    /**
     * Creates the component.
     *
     * @param animations the animations
     */
    public Animator(AnimationSet animations) {
        this.set = animations;
        this.key = null;
    }

    /**
     * Creates the component for animations loaded as an asset; they are used once loaded.
     *
     * @param animations the asset key
     */
    public Animator(AssetKey<AnimationSet> animations) {
        this.key = animations;
    }

    /**
     * Returns the animations.
     *
     * @return the set, or {@code null} while it loads
     */
    public @Nullable AnimationSet animations() {
        return set;
    }

    /**
     * Plays an animation in its own mode; does nothing if it already plays.
     *
     * @param name the animation name
     * @return this component
     * @throws IllegalArgumentException if the set has no such animation
     */
    public Animator play(String name) {
        SpriteAnimation playing = current;
        if (playing != null && playing.name().equals(name) && !once && requested == null) {
            return this;
        }
        once = false;
        after = null;
        begin(name, false);
        return this;
    }

    /**
     * Plays an animation once from the start, pausing the {@link #auto()} rules until it ends.
     *
     * @param name the animation name
     * @return lets you choose what plays next
     */
    public Then playOnce(String name) {
        once = true;
        after = null;
        begin(name, true);
        return then;
    }

    private void begin(String name, boolean single) {
        AnimationSet animations = set;
        if (animations == null) {
            requested = name;
            requestedOnce = single;
            return;
        }
        requested = null;
        SpriteAnimation animation = animations.getOrThrow(name);
        current = animation;
        finished = false;
        frameTime = 0f;
        direction = 1;
        frame = animation.mode() == PlayMode.REVERSED ? animation.frameCount() - 1 : 0;
        shown();
    }

    /**
     * Sets the playback speed.
     *
     * @param value {@code 1} normal, {@code 2} twice as fast
     * @return this component
     */
    public Animator speed(float value) {
        speed = Math.max(0f, value);
        return this;
    }

    /**
     * Returns the playback speed.
     *
     * @return the multiplier
     */
    public float speed() {
        return speed;
    }

    /**
     * Returns the playing animation.
     *
     * @return the animation, or {@code null} before the first play
     */
    public @Nullable SpriteAnimation current() {
        return current;
    }

    /**
     * Returns the name of the playing animation.
     *
     * @return the name, or an empty string
     */
    public String currentName() {
        SpriteAnimation playing = current;
        return playing == null ? "" : playing.name();
    }

    /**
     * Returns the frame shown.
     *
     * @return the frame index
     */
    public int frame() {
        return frame;
    }

    /**
     * Returns whether the animation ended (only animations that end by themselves do).
     *
     * @return {@code true} once finished
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Starts rules that choose the animation every tick, replacing earlier rules. The first matching rule wins.
     *
     * @return the rules
     */
    public Auto auto() {
        Auto rules = new Auto();
        auto = rules;
        return rules;
    }

    /** Removes the {@link #auto()} rules. */
    public void stopAuto() {
        auto = null;
    }

    @Override
    protected void onSpawn() {
        resolve();
        show();
    }

    @Override
    protected void onTick() {
        resolve();
        Auto rules = auto;
        if (rules != null && !once) {
            String pick = rules.pick();
            if (pick != null) {
                play(pick);
            }
        }
        advance(speed / Math.max(1, engine().targetTps()));
        show();
    }

    private void resolve() {
        AssetKey<AnimationSet> asset = key;
        if (set == null && asset != null && assets().isLoaded(asset)) {
            set = assets().get(asset);
        }
        String pending = requested;
        if (set != null && pending != null) {
            begin(pending, requestedOnce);
        }
    }

    private void advance(float seconds) {
        SpriteAnimation playing = current;
        if (playing == null || finished) {
            return;
        }
        frameTime += seconds;
        while (frameTime >= playing.duration(frame)) {
            frameTime -= playing.duration(frame);
            if (!next(playing)) {
                end(playing);
                return;
            }
            shown();
        }
    }

    private boolean next(SpriteAnimation playing) {
        int n = playing.frameCount();
        PlayMode mode = playing.mode();
        if (once && mode != PlayMode.REVERSED) {
            mode = PlayMode.ONCE;
        }
        switch (mode) {
            case ONCE -> {
                if (frame + 1 >= n) {
                    return false;
                }
                frame++;
            }
            case REVERSED -> {
                if (frame <= 0) {
                    return false;
                }
                frame--;
            }
            case LOOP -> frame = (frame + 1) % n;
            case PING_PONG -> {
                if (n > 1) {
                    if (frame + direction < 0 || frame + direction >= n) {
                        direction = -direction;
                    }
                    frame += direction;
                }
            }
            case LOOP_RANDOM -> frame = (int) (Math.random() * n);
        }
        return true;
    }

    private void end(SpriteAnimation playing) {
        finished = true;
        frameTime = 0f;
        if (engine().events().hasListeners(AnimationEndEvent.class)) {
            engine().events().call(new AnimationEndEvent(entity(), playing.name()));
        }
        if (once) {
            once = false;
            String next = after;
            after = null;
            if (next != null && current == playing) {
                begin(next, false);
            }
        }
    }

    private void shown() {
        SpriteAnimation playing = current;
        if (playing == null || !isAttached() || !entity().isSpawned()) {
            return;
        }
        if (playing.hasFrameEvents()) {
            playing.fireFrame(frame, entity());
        }
        if (engine().events().hasListeners(AnimationFrameEvent.class)) {
            engine().events().call(new AnimationFrameEvent(entity(), playing.name(), frame));
        }
    }

    private void show() {
        SpriteAnimation playing = current;
        if (playing != null && entity().has(SpriteComponent.class)) {
            SpriteComponent sprite = entity().get(SpriteComponent.class);
            if (sprite.region() != playing.frame(frame)) {
                sprite.setRegion(playing.frame(frame));
            }
        }
    }

    /** Chooses what plays after a {@link #playOnce}. */
    public final class Then {
        private Then() {}

        /**
         * Plays an animation when the single play ends.
         *
         * @param name the animation name
         * @return the animator
         */
        public Animator then(String name) {
            after = name;
            return Animator.this;
        }
    }

    /** Rules that choose the animation from game state, checked in order every tick. */
    public static final class Auto {
        private final List<BooleanSupplier> conditions = new ArrayList<>();
        private final List<String> names = new ArrayList<>();
        private @Nullable String fallback;

        private Auto() {}

        /**
         * Plays an animation while a condition holds and no earlier rule matched.
         *
         * @param condition the condition
         * @param name the animation name
         * @return these rules
         */
        public Auto when(BooleanSupplier condition, String name) {
            conditions.add(condition);
            names.add(name);
            return this;
        }

        /**
         * Plays an animation when no rule matches.
         *
         * @param name the animation name
         * @return these rules
         */
        public Auto otherwise(String name) {
            fallback = name;
            return this;
        }

        @Nullable String pick() {
            for (int i = 0; i < conditions.size(); i++) {
                if (conditions.get(i).getAsBoolean()) {
                    return names.get(i);
                }
            }
            return fallback;
        }
    }
}
