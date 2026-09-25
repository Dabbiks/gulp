package dev.gulp.api.entity.component;

import dev.gulp.api.entity.Component;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Light;

/**
 * Carries a {@link Light} with its entity: the light is added to the world's lighting when the entity spawns, follows
 * it every tick and is removed with it.
 *
 * <pre>{@code
 * EntityType.builder(key("torch"))
 *         .component(() -> new LightSource(Light.point(Color.rgb(0xffb347), 5f)).offset(0f, -0.3f))
 *         .build();
 * }</pre>
 */
public final class LightSource extends Component {

    private final Light light;
    private Vec2 offset = Vec2.ZERO;

    /**
     * Creates the component.
     *
     * @param light the light it carries
     */
    public LightSource(Light light) {
        this.light = light;
    }

    /**
     * Returns the light.
     *
     * @return the light
     */
    public Light light() {
        return light;
    }

    /**
     * Places the light relative to the entity.
     *
     * @param x world units
     * @param y world units
     * @return this component
     */
    public LightSource offset(float x, float y) {
        offset = new Vec2(x, y);
        return this;
    }

    /**
     * Returns the offset from the entity.
     *
     * @return world units
     */
    public Vec2 offset() {
        return offset;
    }

    @Override
    protected void onSpawn() {
        follow();
        world().lighting().add(light);
    }

    @Override
    protected void onTick() {
        follow();
    }

    @Override
    protected void onRemove() {
        if (isAttached()) {
            world().lighting().remove(light);
        }
    }

    private void follow() {
        light.setPosition(entity().position().add(offset));
    }
}
