package dev.gulp.api.anim;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Materials;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Camera;
import dev.gulp.api.spi.AnimationAccess;
import org.jspecify.annotations.Nullable;

/**
 * Makes tweens: one line animates any {@link Property}. Nothing runs until {@link Tween#start()}.
 *
 * <pre>{@code
 * Tweens.to(door, Props.Y, door.position().y() - 2f, 0.6f).ease(Ease.OUT_BOUNCE).start();
 * Tweens.sequence(Tweens.flash(enemy, Color.WHITE, 0.1f), Tweens.wait(0.2f), Tweens.fadeOut(enemy, 0.3f))
 *         .onComplete(enemy::remove)
 *         .start();
 * Tweens.shake(world.camera(), 0.5f, 0.3f).start();
 * }</pre>
 */
public final class Tweens {

    private Tweens() {}

    /**
     * Animates a property from its value at the start to a value.
     *
     * @param target what to change
     * @param property the property
     * @param value the end value
     * @param seconds how long
     * @param <T> target type
     * @param <V> value type
     * @return the tween
     */
    public static <T, V> Tween to(T target, Property<T, V> property, V value, float seconds) {
        return new ValueTween<>(target, property, ValueTween.Mode.TO, value, null, seconds);
    }

    /**
     * Animates a property from a value back to its value at the start.
     *
     * @param target what to change
     * @param property the property
     * @param value the start value
     * @param seconds how long
     * @param <T> target type
     * @param <V> value type
     * @return the tween
     */
    public static <T, V> Tween from(T target, Property<T, V> property, V value, float seconds) {
        return new ValueTween<>(target, property, ValueTween.Mode.FROM, value, null, seconds);
    }

    /**
     * Animates a property by an amount, relative to its value at the start.
     *
     * @param target what to change
     * @param property the property; its interpolator must add values
     * @param delta the change
     * @param seconds how long
     * @param <T> target type
     * @param <V> value type
     * @return the tween
     */
    public static <T, V> Tween by(T target, Property<T, V> property, V delta, float seconds) {
        return new ValueTween<>(target, property, ValueTween.Mode.BY, delta, null, seconds);
    }

    /**
     * Animates a property between two given values.
     *
     * @param target what to change
     * @param property the property
     * @param from the start value
     * @param to the end value
     * @param seconds how long
     * @param <T> target type
     * @param <V> value type
     * @return the tween
     */
    public static <T, V> Tween fromTo(T target, Property<T, V> property, V from, V to, float seconds) {
        return new ValueTween<>(target, property, ValueTween.Mode.FROM_TO, from, to, seconds);
    }

    /**
     * Drives code with the eased time.
     *
     * @param target what the code changes, so the tween stops with it; or {@code null}
     * @param seconds how long
     * @param function receives the eased time
     * @return the tween
     */
    public static Tween custom(@Nullable Object target, float seconds, TweenFunction function) {
        return new CustomTween(target, seconds, function);
    }

    /**
     * Plays tweens one after another.
     *
     * @param tweens the tweens
     * @return the group
     */
    public static Tween sequence(Tween... tweens) {
        return GroupTween.sequence(tweens);
    }

    /**
     * Plays tweens together; the group ends with the longest.
     *
     * @param tweens the tweens
     * @return the group
     */
    public static Tween parallel(Tween... tweens) {
        return GroupTween.parallel(tweens);
    }

    /**
     * Waits, inside a sequence.
     *
     * @param seconds how long
     * @return the tween
     */
    public static Tween wait(float seconds) {
        return new DelayTween(seconds, null);
    }

    /**
     * Runs code, inside a sequence.
     *
     * @param action the code
     * @return the tween
     */
    public static Tween call(Runnable action) {
        return new DelayTween(0f, action);
    }

    /**
     * Kills every running tween and timeline that animates a target, leaving values where they are.
     *
     * @param target the target
     * @return how many were killed
     */
    public static int killAll(Object target) {
        return AnimationAccess.backend().killAll(target);
    }

    /**
     * Grows an entity for a moment and springs back.
     *
     * @param entity the entity
     * @param amount how much bigger, {@code 0.2} is 20%
     * @param seconds the whole punch
     * @return the tween
     */
    public static Tween punchScale(Entity entity, float amount, float seconds) {
        Vec2 base = entity.scale();
        return to(entity, Props.SCALE, base.scale(1f + amount), seconds * 0.5f)
                .ease(Ease.OUT_QUAD)
                .yoyo()
                .repeat(1);
    }

