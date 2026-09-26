package dev.gulp.api.entity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a component type and makes it persistent: its {@link Save} fields are written with the entity when a world is
 * saved and restored on load. {@code gulp-processor} generates the code that reads and writes the fields (no
 * reflection), and the type is registered in {@code Registries.COMPONENT_TYPE}.
 *
 * <pre>{@code
 * @ComponentInfo(key = "coins:wallet", persistent = true)
 * public final class Wallet extends Component {
 *     @Save int coins;
 *     @Save List<String> keys = new ArrayList<>();
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ComponentInfo {

    /**
     * Returns the key of the component type.
     *
     * @return {@code namespace:path}
     */
    String key();

    /**
     * Returns whether the component is saved with its entity.
     *
     * @return {@code true} to save the {@link Save} fields
     */
    boolean persistent() default true;
}
