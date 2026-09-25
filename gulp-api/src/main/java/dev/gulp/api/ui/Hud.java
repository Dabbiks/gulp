package dev.gulp.api.ui;

import dev.gulp.api.Owner;
import java.util.List;

/**
 * The HUD: a screen-space layer below screens where modules put anchored nodes. Nodes belong to their owner and go away
 * when it is disabled. The engine adds nothing of its own; everything on the HUD is built from the same widgets.
 *
 * <pre>{@code
 * ui().hud().add(this, row(image(GameAssets.Sprites.COIN_ICON), label(coins.map(c -> tr("hud.coins", c))))
 *         .gap(4).anchor(Anchor.TOP_LEFT).offset(8, 8));
 * }</pre>
 */
public interface Hud {

    /**
     * Adds a node, placed by its anchor and offset over the whole screen.
     *
     * @param owner the owner; the node is removed when it is disabled
     * @param node the node
     * @param <N> the node type
     * @return the node
     */
    <N extends Node<?>> N add(Owner owner, N node);

    /**
     * Removes a node.
     *
     * @param node the node
     * @return {@code true} if it was on the HUD
     */
    boolean remove(Node<?> node);

    /**
     * Returns the nodes, bottom first.
     *
     * @return a copy
     */
    List<Node<?>> nodes();

    /**
     * Hides or shows the whole HUD, for example for cutscenes or screenshots.
     *
     * @param visible whether the HUD shows
     */
    void setVisible(boolean visible);

    /**
     * Returns whether the HUD shows.
     *
     * @return {@code true} by default
     */
    boolean isVisible();
}
