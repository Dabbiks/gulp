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
        HttpResult result = responses.getOrDefault(url, new HttpResult(404, Map.of(), ByteBuffer.allocate(0)));
        mainQueue.accept(() -> callback.success(result));
    }

    @Override
    public FakeWebSocket openWebSocket(String url, WebSocketListener listener) {
        FakeWebSocket socket = new FakeWebSocket(listener);
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
