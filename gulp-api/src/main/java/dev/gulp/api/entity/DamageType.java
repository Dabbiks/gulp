package dev.gulp.api.entity;

import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;

/**
 * A kind of damage, such as fire or a fall, registered in {@code Registries.DAMAGE_TYPE}. The engine registers {@link
 * #GENERIC}.
 *
 * <pre>{@code
 * FIRE = registries().register(Registries.DAMAGE_TYPE, DamageType.of(key("fire")));
 * target.get(Health.class).damage(2, FIRE, torch);
 * }</pre>
 */
public final class DamageType implements Keyed {

    /** Damage without a particular kind. */
    public static final DamageType GENERIC = new DamageType(Key.of(Key.RESERVED, "generic"));

    private final Key key;

    private DamageType(Key key) {
        this.key = key;
    }

    /**
     * Creates a damage type to register.
     *
     * @param key the key
     * @return the type
     */
    public static DamageType of(Key key) {
        return new DamageType(key);
    }

    @Override
    public Key key() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DamageType type && type.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "DamageType[" + key + "]";
    }
}
