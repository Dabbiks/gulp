package dev.gulp.backend.headless;

import dev.gulp.platform.HttpResult;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformNet;
import dev.gulp.platform.PlatformWebSocket;
import dev.gulp.platform.WebSocketListener;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Network fakes. HTTP answers with responses registered by the test (404 otherwise); WebSockets open on the next frame
 * and record what the game sends; opened URLs are recorded instead of launching a browser.
 *
 * <pre>{@code
 * backend.net().respond("https://example.com/scores", new HttpResult(200, Map.of(), body));
 * }</pre>
 */
public final class HeadlessNet implements PlatformNet {

    private final Consumer<Runnable> mainQueue;
    private final Map<String, HttpResult> responses = new HashMap<>();
    private final List<String> requests = new ArrayList<>();
    private final List<String> openedUrls = new ArrayList<>();
    private final Map<String, String> failures = new HashMap<>();
    private final java.util.Set<String> hanging = new java.util.HashSet<>();
    private final List<FakeWebSocket> sockets = new ArrayList<>();
    private final List<String> refusedSockets = new ArrayList<>();

    HeadlessNet(Consumer<Runnable> mainQueue) {
        this.mainQueue = mainQueue;
    }

    /**
     * Registers the response for a URL, for any method.
     *
     * @param url the exact URL
     * @param result the response
     */
    public void respond(String url, HttpResult result) {
        responses.put(url, result);
    }

    /**
     * Makes requests to a URL fail as a network error would, such as a refused connection or a CORS block.
     *
     * <pre>{@code
     * backend.net().fail("https://example.com/down", "Connection refused");
     * }</pre>
     *
     * @param url the exact URL
     * @param message the error message
     */
    public void fail(String url, String message) {
        failures.put(url, message);
    }

    /**
     * Makes requests to a URL never answer, to test timeouts.
     *
     * <pre>{@code
     * backend.net().hang("https://example.com/slow");
     * }</pre>
     *
     * @param url the exact URL
     */
    public void hang(String url) {
        hanging.add(url);
    }

    /**
     * Makes WebSocket connections to a URL fail instead of opening.
     *
     * <pre>{@code
     * backend.net().refuseSocket("wss://example.com/chat");
     * }</pre>
     *
     * @param url the exact URL
     */
    public void refuseSocket(String url) {
        refusedSockets.add(url);
    }

    /**
     * Returns the WebSockets opened so far.
     *
     * <pre>{@code
     * HeadlessNet.FakeWebSocket socket = backend.net().sockets().getFirst();
     * }</pre>
     *
     * @return the sockets in opening order
     */
    public List<FakeWebSocket> sockets() {
        return List.copyOf(sockets);
    }

    /**
     * Returns the requests made so far as {@code "METHOD url"}.
     *
     * @return a copy of the request log
     */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    /**
     * Returns the URLs passed to {@link #openUrl(String)}.
     *
     * @return a copy of the list
     */
    public List<String> openedUrls() {
        return List.copyOf(openedUrls);
    }

    @Override
    public void http(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable ByteBuffer body,
            PlatformCallback<HttpResult> callback) {
        requests.add(method + " " + url);
        if (hanging.contains(url)) {
            return;
        }
        String failure = failures.get(url);
        if (failure != null) {
            mainQueue.accept(() -> callback.failure(new java.io.IOException(failure)));
            return;
        }
        HttpResult result = responses.getOrDefault(url, new HttpResult(404, Map.of(), ByteBuffer.allocate(0)));
        mainQueue.accept(() -> callback.success(result));
    }

    @Override
    public FakeWebSocket openWebSocket(String url, WebSocketListener listener) {
        FakeWebSocket socket = new FakeWebSocket(listener);
        sockets.add(socket);
        if (refusedSockets.contains(url)) {
            socket.closed = true;
            mainQueue.accept(() -> listener.failed(new java.io.IOException("Connection refused: " + url)));
            return socket;
        }
        mainQueue.accept(listener::opened);
        return socket;
    }

    @Override
    public void openUrl(String url) {
        openedUrls.add(url);
    }

    /**
     * WebSocket that records sent messages; tests push incoming messages with {@link #receive(String)}.
     *
     * <pre>{@code
     * socket.receive("{\"type\":\"hello\"}");
     * assertThat(socket.sentText()).containsExactly("ping");
     * }</pre>
     */
    public final class FakeWebSocket implements PlatformWebSocket {

        private final WebSocketListener listener;
        private final List<String> sentText = new ArrayList<>();
        private int sentBinaryCount;
        private boolean closed;

        private FakeWebSocket(WebSocketListener listener) {
            this.listener = listener;
        }

        /**
         * Returns the text messages sent by the game.
         *
         * @return a copy of the list
         */
        public List<String> sentText() {
            return List.copyOf(sentText);
        }

        /**
         * Returns how many binary messages the game sent.
         *
         * @return the count
         */
        public int sentBinaryCount() {
            return sentBinaryCount;
        }

        /**
         * Delivers a text message to the game before the next frame.
         *
         * @param text the message
         */
        public void receive(String text) {
            mainQueue.accept(() -> listener.textMessage(text));
        }

        /**
         * Delivers a binary message from the fake server.
         *
         * <pre>{@code
         * socket.receive(new byte[] {1, 2, 3});
         * }</pre>
         *
         * @param data the message
         */
        public void receive(byte[] data) {
            ByteBuffer buffer = ByteBuffer.wrap(data.clone());
            mainQueue.accept(() -> listener.binaryMessage(buffer));
        }

        /**
         * Closes the connection from the server side.
         *
         * <pre>{@code
         * socket.serverClose(1001, "going away");
         * }</pre>
         *
         * @param code the close code
         * @param reason the reason
         */
        public void serverClose(int code, String reason) {
            close(code, reason);
        }

        /**
         * Returns whether the socket is closed.
         *
         * <pre>{@code
         * assertTrue(socket.isClosed());
         * }</pre>
         *
         * @return {@code true} once closed by either side
         */
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void send(String text) {
            requireOpen();
            sentText.add(text);
        }

        @Override
        public void send(ByteBuffer data) {
            requireOpen();
            sentBinaryCount++;
        }

        @Override
        public void close(int code, String reason) {
            if (!closed) {
                closed = true;
                mainQueue.accept(() -> listener.closed(code, reason));
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("WebSocket is closed");
            }
        }
    }
}
