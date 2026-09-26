package dev.gulp.api.entity;

import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;

/**
 * A named component class, from {@link ComponentInfo}. The engine registers one for every annotated component in
 * {@code Registries.COMPONENT_TYPE}; saves refer to components by these keys.
 *
 * <pre>{@code
 * ComponentType wallet = registries().get(Registries.COMPONENT_TYPE).getOrThrow(Key.parse("coins:wallet"));
 * boolean saved = wallet.persistent();
 * }</pre>
 *
 * @param key the key from {@code @ComponentInfo}
 * @param type the component class
 * @param persistent whether its {@code @Save} fields are saved
 */
public record ComponentType(Key key, Class<? extends Component> type, boolean persistent) implements Keyed {}
