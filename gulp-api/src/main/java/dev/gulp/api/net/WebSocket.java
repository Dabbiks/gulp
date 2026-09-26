package dev.gulp.api.net;

import java.util.function.Consumer;

/**
 * A WebSocket connection from {@code http().connect(url)}. Handlers run on the main thread; set them right after
 * opening. Messages sent before the connection opens are queued and sent on open.
 *
 * <pre>{@code
 * WebSocket socket = http().connect("wss://example.com/room/7")
 *         .onOpen(() -> logger().info("connected"))
 *         .onMessage(text -> chat.add(text))
 *         .onClose((code, reason) -> logger().info("closed " + code))
 *         .onError(error -> logger().warn("socket error: " + error));
 * socket.send("{\"type\":\"join\"}");
 * }</pre>
 */
public interface WebSocket {

    /** Receives the close code and reason. */
    @FunctionalInterface
    interface CloseHandler {
        /**
         * Handles the close.
         *
         * @param code the close code, 1000 for a normal close
         * @param reason the reason text, possibly empty
         */
        void closed(int code, String reason);
    }

    /**
     * Sends a text message.
     *
     * @param text the message
     */
    void send(String text);

    /**
     * Sends a binary message.
     *
     * @param bytes the message
     */
    void send(byte[] bytes);

    /**
     * Closes the connection with code 1000.
     */
    void close();

    /**
     * Returns whether the connection is open.
     *
     * @return {@code true} between open and close
     */
    boolean isOpen();

    /**
     * Sets the open handler.
     *
     * @param handler runs when connected
     * @return this socket
     */
    WebSocket onOpen(Runnable handler);

    /**
     * Sets the text message handler.
     *
     * @param handler receives each text message
     * @return this socket
     */
    WebSocket onMessage(Consumer<String> handler);

    /**
     * Sets the binary message handler.
     *
     * @param handler receives each binary message
     * @return this socket
     */
    WebSocket onBinary(Consumer<byte[]> handler);

    /**
     * Sets the close handler.
     *
     * @param handler receives the code and reason
     * @return this socket
     */
    WebSocket onClose(CloseHandler handler);

    /**
     * Sets the error handler.
     *
     * @param handler receives a description of the error
     * @return this socket
     */
    WebSocket onError(Consumer<String> handler);
}
