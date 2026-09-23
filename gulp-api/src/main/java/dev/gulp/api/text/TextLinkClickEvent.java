package dev.gulp.api.text;

import dev.gulp.api.event.Event;

/**
 * Fired when a player clicks a {@code [link=...]} in text shown by the UI (from stage 9).
 *
 * <pre>{@code
 * on(TextLinkClickEvent.class, e -> {
 *     if (e.link().equals("shop")) openShop();
 * });
 * }</pre>
 */
public final class TextLinkClickEvent extends Event {

    private final String link;

    /**
     * Creates the event; fired by the engine.
     *
     * @param link the link id
     */
    public TextLinkClickEvent(String link) {
        this.link = link;
    }

    /**
     * Returns the link id.
     *
     * @return the id
     */
    public String link() {
        return link;
    }
}
