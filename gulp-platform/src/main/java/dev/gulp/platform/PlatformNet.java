package dev.gulp.platform;

import java.nio.ByteBuffer;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * HTTP and WebSocket access ({@code java.net.http} on desktop, {@code fetch} and {@code WebSocket} on the web).
 *
 * <pre>{@code
 * net.http("GET", "https://example.com/scores.json", Map.of(), null, callback);
 * }</pre>
 */
public interface PlatformNet {

    /**
     * Sends an HTTP request.
     *
     * @param method for example {@code "GET"} or {@code "POST"}
     * @param url absolute URL
     * @param headers request headers
     * @param body request body, or {@code null} for none
     * @param callback receives the response for any status code, fails only on network errors
     */
    void http(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable ByteBuffer body,
            PlatformCallback<HttpResult> callback);

    /**
     * Opens a WebSocket connection.
     *
     * @param url {@code ws://} or {@code wss://} URL
     * @param listener receives events
     * @return the connection, usable after {@link WebSocketListener#opened()}
     */
    PlatformWebSocket openWebSocket(String url, WebSocketListener listener);

    /**
     * Opens a URL in the system browser, or a new tab on the web.
     *
     * @param url the URL
     */
    void openUrl(String url);
}
