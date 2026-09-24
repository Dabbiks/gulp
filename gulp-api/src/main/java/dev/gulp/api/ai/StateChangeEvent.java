package dev.gulp.api.ai;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;

/**
 * A {@link StateMachine} changed state.
 *
 * <pre>{@code
 * boss.on(StateChangeEvent.class, e -> { if (e.to() == BossState.ENRAGED) music().play(BOSS_THEME); });
 * }</pre>
 */
public final class StateChangeEvent extends EntityEvent {

    private final StateMachine<?> machine;
    private final Object from;
    private final Object to;

    /**
     * Creates the event.
     *
     * @param entity the machine's entity
     * @param machine the machine
     * @param from the previous state
     * @param to the new state
     */
    public StateChangeEvent(Entity entity, StateMachine<?> machine, Object from, Object to) {
        super(entity);
        this.machine = machine;
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the machine.
     *
     * @return the machine
     */
    public StateMachine<?> machine() {
        return machine;
    }

    /**
     * Returns the previous state.
     *
     * @return the state
     */
    public Object from() {
        return from;
    }

    /**
     * Returns the new state.
     *
     * @return the state
     */
    public Object to() {
        return to;
    }
}
