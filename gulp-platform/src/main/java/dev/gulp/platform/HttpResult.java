package dev.gulp.platform;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Response to an HTTP request.
 *
 * <pre>{@code
 * if (result.status() == 200) parse(result.body());
 * }</pre>
 *
 * @param status HTTP status code
 * @param headers response headers, names in lower case
 * @param body response body
 */
public record HttpResult(int status, Map<String, List<String>> headers, ByteBuffer body) {}
