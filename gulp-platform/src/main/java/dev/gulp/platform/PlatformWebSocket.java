package dev.gulp.platform;

import java.nio.ByteBuffer;

/**
 * An open WebSocket connection.
 *
 * <pre>{@code
 * PlatformWebSocket socket = net.openWebSocket(url, listener);
 * socket.send("hello");
 * }</pre>
 */
public interface PlatformWebSocket {

    /**
     * Sends a text message.
     *
     * @param text the message
     */
    void send(String text);

    /**
     * Sends a binary message.
     *
     * @param data bytes from position to limit
     */
    void send(ByteBuffer data);

    /**
     * Closes the connection.
     *
     * @param code close code, usually 1000
     * @param reason close reason
     */
    void close(int code, String reason);
}
