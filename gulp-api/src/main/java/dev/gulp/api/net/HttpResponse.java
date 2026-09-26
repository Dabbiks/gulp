package dev.gulp.api.net;

import dev.gulp.api.data.Json;
import dev.gulp.api.data.JsonValue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An HTTP response.
 *
 * <pre>{@code
 * if (response.isOk()) {
 *     JsonValue scores = response.json();
 * } else {
 *     logger().warn("HTTP " + response.status() + ": " + response.text());
 * }
 * }</pre>
 */
public final class HttpResponse {

    private final int status;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    /**
     * Creates a response.
     *
     * @param status the status code
     * @param headers headers by lower-case name
     * @param body the body
     */
    public HttpResponse(int status, Map<String, List<String>> headers, byte[] body) {
        this.status = status;
        this.headers = Map.copyOf(headers);
        this.body = body.clone();
    }

    /**
     * Returns the status code.
     *
     * @return for example 200 or 404
     */
    public int status() {
        return status;
    }

    /**
     * Returns whether the status is 2xx.
     *
     * @return {@code true} for success
     */
    public boolean isOk() {
        return status >= 200 && status < 300;
    }

    /**
     * Returns the headers.
     *
     * @return values by lower-case name
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * Returns the first value of a header.
     *
     * @param name the name, in any case
     * @return the value, or {@code null}
     */
    public @Nullable String header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Returns the body as UTF-8 text.
     *
     * @return the text
     */
    public String text() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Parses the body as JSON.
     *
     * @return the value
     * @throws dev.gulp.api.data.JsonParseException if the body is not JSON
     */
    public JsonValue json() {
        return Json.parse(text());
    }

    /**
     * Returns the body.
     *
     * @return a copy of the bytes
     */
    public byte[] bytes() {
        return body.clone();
    }

    @Override
    public String toString() {
        return "HttpResponse[" + status + ", " + body.length + " bytes]";
    }
}
