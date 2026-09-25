package dev.gulp.api.anim;

import dev.gulp.api.audio.Bus;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Camera;
import dev.gulp.api.render.Light;

/**
 * Built-in animatable properties. Post-processing effects keep theirs as constants of their own classes, such as
 * {@code Bloom.INTENSITY}; properties of UI nodes arrive with the UI.
 *
 * <pre>{@code
 * Tweens.to(player, Props.ALPHA, 0f, 0.3f).start();
 * Tweens.to(world.camera(), Props.CAMERA_ZOOM, 2f, 1f).ease(Ease.IN_OUT_SINE).start();
 * Tweens.to(torch.get(LightSource.class).light(), Props.LIGHT_RADIUS, 8f, 0.5f).start();
 * }</pre>
 */
public final class Props {

    /** Entity position. */
    public static final Property<Entity, Vec2> POSITION =
            Property.of(Entity::position, Entity::setPosition, Interpolators.VEC2);

    /** Entity x. */
    public static final Property<Entity, Float> X = Property.of(
            e -> e.position().x(), (e, x) -> e.setPosition(x, e.position().y()), Interpolators.FLOAT);

    /** Entity y. */
    public static final Property<Entity, Float> Y = Property.of(
            e -> e.position().y(), (e, y) -> e.setPosition(e.position().x(), y), Interpolators.FLOAT);

    /** Entity rotation in degrees, turning the shorter way. */
    public static final Property<Entity, Float> ROTATION =
            Property.of(Entity::rotation, Entity::setRotation, Interpolators.ANGLE);

    /** Entity scale. */
    public static final Property<Entity, Vec2> SCALE =
            Property.of(Entity::scale, (e, s) -> e.setScale(s.x(), s.y()), Interpolators.VEC2);

    /** Entity opacity: the alpha of its tint. */
    public static final Property<Entity, Float> ALPHA =
            Property.of(e -> e.tint().a(), (e, a) -> e.setTint(e.tint().withAlpha(a)), Interpolators.FLOAT);

    /** Entity tint. */
    public static final Property<Entity, Color> TINT = Property.of(Entity::tint, Entity::setTint, Interpolators.COLOR);

    /**
     * Where the sprite of an entity is drawn relative to it; moves the picture without moving the entity (used by
     * {@code bob} and {@code shake}).
     */
    public static final Property<Entity, Vec2> SPRITE_OFFSET = Property.of(
            e -> e.has(SpriteComponent.class) ? e.get(SpriteComponent.class).offset() : Vec2.ZERO,
            (e, v) -> {
                if (e.has(SpriteComponent.class)) {
                    e.get(SpriteComponent.class).setOffset(v.x(), v.y());
                }
            },
            Interpolators.VEC2);

    /** The per-sprite parameter of the sprite material, such as the colour and amount of {@code Materials.FLASH}. */
    public static final Property<Entity, Color> EFFECT = Property.of(
            e -> e.has(SpriteComponent.class) ? e.get(SpriteComponent.class).effect() : Color.CLEAR,
            (e, c) -> {
                if (e.has(SpriteComponent.class)) {
                    e.get(SpriteComponent.class).setEffect(c);
                }
            },
            Interpolators.COLOR);

    /** Camera position. */
    public static final Property<Camera, Vec2> CAMERA_POSITION =
            Property.of(Camera::position, Camera::setPosition, Interpolators.VEC2);

    /** Camera zoom. */
    public static final Property<Camera, Float> CAMERA_ZOOM =
            Property.of(Camera::zoom, Camera::setZoom, Interpolators.FLOAT);

    /** Camera rotation in degrees. */
    public static final Property<Camera, Float> CAMERA_ROTATION =
            Property.of(Camera::rotation, Camera::setRotation, Interpolators.ANGLE);

    /** Volume of a playing sound. */
    public static final Property<Playback, Float> VOLUME =
            Property.of(Playback::volume, Playback::setVolume, Interpolators.FLOAT);

    /** Volume of an audio bus. */
    public static final Property<Bus, Float> BUS_VOLUME = Property.of(Bus::volume, Bus::setVolume, Interpolators.FLOAT);

    /** Light intensity. */
    public static final Property<Light, Float> LIGHT_INTENSITY =
            Property.of(Light::intensity, Light::setIntensity, Interpolators.FLOAT);

    /** Light radius. */
    public static final Property<Light, Float> LIGHT_RADIUS =
            Property.of(Light::radius, Light::setRadius, Interpolators.FLOAT);

    /** Light colour. */
    public static final Property<Light, Color> LIGHT_COLOR =
            Property.of(Light::color, Light::setColor, Interpolators.COLOR);

    private Props() {}
}