    /**
     * Flashes the sprite of an entity with a colour that fades out, through {@link Materials#FLASH}.
     *
     * @param entity the entity; needs a {@link SpriteComponent}
     * @param color the flash colour
     * @param seconds how long it fades
     * @return the tween
     */
    public static Tween flash(Entity entity, Color color, float seconds) {
        Material[] before = new Material[1];
        return sequence(
                call(() -> {
                    if (entity.has(SpriteComponent.class)) {
                        SpriteComponent sprite = entity.get(SpriteComponent.class);
                        before[0] = sprite.material() == Materials.FLASH ? Material.DEFAULT : sprite.material();
                        sprite.setMaterial(Materials.FLASH);
                    }
                }),
                fromTo(entity, Props.EFFECT, color.withAlpha(1f), color.withAlpha(0f), seconds)
                        .ease(Ease.LINEAR),
                call(() -> {
                    if (before[0] != null && entity.has(SpriteComponent.class)) {
                        entity.get(SpriteComponent.class).setMaterial(before[0]);
                    }
                }));
    }

    /**
     * Fades an entity in from invisible.
     *
     * @param entity the entity
     * @param seconds how long
     * @return the tween
     */
    public static Tween fadeIn(Entity entity, float seconds) {
        return fromTo(entity, Props.ALPHA, 0f, 1f, seconds).ease(Ease.LINEAR);
    }

    /**
     * Fades an entity out to invisible.
     *
     * @param entity the entity
     * @param seconds how long
     * @return the tween
     */
    public static Tween fadeOut(Entity entity, float seconds) {
        return to(entity, Props.ALPHA, 0f, seconds).ease(Ease.LINEAR);
    }

    /**
     * Makes an entity grow and shrink gently, forever.
     *
     * @param entity the entity
     * @param amount how much bigger at the peak, {@code 0.1} is 10%
     * @param seconds one full beat
     * @return the endless tween; kill it to stop
     */
    public static Tween pulse(Entity entity, float amount, float seconds) {
        Vec2 base = entity.scale();
        return to(entity, Props.SCALE, base.scale(1f + amount), seconds * 0.5f)
                .ease(Ease.IN_OUT_SINE)
                .yoyo()
                .repeat(-1);
    }

    /**
     * Makes the sprite of an entity float up and down, forever, without moving the entity.
     *
     * @param entity the entity; needs a {@link SpriteComponent}
     * @param height world units
     * @param seconds one full bob
     * @return the endless tween; kill it to stop
     */
    public static Tween bob(Entity entity, float height, float seconds) {
        Vec2 base = Props.SPRITE_OFFSET.get(entity);
        return to(entity, Props.SPRITE_OFFSET, base.add(0f, -height), seconds * 0.5f)
                .ease(Ease.IN_OUT_SINE)
                .yoyo()
                .repeat(-1);
    }

    /**
     * Shakes the sprite of an entity, weaker over time, without moving the entity.
     *
     * @param entity the entity; needs a {@link SpriteComponent}
     * @param strength world units at the start
     * @param seconds how long
     * @return the tween
     */
    public static Tween shake(Entity entity, float strength, float seconds) {
        Vec2 base = Props.SPRITE_OFFSET.get(entity);
        return custom(entity, seconds, t -> {
                    if (t >= 1f) {
                        Props.SPRITE_OFFSET.set(entity, base);
                        return;
                    }
                    float power = strength * (1f - t);
                    float dx = (float) (Math.random() * 2.0 - 1.0) * power;
                    float dy = (float) (Math.random() * 2.0 - 1.0) * power;
                    Props.SPRITE_OFFSET.set(entity, base.add(dx, dy));
                })
                .ease(Ease.LINEAR);
    }

    /**
     * Shakes a camera through its trauma shake.
     *
     * @param camera the camera
     * @param strength trauma, {@code 0..1}
     * @param seconds how long
     * @return the tween
     */
    public static Tween shake(Camera camera, float strength, float seconds) {
        return sequence(call(() -> camera.shake(strength, seconds)), wait(seconds));
    }
}
