package dev.gulp.api.render;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.FrameBuffer;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Mesh2D;
import dev.gulp.api.graphics.NinePatch;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Polygon;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Transform2D;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextAlign;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextStyle;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Immediate-mode drawing, the same in the world (world units, through the camera), on screen layers and in overlays
 * (logical screen units). One batcher collects everything and flushes only when the texture, material, clip or target
 * changes. Shapes are anti-aliased with a one-pixel soft edge. Text arrives in stage 4.
 *
 * <pre>{@code
 * on(RenderLayerEvent.class, e -> {
 *     if (!e.layer().name().equals("entities")) return;
 *     Draw d = e.draw();
 *     d.image(player, x, y, 1, 1);                          // one tile
 *     d.push().translate(x, y).rotate(angle).color(Color.RED);
 *     d.circle(0, 0, 0.5f);
 *     d.pop();
 *     d.clip(Rect.of(0, 0, 10, 5), () -> d.line(0, 0, 20, 10, 0.1f));
 * });
 * }</pre>
 */
public interface Draw {

    // ------------------------------------------------------------ images

    /**
     * Draws a region at its pixel size in the current units' pixel scale: one texture pixel per display pixel on
     * screen layers, one texture pixel per world pixel ({@code 1 / pixelsPerUnit}) in the world.
     *
     * @param region the image
     * @param x left edge
     * @param y top edge
     * @return this
     */
    Draw image(TextureRegion region, float x, float y);

    /**
     * Draws a region stretched to a rectangle.
     *
     * @param region the image
     * @param x left edge
     * @param y top edge
     * @param width width
     * @param height height
     * @return this
     */
    Draw image(TextureRegion region, float x, float y, float width, float height);

    /**
     * Draws a region rotated and scaled around an origin.
     *
     * @param region the image
     * @param x left edge before rotation
     * @param y top edge before rotation
     * @param width width
     * @param height height
     * @param originX rotation origin, relative to the left edge
     * @param originY rotation origin, relative to the top edge
     * @param degrees rotation
     * @return this
     */
    Draw image(
            TextureRegion region,
            float x,
            float y,
            float width,
            float height,
            float originX,
            float originY,
            float degrees);

    /**
     * Draws a region as a unit square ({@code 0..1} on both axes) placed by a transform.
     *
     * @param region the image
     * @param transform position, rotation, scale and skew
     * @return this
     */
    Draw image(TextureRegion region, Transform2D transform);

    /**
     * Draws a nine-patch stretched to a rectangle; borders keep their pixel size in the current units' pixel scale.
     *
     * @param patch the nine-patch
     * @param rect the target
     * @return this
     */
    Draw ninePatch(NinePatch patch, Rect rect);

    /**
     * Fills a rectangle with copies of a region at its pixel size, cut at the edges.
     *
     * @param region the tile image
     * @param rect the area
     * @return this
     */
    Draw tiled(TextureRegion region, Rect rect);

    /**
     * Draws custom triangles.
     *
     * @param mesh the mesh, in current units
     * @param texture the texture, or {@code null} for solid colors
     * @return this
     */
    Draw mesh(Mesh2D mesh, @Nullable Texture texture);

    // ------------------------------------------------------------ shapes

    /**
     * Fills a rectangle.
     *
     * @param x left edge
     * @param y top edge
     * @param width width
     * @param height height
     * @return this
     */
    Draw rect(float x, float y, float width, float height);

    /**
     * Fills a rectangle.
     *
     * @param rect the rectangle
     * @return this
     */
    Draw rect(Rect rect);

    /**
     * Draws a rectangle outline inside its bounds.
     *
     * @param rect the rectangle
     * @param thickness line thickness
     * @return this
     */
    Draw rectOutline(Rect rect, float thickness);

    /**
     * Fills a rectangle with rounded corners.
     *
     * @param rect the rectangle
     * @param radius corner radius, limited to half the smaller side
     * @return this
     */
    Draw roundedRect(Rect rect, float radius);

    /**
     * Fills a rectangle with a vertical gradient.
     *
     * @param rect the rectangle
     * @param top color at the top edge
     * @param bottom color at the bottom edge
     * @return this
     */
    Draw gradientRect(Rect rect, Color top, Color bottom);

    /**
     * Fills a circle.
     *
     * @param cx center X
     * @param cy center Y
     * @param radius radius
     * @return this
     */
    Draw circle(float cx, float cy, float radius);

    /**
     * Draws a circle outline.
     *
     * @param cx center X
     * @param cy center Y
     * @param radius radius of the middle of the line
     * @param thickness line thickness
     * @return this
     */
    Draw circleOutline(float cx, float cy, float radius, float thickness);

    /**
     * Fills an ellipse.
     *
     * @param cx center X
     * @param cy center Y
     * @param radiusX horizontal radius
     * @param radiusY vertical radius
     * @return this
     */
    Draw ellipse(float cx, float cy, float radiusX, float radiusY);

    /**
     * Fills a circular sector (pie slice).
     *
     * @param cx center X
     * @param cy center Y
     * @param radius radius
     * @param startDegrees start angle, from +X towards +Y
     * @param sweepDegrees angle covered; negative goes the other way
     * @return this
     */
    Draw arc(float cx, float cy, float radius, float startDegrees, float sweepDegrees);

