package dev.gulp.api.net;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An HTTP request built step by step.
 *
 * <pre>{@code
 * HttpRequest request = HttpRequest.builder("https://example.com/api/save")
 *         .method("PUT")
 *         .header("Authorization", "Bearer " + token)
 *         .body("{\"level\":3}", "application/json")
 *         .timeout(Duration.ofSeconds(5))
 *         .build();
 * http().request(request).thenSync(response -> logger().info("status " + response.status()));
 * }</pre>
 *
 * @param method the method, for example {@code GET}
 * @param url absolute URL
 * @param headers request headers
 * @param body the body, or {@code null}
 * @param timeout how long to wait for the whole response
 */
public record HttpRequest(
        String method, String url, Map<String, String> headers, byte @Nullable [] body, Duration timeout) {

    /** The timeout when none is set. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Copies the headers.
     *
     * @param method the method
     * @param url the URL
     * @param headers the headers
     * @param body the body
     * @param timeout the timeout
     */
    public HttpRequest {
        Objects.requireNonNull(url, "url");
        headers = Map.copyOf(headers);
    }

    /**
     * Starts a GET request.
     *
     * @param url absolute URL
     * @return the builder
     */
    public static Builder builder(String url) {
        return new Builder(url);
    }

    /** Builds an {@link HttpRequest}. */
    public static final class Builder {
        private final String url;
        private String method = "GET";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte @Nullable [] body;
        private Duration timeout = DEFAULT_TIMEOUT;

        private Builder(String url) {
            this.url = url;
        }

        /**
         * Sets the method.
         *
         * @param value for example {@code POST}
         * @return this builder
         */
        public Builder method(String value) {
            this.method = value;
            return this;
        }

        /**
         * Adds a header.
         *
         * @param name the name
         * @param value the value
         * @return this builder
         */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /**
         * Sets a text body (UTF-8) and its content type; a GET becomes a POST.
         *
         * @param text the body
         * @param contentType the media type
         * @return this builder
         */
        public Builder body(String text, String contentType) {
            return body(text.getBytes(StandardCharsets.UTF_8), contentType);
        }

        /**
         * Sets a binary body and its content type; a GET becomes a POST.
         *
         * @param bytes the body
         * @param contentType the media type
         * @return this builder
         */
        public Builder body(byte[] bytes, String contentType) {
            this.body = bytes.clone();
            headers.put("Content-Type", contentType);
            if (method.equals("GET")) {
                method = "POST";
            }
            return this;
        }

        /**
         * Sets the timeout.
         *
         * @param value how long to wait, more than zero
         * @return this builder
         * @throws IllegalArgumentException if the timeout is zero or negative
         */
        public Builder timeout(Duration value) {
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("The timeout must be positive: " + value);
            }
            this.timeout = value;
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public HttpRequest build() {
            return new HttpRequest(method, url, headers, body, timeout);
        }
    }
}
