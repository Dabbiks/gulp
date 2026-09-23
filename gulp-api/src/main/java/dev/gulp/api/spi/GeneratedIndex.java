package dev.gulp.api.spi;

import dev.gulp.api.data.Codec;
import dev.gulp.api.event.Listener;
import dev.gulp.api.module.GameModule;

/**
 * Index of the code {@code gulp-processor} generated for one compilation (one JAR): listener dispatchers, module
 * descriptors and record codecs. Each compilation gets one generated implementation, registered in
 * {@code META-INF/services/dev.gulp.api.spi.GeneratedIndex}; the engine reads all of them at startup instead of using
 * reflection.
 *
 * <pre>{@code
 * // generated
 * public final class GulpGeneratedIndex implements GeneratedIndex {
 *     public void register(Sink sink) {
 *         sink.listener(DamageListener.class, new DamageListener$Handlers());
 *         sink.module(CombatModule.class, new ModuleDescriptor("combat", List.of("world"), List.of(), true));
 *         sink.codec(Upgrade.class, new Upgrade$Codec());
 *     }
 * }
 * }</pre>
 */
public interface GeneratedIndex {

    /**
     * Reports everything generated in this compilation.
     *
     * @param sink receives the entries
     */
    void register(Sink sink);

    /**
     * Receives generated entries.
     *
     * <pre>{@code
     * index.register(new GeneratedIndex.Sink() { ... });
     * }</pre>
     */
    interface Sink {

        /**
         * Reports the dispatcher of a listener class.
         *
         * @param <L> the listener type
         * @param type the listener class
         * @param handlers the generated dispatcher
         */
        <L extends Listener> void listener(Class<L> type, ListenerHandlers<L> handlers);

        /**
         * Reports the descriptor of a module class.
         *
         * @param type the module class
         * @param descriptor the values of its {@code @ModuleInfo}
         */
        void module(Class<? extends GameModule> type, ModuleDescriptor descriptor);

        /**
         * Reports the codec of a {@code @Serializable} record.
         *
         * @param <T> the record type
         * @param type the record class
         * @param codec the generated codec
         */
        <T> void codec(Class<T> type, Codec<T> codec);
    }
}