    /**
     * Draws a line.
     *
     * @param x1 start X
     * @param y1 start Y
     * @param x2 end X
     * @param y2 end Y
     * @param width line width
     * @return this
     */
    Draw line(float x1, float y1, float x2, float y2, float width);

    /**
     * Draws a line.
     *
     * @param a start
     * @param b end
     * @param width line width
     * @return this
     */
    Draw line(Vec2 a, Vec2 b, float width);

    /**
     * Draws connected lines.
     *
     * @param points the points, at least two
     * @param width line width
     * @param closed whether to connect the last point back to the first
     * @return this
     */
    Draw polyline(List<Vec2> points, float width, boolean closed);

    /**
     * Fills a polygon, convex or not.
     *
     * @param polygon the polygon
     * @return this
     */
    Draw polygon(Polygon polygon);

    /**
     * Fills a triangle.
     *
     * @param x1 first X
     * @param y1 first Y
     * @param x2 second X
     * @param y2 second Y
     * @param x3 third X
     * @param y3 third Y
     * @return this
     */
    Draw triangle(float x1, float y1, float x2, float y2, float x3, float y3);

    // ------------------------------------------------------------ state

    /**
     * Saves transform, color, alpha and material; restore with {@link #pop()}.
     *
     * @return this
     */
    Draw push();

    /**
     * Restores the state saved by the matching {@link #push()}.
     *
     * @return this
     * @throws IllegalStateException without a matching push
     */
    Draw pop();

    /**
     * Moves the origin.
     *
     * @param x offset X
     * @param y offset Y
     * @return this
     */
    Draw translate(float x, float y);

    /**
     * Rotates around the origin.
     *
     * @param degrees the angle, positive towards +Y (clockwise on screen)
     * @return this
     */
    Draw rotate(float degrees);

    /**
     * Scales around the origin.
     *
     * @param sx scale X
     * @param sy scale Y
     * @return this
     */
    Draw scale(float sx, float sy);

    /**
     * Sets the tint of images and the fill of shapes.
     *
     * @param color the color
     * @return this
     */
    Draw color(Color color);

    /**
     * Current color.
     *
     * @return the color
     */
    Color color();

    /**
     * Multiplies everything drawn by an opacity.
     *
     * @param alpha {@code 0..1}
     * @return this
     */
    Draw alpha(float alpha);

    /**
     * Sets the material (shader, blending, extra textures).
     *
     * @param material the material
     * @return this
     */
    Draw material(Material material);

    /**
     * Draws only inside a rectangle while the body runs. Nested clips intersect.
     *
     * @param rect the clip area in current units (axis-aligned bounds are used under rotation)
     * @param body the drawing code
     * @return this
     */
    Draw clip(Rect rect, Runnable body);

    /**
     * Draws into a frame buffer while the body runs; its coordinates are pixels with the origin at the top-left.
     *
     * @param target the frame buffer; cleared to transparent first
     * @param body the drawing code, receiving this draw
     * @return this
     */
    Draw into(FrameBuffer target, Consumer<Draw> body);

    /**
     * Size of one display pixel in current units, for pixel-exact lines.
     *
     * @return current units per pixel
     */
    float pixelSize();

    /**
     * Draws text in the default style with its top-left corner at a point.
     *
     * @param text the characters; {@code \n} starts a new line
     * @param x left edge
     * @param y top edge
     * @return this draw
     */
    Draw text(String text, float x, float y);

    /**
     * Draws text with its top-left corner at a point.
     *
     * @param text the characters
     * @param x left edge
     * @param y top edge
     * @param style the style
     * @return this draw
     */
    Draw text(String text, float x, float y, TextStyle style);

    /**
     * Draws rich text with its top-left corner at a point.
     *
     * @param text the text
     * @param x left edge
     * @param y top edge
     * @param style the base style
     * @return this draw
     */
    Draw text(Text text, float x, float y, TextStyle style);

    /**
     * Draws text aligned to a point: {@link TextAlign#CENTER} centers it on the point, {@link TextAlign#TOP_RIGHT}
     * puts its top-right corner there.
     *
     * @param text the characters
     * @param x the point
     * @param y the point
     * @param style the style
     * @param align which part of the text sits on the point
     * @return this draw
     */
    Draw text(String text, float x, float y, TextStyle style, TextAlign align);

    /**
     * Draws rich text wrapped by words to the width of a rectangle and aligned inside it.
     *
     * @param text the text
     * @param rect the rectangle
     * @param style the base style
     * @param align alignment of the lines and of the block inside the rectangle
     * @return this draw
     */
    Draw text(Text text, Rect rect, TextStyle style, TextAlign align);

    /**
     * Draws a prepared layout with its top-left corner at a point.
     *
     * @param layout the layout
     * @param x left edge
     * @param y top edge
     * @return this draw
     */
    Draw text(TextLayout layout, float x, float y);

    /**
     * Draws the first characters of a prepared layout, for typewriter effects.
     *
     * @param layout the layout
     * @param x left edge
     * @param y top edge
     * @param visibleCharacters how many characters and images to draw
     * @return this draw
     */
    Draw text(TextLayout layout, float x, float y, int visibleCharacters);
}
