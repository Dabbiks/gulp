package dev.gulp.core;

import dev.gulp.api.data.Codec;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.spi.ApiSupport;
import dev.gulp.core.data.DataContainerImpl;
import dev.gulp.core.data.JsonReader;
import dev.gulp.core.data.JsonWriter;
import org.jspecify.annotations.Nullable;

/** {@link ApiSupport} backed by the core JSON implementation and the shared generated-code registry. */
public final class CoreApiSupport implements ApiSupport {

    /** Creates the support; instantiated by {@link java.util.ServiceLoader}. */
    public CoreApiSupport() {}

    @Override
    public JsonValue parseJson(String text) {
        return JsonReader.parse(text);
    }

    @Override
    public String writeJson(JsonValue value, boolean pretty) {
        return JsonWriter.write(value, pretty);
    }

    @Override
    public DataContainer newDataContainer(JsonObject initial) {
        return new DataContainerImpl(initial);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable Codec<T> generatedCodec(Class<T> type) {
        return GeneratedModules.shared().lookup(Codec.class, type);
    }
}
