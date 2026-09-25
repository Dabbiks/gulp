package dev.gulp.api.anim;

import dev.gulp.api.Owner;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Interpolation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Choreography of many properties over time: tracks of keyframes plus events at points in time, with a length, loop,
 * speed and direction. Built once with {@link #builder()} and played any number of times.
 *
 * <pre>{@code
 * Timeline opening = Timeline.builder()
 *         .track(door, Props.Y, k -> k.key(0f, 4f).key(0.8f, 2f, Ease.OUT_BOUNCE))
 *         .track(lamp, Props.LIGHT_INTENSITY, k -> k.key(0f, 0f).key(1.2f, 1f))
 *         .at(0.5f, () -> world.playSound(door.position(), DOOR_SOUND))
 *         .build();
 * opening.play();
 * opening.reverse().play(); // closes it again
 * }</pre>
 */
public final class Timeline extends Animation {

    private final List<Track<?, ?>> tracks;
    private final float[] eventTimes;
    private final Runnable[] events;
    private final float length;
    private boolean loop;
    private float speed = 1f;
    private boolean reversed;
    private float time;
    private boolean fresh;

    private Timeline(Builder builder) {
        tracks = List.copyOf(builder.tracks);
        int n = builder.eventTimes.size();
        eventTimes = new float[n];
        events = new Runnable[n];
        for (int i = 0; i < n; i++) {
            float t = builder.eventTimes.get(i);
            Runnable event = builder.events.get(i);
            int j = i;
            while (j > 0 && eventTimes[j - 1] > t) {
                eventTimes[j] = eventTimes[j - 1];
                events[j] = events[j - 1];
                j--;
            }
            eventTimes[j] = t;
            events[j] = event;
        }
        float end = builder.length;
        if (end <= 0f) {
            for (Track<?, ?> track : tracks) {
                end = Math.max(end, track.end());
            }
            for (float t : eventTimes) {
                end = Math.max(end, t);
            }
        }
        length = end;
        loop = builder.loop;
    }

    /**
     * Starts building a timeline.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Plays from the start of the current direction: from {@code 0}, or from the end when reversed.
     *
     * @return this timeline
     */
    public Timeline play() {
        start();
        return this;
    }

    @Override
    public Timeline start() {
        super.start();
        return this;
    }

    @Override
    public Timeline owner(Owner value) {
        super.owner(value);
        return this;
    }

    /** Stops playing, leaving values where they are. */
    public void stop() {
        kill();
    }

    /**
     * Jumps to a time and shows the values there, without firing events.
     *
     * @param seconds the time, clamped to the length
     * @return this timeline
     */
    public Timeline seek(float seconds) {
        time = Math.clamp(seconds, 0f, length);
        applyTracks();
        return this;
    }

    /**
     * Flips the direction; a running timeline turns around where it is.
     *
     * @return this timeline
     */
    public Timeline reverse() {
        reversed = !reversed;
        return this;
    }

    /**
     * Returns whether it plays backwards.
     *
     * @return {@code true} when reversed
     */
    public boolean isReversed() {
        return reversed;
    }

    /**
     * Sets the playback speed.
     *
     * @param value {@code 1} normal, {@code 2} twice as fast
     * @return this timeline
     */
    public Timeline speed(float value) {
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
     * Sets whether it starts over at the end.
     *
     * @param value whether to loop
     * @return this timeline
     */
    public Timeline loop(boolean value) {
        loop = value;
        return this;
    }

    /**
     * Returns whether it loops.
     *
     * @return {@code true} when looping
     */
    public boolean isLooping() {
        return loop;
    }

    /**
     * Plays in real time, ignoring the pause and the time scale.
     *
     * @return this timeline
     */
    public Timeline realtime() {
        realtime = true;
        return this;
    }

    /**
     * Returns the length.
     *
     * @return seconds
     */
    public float length() {
        return length;
    }

    /**
     * Returns the current time.
     *
     * @return seconds
     */
    public float time() {
        return time;
    }

    @Override
    public float progress() {
        return length <= 0f ? 1f : time / length;
    }

    @Override
    void restart() {
        time = reversed ? length : 0f;
        fresh = true;
    }

    @Override
    boolean step(float seconds) {
        float delta = seconds * speed;
        boolean first = fresh;
        fresh = false;
        if (length <= 0f) {
            fire(0f, 0f, true);
            applyTracks();
            return !loop;
        }
        if (!reversed) {
            float next = time + delta;
            while (next >= length) {
                fire(time, length, first);
                first = false;
                if (!loop) {
                    time = length;
                    applyTracks();
                    return true;
                }
                next -= length;
                time = 0f;
                first = true;
            }
            fire(time, next, first);
            time = next;
        } else {
            float next = time - delta;
            while (next <= 0f) {
                fireBack(time, 0f, first);
                first = false;
                if (!loop) {
                    time = 0f;
                    applyTracks();
                    return true;
                }
                next += length;
                time = length;
                first = true;
            }
            fireBack(time, next, first);
            time = next;
        }
        applyTracks();
        return false;
    }

    /** Fires events in {@code (from, to]}, or {@code [from, to]} on the first step. */
    private void fire(float from, float to, boolean inclusive) {
        for (int i = 0; i < eventTimes.length; i++) {
            float t = eventTimes[i];
            if ((t > from || (inclusive && t == from)) && t <= to) {
                events[i].run();
            }
        }
    }

    /** Fires events in {@code [to, from)} going backwards, or {@code [to, from]} on the first step. */
    private void fireBack(float from, float to, boolean inclusive) {
        for (int i = eventTimes.length - 1; i >= 0; i--) {
            float t = eventTimes[i];
            if ((t < from || (inclusive && t == from)) && t >= to) {
                events[i].run();
            }
        }
    }

    private void applyTracks() {
        for (int i = 0; i < tracks.size(); i++) {
            tracks.get(i).apply(time);
        }
    }

    @Override
    @Nullable Object target() {
        return null;
    }

    @Override
    boolean involves(Object target) {
        for (Track<?, ?> track : tracks) {
            if (track.target == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keyframes of one property, added in time order.
     *
     * @param <V> value type
     */
    public static final class Keys<V> {
        private final List<Float> times = new ArrayList<>();
        private final List<V> values = new ArrayList<>();
        private final List<Interpolation> eases = new ArrayList<>();

        private Keys() {}

        /**
         * Adds a key reached in a straight line from the previous one.
         *
         * @param seconds when
         * @param value the value
         * @return these keys
         */
        public Keys<V> key(float seconds, V value) {
            return key(seconds, value, Ease.LINEAR);
        }

        /**
         * Adds a key reached from the previous one along an easing curve.
         *
         * @param seconds when; not before the previous key
         * @param value the value
         * @param ease the curve into this key
         * @return these keys
         * @throws IllegalArgumentException if the time is before the previous key
         */
        public Keys<V> key(float seconds, V value, Interpolation ease) {
            if (!times.isEmpty() && seconds < times.get(times.size() - 1)) {
                throw new IllegalArgumentException("Timeline keys must be in time order");
            }
            times.add(seconds);
            values.add(value);
            eases.add(ease);
            return this;
        }
    }

    private static final class Track<T, V> {
        final T target;
        final Property<T, V> property;
        final float[] times;
        final List<V> values;
        final Interpolation[] eases;

        Track(T target, Property<T, V> property, Keys<V> keys) {
            this.target = target;
            this.property = property;
            int n = keys.times.size();
            times = new float[n];
            for (int i = 0; i < n; i++) {
                times[i] = keys.times.get(i);
            }
            values = List.copyOf(keys.values);
            eases = keys.eases.toArray(new Interpolation[0]);
        }

        float end() {
            return times.length == 0 ? 0f : times[times.length - 1];
        }

        void apply(float time) {
            int n = times.length;
            if (n == 0) {
                return;
            }
            if (time <= times[0]) {
                property.set(target, values.get(0));
                return;
            }
            if (time >= times[n - 1]) {
                property.set(target, values.get(n - 1));
                return;
            }
            int i = 1;
            while (times[i] < time) {
                i++;
            }
            float span = times[i] - times[i - 1];
            float f = span <= 0f ? 1f : (time - times[i - 1]) / span;
            property.set(
                    target, property.interpolator().interpolate(values.get(i - 1), values.get(i), eases[i].apply(f)));
        }
    }

    /** Builds a {@link Timeline}. */
    public static final class Builder {
        private final List<Track<?, ?>> tracks = new ArrayList<>();
        private final List<Float> eventTimes = new ArrayList<>();
        private final List<Runnable> events = new ArrayList<>();
        private float length;
        private boolean loop;

        private Builder() {}

        /**
         * Adds a track of keyframes for one property.
         *
         * @param target what to change
         * @param property the property
         * @param keys adds the keys
         * @param <T> target type
         * @param <V> value type
         * @return this builder
         */
        public <T, V> Builder track(T target, Property<T, V> property, Consumer<Keys<V>> keys) {
            Keys<V> added = new Keys<>();
            keys.accept(added);
            tracks.add(new Track<>(target, property, added));
            return this;
        }

        /**
         * Runs code when playback passes a point, in either direction.
         *
         * @param seconds when
         * @param action the code
         * @return this builder
         */
        public Builder at(float seconds, Runnable action) {
            eventTimes.add(seconds);
            events.add(action);
            return this;
        }

        /**
         * Sets the length; by default it ends at the last key or event.
         *
         * @param seconds the length
         * @return this builder
         */
        public Builder length(float seconds) {
            length = seconds;
            return this;
        }

        /**
         * Makes it start over at the end.
         *
         * @param value whether to loop
         * @return this builder
         */
        public Builder loop(boolean value) {
            loop = value;
            return this;
        }

        /**
         * Finishes the timeline.
         *
         * @return the timeline
         */
        public Timeline build() {
            return new Timeline(this);
        }
    }
}
