package dev.gulp.api.input;

import dev.gulp.api.scheduler.Promise;

/**
 * The system clipboard. Reading is asynchronous because browsers ask the player for permission.
 *
 * <pre>{@code
 * input().clipboard().set(seedCode);
 * input().clipboard().get().thenSync(text -> field.setText(text));
 * }</pre>
 */
public interface Clipboard {

    /**
     * Reads the clipboard text.
     *
     * @return the text, empty if there is none or reading is not allowed
     */
    Promise<String> get();

    /**
     * Puts text in the clipboard.
     *
     * @param text the text
     */
    void set(String text);
}
