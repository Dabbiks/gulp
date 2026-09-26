package dev.gulp.core.log;

import dev.gulp.api.asset.LoadingScreen;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Display;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.TextAlign;
import dev.gulp.api.text.TextStyle;

/**
 * The readable error screen after a fatal error, drawn in place of the game until the window closes.
 *
 * <pre>{@code
 * renderer.showLoading(new CrashScreen(error.toString(), "crash-reports/crash-2026-09-25_14-03-07.txt"), 0f);
 * }</pre>
 */
public final class CrashScreen implements LoadingScreen {

    private static final Color BACKGROUND = Color.rgba(0x1b1d24ff);
    private static final Color ACCENT = Color.rgba(0xef5350ff);
    private static final Color TEXT = Color.rgba(0xe0e0e0ff);

    private final String message;
    private final String location;

    /**
     * Creates the screen.
     *
     * @param message what failed
     * @param location where the report is, or an empty string
     */
    public CrashScreen(String message, String location) {
        this.message = message.length() > 300 ? message.substring(0, 300) + "…" : message;
        this.location = location;
    }

    @Override
    public void draw(Draw draw, Display display, float progress) {
        float width = display.width();
        float height = display.height();
        draw.color(BACKGROUND).rect(0f, 0f, width, height);
        draw.color(ACCENT).rect(0f, height * 0.3f - 4f, width, 4f);
        float x = width / 2f;
        float y = height * 0.3f + 24f;
        draw.color(Color.WHITE);
        draw.text("The game crashed", x, y, TextStyle.of(28).color(Color.WHITE), TextAlign.CENTER);
        TextStyle body = TextStyle.of(16).color(TEXT);
        draw.text(message, x, y + 48f, body, TextAlign.CENTER);
        if (!location.isEmpty()) {
            draw.text("Crash report: " + location, x, y + 80f, body, TextAlign.CENTER);
        }
        draw.text("Close the window to exit.", x, y + 112f, body, TextAlign.CENTER);
    }
}
