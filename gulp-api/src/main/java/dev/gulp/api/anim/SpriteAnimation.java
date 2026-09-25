package dev.gulp.api.anim;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.graphics.TextureRegion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * A named frame animation: images, how long each shows, a {@link PlayMode} and code run on chosen frames (a footstep
 * sound, for example). Immutable; played by the {@link Animator} component.
 *
 * <pre>{@code
 * SpriteAnimation run = SpriteAnimation.builder("run")
 *         .frames(sprites.regions("player/run_"), 12f)
 *         .onFrame(2, e -> e.world().playSound(e.position(), STEP))
 *         .build();
 * }</pre>
 */
public final class SpriteAnimation {

    private final String name;
    private final List<TextureRegion> frames;
    private final float[] durations;
    private final PlayMode mode;
    private final int[] eventFrames;
    private final List<Consumer<Entity>> events;

    private SpriteAnimation(
            String name,
            List<TextureRegion> frames,
            float[] durations,
            PlayMode mode,
            int[] eventFrames,
            List<Consumer<Entity>> events) {
        this.name = name;
        this.frames = List.copyOf(frames);
        this.durations = durations;
        this.mode = mode;
        this.eventFrames = eventFrames;
        this.events = List.copyOf(events);
    }

    /**
     * Starts building an animation.
     *
     * @param name the name it is played by
     * @return the builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the number of frames.
     *
     * @return the count
     */
    public int frameCount() {
        return frames.size();
    }

    /**
     * Returns a frame image.
     *
     * @param index the frame
     * @return the image
     */
    public TextureRegion frame(int index) {
        return frames.get(index);
    }

    /**
     * Returns how long a frame shows.
     *
     * @param index the frame
     * @return seconds
     */
    public float duration(int index) {
        return durations[index];
    }

    /**
     * Returns the length of one pass through the frames.
     *
     * @return seconds
     */
    public float totalDuration() {
        float total = 0f;
        for (float d : durations) {
            total += d;
        }
        return total;
    }

    /**
     * Returns the play mode.
     *
     * @return the mode
     */
    public PlayMode mode() {
        return mode;
    }

    /**
     * Returns a copy with another play mode.
     *
     * @param value the mode
     * @return the copy
     */
    public SpriteAnimation withMode(PlayMode value) {
        return new SpriteAnimation(name, frames, durations, value, eventFrames, events);
    }

    /**
     * Returns a copy that also runs code when a frame shows, for animations loaded from files.
     *
     * @param frame the frame index
     * @param action receives the animated entity
     * @return the copy
     */
    public SpriteAnimation onFrame(int frame, Consumer<Entity> action) {
        int n = eventFrames.length;
        int[] frames2 = Arrays.copyOf(eventFrames, n + 1);
        frames2[n] = frame;
        List<Consumer<Entity>> events2 = new ArrayList<>(events);
        events2.add(action);
        return new SpriteAnimation(name, frames, durations, mode, frames2, events2);
    }

    /**
     * Runs the code attached to a frame.
     *
     * @param frame the frame that just showed
     * @param entity the animated entity
     */
    public void fireFrame(int frame, Entity entity) {
        for (int i = 0; i < eventFrames.length; i++) {
            if (eventFrames[i] == frame) {
                events.get(i).accept(entity);
            }
        }
    }

    /**
     * Returns whether code is attached to any frame.
     *
     * @return {@code true} with frame events
     */
    public boolean hasFrameEvents() {
        return eventFrames.length > 0;
    }

    @Override
    public String toString() {
        return "SpriteAnimation[" + name + ", " + frames.size() + " frames, " + mode + "]";
    }

    /** Builds a {@link SpriteAnimation}. */
    public static final class Builder {
        private final String name;
        private final List<TextureRegion> frames = new ArrayList<>();
        private final List<Float> durations = new ArrayList<>();
        private PlayMode mode = PlayMode.LOOP;
        private final List<Integer> eventFrames = new ArrayList<>();
        private final List<Consumer<Entity>> events = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Adds a frame.
         *
         * @param region the image
         * @param seconds how long it shows
         * @return this builder
         */
        public Builder frame(TextureRegion region, float seconds) {
            frames.add(region);
            durations.add(Math.max(0.001f, seconds));
            return this;
        }

        /**
         * Adds frames shown for the same time.
         *
         * @param regions the images, in order
         * @param fps frames per second
         * @return this builder
         */
        public Builder frames(List<TextureRegion> regions, float fps) {
            float seconds = 1f / Math.max(0.001f, fps);
            for (TextureRegion region : regions) {
                frame(region, seconds);
            }
            return this;
        }

        /**
         * Sets the play mode; {@link PlayMode#LOOP} by default.
         *
         * @param value the mode
         * @return this builder
         */
        public Builder mode(PlayMode value) {
            mode = value;
            return this;
        }

        /**
         * Runs code when a frame shows.
         *
         * @param frame the frame index
         * @param action receives the animated entity
         * @return this builder
         */
        public Builder onFrame(int frame, Consumer<Entity> action) {
            eventFrames.add(frame);
            events.add(action);
            return this;
        }

        /**
         * Finishes the animation.
         *
         * @return the animation
         * @throws IllegalStateException without frames
         */
        public SpriteAnimation build() {
            if (frames.isEmpty()) {
                throw new IllegalStateException("Animation " + name + " has no frames");
            }
            float[] times = new float[durations.size()];
            for (int i = 0; i < times.length; i++) {
                times[i] = durations.get(i);
            }
            int[] at = new int[eventFrames.size()];
            for (int i = 0; i < at.length; i++) {
                at[i] = eventFrames.get(i);
            }
            return new SpriteAnimation(name, frames, times, mode, at, events);
        }
    }
}
