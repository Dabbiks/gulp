package dev.gulp.api.spi;

import dev.gulp.api.data.Codec;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of the parts of the API that are static but live in {@code gulp-core} (JSON and YAML parsing, data
 * containers, generated codecs). Provided by {@code gulp-core} through {@link ServiceLoader}; game code never uses it
 * directly.
 *
 * <pre>{@code
 * // META-INF/services/dev.gulp.api.spi.ApiSupport in gulp-core
 * dev.gulp.core.CoreApiSupport
 * }</pre>
 */
public interface ApiSupport {

    /**
     * Returns the implementation found on the classpath.
     *
     * @return the implementation
     * @throws IllegalStateException if {@code gulp-core} is not on the classpath
     */
    static ApiSupport get() {
        return Holder.INSTANCE;
    }

    /**
     * Parses JSON text.
     *
     * @param text the text
     * @return the value
     * @throws dev.gulp.api.data.JsonParseException if the text is invalid
     */
    JsonValue parseJson(String text);

    /**
     * Writes JSON text.
     *
     * @param value the value
     * @param pretty whether to indent
     * @return the text
     */
    String writeJson(JsonValue value, boolean pretty);

    /**
     * Creates a data container.
     *
     * @param initial initial contents in JSON form; member names are keys
     * @return the container
     */
    DataContainer newDataContainer(JsonObject initial);

    /**
     * Finds the codec generated for a {@code @Serializable} record.
     *
     * @param <T> the record type
     * @param type the record class
     * @return the codec, or {@code null} if none was generated
     */
    <T> @Nullable Codec<T> generatedCodec(Class<T> type);

    /** Lazily loads the implementation once. */
    final class Holder {
        static final ApiSupport INSTANCE = load();

        private Holder() {}

        private static ApiSupport load() {
            Iterator<ApiSupport> found = ServiceLoader.load(ApiSupport.class).iterator();
            if (!found.hasNext()) {
                throw new IllegalStateException("gulp-core is not on the classpath (no ApiSupport implementation)");
            }
            return found.next();
        }
    }
}
