package dev.gulp.api.anim;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Typed access to one value of an object, without reflection, so tweens and timelines can animate it. The built-in
 * ones are in {@link Props}.
 *
 * <pre>{@code
 * Property<Enemy, Float> speed = Property.of(Enemy::speed, Enemy::setSpeed, Interpolators.FLOAT);
 * Tweens.to(slime, speed, 0f, 1f).start();
 * }</pre>
 *
 * @param <T> the owner type
 * @param <V> the value type
 */
public interface Property<T, V> {

    /**
     * Reads the value.
     *
     * @param target the object
     * @return the value
     */
    V get(T target);

    /**
     * Writes the value.
     *
     * @param target the object
     * @param value the value
     */
    void set(T target, V value);

    /**
     * Returns how values of this property blend.
     *
     * @return the interpolator
     */
    Interpolator<V> interpolator();

    /**
     * Creates a property from a getter and a setter.
     *
     * @param getter reads the value
     * @param setter writes the value
     * @param interpolator blends values
     * @param <T> the owner type
     * @param <V> the value type
     * @return the property
     */
    static <T, V> Property<T, V> of(Function<T, V> getter, BiConsumer<T, V> setter, Interpolator<V> interpolator) {
        return new Property<>() {
            @Override
            public V get(T target) {
                return getter.apply(target);
            }

            @Override
            public void set(T target, V value) {
                setter.accept(target, value);
            }

            @Override
            public Interpolator<V> interpolator() {
                return interpolator;
            }
        };
    }
}
