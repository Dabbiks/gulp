package dev.gulp.api.net;

import dev.gulp.api.scheduler.Promise;

/**
 * HTTP and WebSockets ({@code http()}); every callback runs on the main thread. On the web the browser's rules apply:
 * the server must allow the game's origin through CORS headers ({@code Access-Control-Allow-Origin}), otherwise the
 * request fails as a network error; mixed content (an {@code http://} URL from an {@code https://} page) is blocked.
 * Plain TCP sockets are not offered because browsers have none.
 *
 * <pre>{@code
 * http().get("https://example.com/news.json").thenSync(response -> {
 *     if (response.isOk()) showNews(response.json());
 * });
 * http().post("https://example.com/scores", "{\"score\":1200}", "application/json");
 * WebSocket chat = http().connect("wss://example.com/chat")
 *         .onMessage(text -> log.add(text))
 *         .onOpen(() -> chat.send("hello"));
 * }</pre>
 */
public interface Http {

    /**
     * Sends a GET request.
     *
     * @param url absolute URL
     * @return the response for any status; fails on network errors and timeouts
     */
    Promise<HttpResponse> get(String url);

    /**
     * Sends a POST request with a text body.
     *
     * @param url absolute URL
     * @param body the body
     * @param contentType its media type, for example {@code application/json}
     * @return the response for any status; fails on network errors and timeouts
     */
    Promise<HttpResponse> post(String url, String body, String contentType);

    /**
     * Sends a request.
     *
     * @param request the request
     * @return the response for any status; fails on network errors and timeouts
     */
    Promise<HttpResponse> request(HttpRequest request);

    /**
     * Opens a WebSocket connection; set handlers at once, events start after this call returns.
     *
     * @param url {@code ws://} or {@code wss://} URL
     * @return the connection
     */
    WebSocket connect(String url);
}
