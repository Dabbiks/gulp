package dev.gulp.core.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs rectangles into pages with the skyline method: each page keeps the top outline of what was placed, and a new
 * rectangle goes where it ends lowest (ties: where it wastes least width). Used for atlases and font glyph pages.
 *
 * <pre>{@code
 * RectPacker packer = new RectPacker(1024, 1024, 2);
 * RectPacker.Placement at = packer.add(40, 30); // page, x, y
 * }</pre>
 */
public final class RectPacker {

    /**
     * Where a rectangle went.
     *
     * @param page page index
     * @param x left edge in pixels
     * @param y top edge in pixels
     */
    public record Placement(int page, int x, int y) {}

    private final int pageWidth;
    private final int pageHeight;
    private final int padding;
    private final List<List<int[]>> skylines = new ArrayList<>();
    private final List<int[]> used = new ArrayList<>();

    /**
     * Creates a packer.
     *
     * @param pageWidth page width in pixels
     * @param pageHeight page height in pixels
     * @param padding empty pixels kept around every rectangle
     */
    public RectPacker(int pageWidth, int pageHeight, int padding) {
        if (pageWidth < 1 || pageHeight < 1 || padding < 0) {
            throw new IllegalArgumentException("Invalid page " + pageWidth + "x" + pageHeight + ", padding " + padding);
        }
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.padding = padding;
    }

    /**
     * Places a rectangle, opening a new page if no page has room.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @return the placement
     * @throws IllegalArgumentException if the rectangle is larger than a page
     */
    public Placement add(int width, int height) {
        int w = width + padding * 2;
        int h = height + padding * 2;
        if (w > pageWidth || h > pageHeight) {
            throw new IllegalArgumentException(
                    width + "x" + height + " does not fit a " + pageWidth + "x" + pageHeight + " page");
        }
        for (int page = 0; page < skylines.size(); page++) {
            Placement placement = place(page, w, h);
            if (placement != null) {
                return placement;
            }
        }
        List<int[]> skyline = new ArrayList<>();
        skyline.add(new int[] {0, 0, pageWidth});
        skylines.add(skyline);
        used.add(new int[] {0, 0});
        Placement placement = place(skylines.size() - 1, w, h);
        if (placement == null) {
            throw new IllegalStateException("A fresh page could not take " + w + "x" + h);
        }
        return placement;
    }

    private Placement place(int page, int w, int h) {
        List<int[]> skyline = skylines.get(page);
        int bestIndex = -1;
        int bestY = Integer.MAX_VALUE;
        int bestWaste = Integer.MAX_VALUE;
        for (int i = 0; i < skyline.size(); i++) {
            int x = skyline.get(i)[0];
            if (x + w > pageWidth) {
                break;
            }
            int y = 0;
            int waste = 0;
            int covered = 0;
            for (int j = i; j < skyline.size() && covered < w; j++) {
                int[] segment = skyline.get(j);
                y = Math.max(y, segment[1]);
                covered = segment[0] + segment[2] - x;
            }
            if (y + h > pageHeight) {
                continue;
            }
            covered = 0;
            for (int j = i; j < skyline.size() && covered < w; j++) {
                int[] segment = skyline.get(j);
                int span = Math.min(segment[0] + segment[2], x + w) - Math.max(segment[0], x);
                waste += (y - segment[1]) * Math.max(0, span);
                covered = segment[0] + segment[2] - x;
            }
            if (y < bestY || (y == bestY && waste < bestWaste)) {
                bestIndex = i;
                bestY = y;
                bestWaste = waste;
            }
        }
        if (bestIndex < 0) {
            return null;
        }
        int x = skyline.get(bestIndex)[0];
        // Replace the covered segments with one raised segment.
        List<int[]> next = new ArrayList<>();
        for (int[] segment : skyline) {
            int start = segment[0];
            int end = segment[0] + segment[2];
            if (end <= x || start >= x + w) {
                next.add(segment);
                continue;
            }
            if (start < x) {
                next.add(new int[] {start, segment[1], x - start});
            }
            if (end > x + w) {
                next.add(new int[] {x + w, segment[1], end - x - w});
            }
        }
        next.add(new int[] {x, bestY + h, w});
        next.sort((a, b) -> Integer.compare(a[0], b[0]));
        // Merge neighbours at the same height.
        List<int[]> merged = new ArrayList<>();
        for (int[] segment : next) {
            int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last[1] == segment[1] && last[0] + last[2] == segment[0]) {
                last[2] += segment[2];
            } else {
                merged.add(segment);
            }
        }
        skylines.set(page, merged);
        int[] extent = used.get(page);
        extent[0] = Math.max(extent[0], x + w);
        extent[1] = Math.max(extent[1], bestY + h);
        return new Placement(page, x + padding, bestY + padding);
    }

    /**
     * Returns the number of pages in use.
     *
     * @return the page count
     */
    public int pageCount() {
        return skylines.size();
    }

    /**
     * Returns the area used on a page, for trimming the last page.
     *
     * @param page the page index
     * @return {@code {width, height}} in pixels
     */
    public int[] usedSize(int page) {
        return used.get(page).clone();
    }
}
