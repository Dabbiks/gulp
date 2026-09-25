package dev.gulp.api.entity.component;

import dev.gulp.api.entity.Component;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.spi.UiAccess;
import dev.gulp.api.ui.Node;

/**
 * Pins a UI node to an entity, such as a health bar above an enemy: the node is drawn in screen space at the entity's
 * interpolated position through the active world's camera, hides when the entity is off screen, and optionally scales
 * with the camera zoom. The node's bottom centre sits at the anchor point. It is drawn below the HUD and takes the
 * pointer like other UI.
 *
 * <pre>{@code
 * EntityType.builder(key("slime"))
 *         .component(() -> new WorldUi(progressBar(0.5f).size(24, 4)).offset(0f, -0.6f))
 *         .build();
 * }</pre>
 */
public final class WorldUi extends Component {

    private final Node<?> node;
    private Vec2 offset = new Vec2(0f, -0.6f);
    private boolean scaleWithZoom;

    /**
     * Pins a node.
     *
     * @param node the node
     */
    public WorldUi(Node<?> node) {
        this.node = node;
    }

    /**
     * Returns the node.
     *
     * @return the pinned node
     */
    public Node<?> node() {
        return node;
    }

    /**
     * Moves the anchor point relative to the entity's position.
     *
     * @param dx world units right
     * @param dy world units down (negative is above)
     * @return this component
     */
    public WorldUi offset(float dx, float dy) {
        this.offset = new Vec2(dx, dy);
        return this;
    }

    /**
     * Returns the anchor offset.
     *
     * @return world units
     */
    public Vec2 offset() {
        return offset;
    }

    /**
     * Scales the node with the camera zoom.
     *
     * @param value whether to scale
     * @return this component
     */
    public WorldUi scaleWithZoom(boolean value) {
        this.scaleWithZoom = value;
        return this;
    }

    /**
     * Returns whether the node scales with the camera zoom.
     *
     * @return {@code false} by default
     */
    public boolean scalesWithZoom() {
        return scaleWithZoom;
    }

    @Override
    protected void onSpawn() {
        if (UiAccess.hasBackend()) {
            UiAccess.backend().worldUi(this, true);
        }
    }

    @Override
    protected void onRemove() {
        if (UiAccess.hasBackend()) {
            UiAccess.backend().worldUi(this, false);
        }
    }
}
