package dev.gulp.platform;

import java.nio.ByteBuffer;

/**
 * Receives WebSocket events on the main thread.
 *
 * <pre>{@code
 * net.openWebSocket("wss://example.com/live", listener);
 * }</pre>
 */
public interface WebSocketListener {

    /** The connection is open and can send. */
    void opened();

    /**
     * A text message arrived.
     *
     * @param text the message
     */
    void textMessage(String text);

    /**
     * A binary message arrived.
     *
     * @param data the message
     */
    void binaryMessage(ByteBuffer data);

    /**
     * The connection closed.
     *
     * @param code close code
     * @param reason close reason, possibly empty
     */
    void closed(int code, String reason);

    /**
     * The connection failed.
     *
     * @param error what went wrong
     */
    void failed(Throwable error);
}
