package dev.gulp.api.ai;

import dev.gulp.api.entity.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * A finite state machine: each state may run code on enter, every tick and on exit, and conditional transitions are
 * checked every tick before the current state's tick code. As a component it ticks with its entity and fires {@link
 * StateChangeEvent}; it also works on its own through {@link #update()}.
 *
 * <pre>{@code
 * enum Mode { PATROL, CHASE, FLEE }
 *
 * StateMachine<Mode> brain = new StateMachine<>(Mode.PATROL)
 *         .onTick(Mode.PATROL, this::patrol)
 *         .onEnter(Mode.CHASE, () -> agent.follow(player))
 *         .onTick(Mode.FLEE, this::runAway)
 *         .transition(Mode.PATROL, Mode.CHASE, () -> seesPlayer())
 *         .transition(Mode.CHASE, Mode.PATROL, () -> !seesPlayer())
 *         .anyTransition(Mode.FLEE, () -> health.current() < 3);
 * }</pre>
 *
 * @param <S> the state type, usually an enum
 */
public final class StateMachine<S> extends Component {

    private record Transition<S>(S to, BooleanSupplier condition) {}

    private final Map<S, List<Runnable>> enters = new HashMap<>();
    private final Map<S, List<Runnable>> ticks = new HashMap<>();
    private final Map<S, List<Runnable>> exits = new HashMap<>();
    private final Map<S, List<Transition<S>>> transitions = new HashMap<>();
    private final List<Transition<S>> anyTransitions = new ArrayList<>();
    private S state;
    private S previous;
    private int ticksInState;
    private boolean started;

    /**
     * Creates a machine; the initial state is entered on the first tick.
     *
     * @param initial the first state
     */
    public StateMachine(S initial) {
        this.state = initial;
        this.previous = initial;
    }

    /**
     * Adds code run when a state is entered.
     *
     * @param target the state
     * @param action the code
     * @return this machine
     */
    public StateMachine<S> onEnter(S target, Runnable action) {
        enters.computeIfAbsent(target, k -> new ArrayList<>()).add(action);
        return this;
    }

    /**
     * Adds code run every tick in a state.
     *
     * @param target the state
     * @param action the code
     * @return this machine
     */
    public StateMachine<S> onTick(S target, Runnable action) {
        ticks.computeIfAbsent(target, k -> new ArrayList<>()).add(action);
        return this;
    }

    /**
     * Adds code run when a state is left.
     *
     * @param target the state
     * @param action the code
     * @return this machine
     */
    public StateMachine<S> onExit(S target, Runnable action) {
        exits.computeIfAbsent(target, k -> new ArrayList<>()).add(action);
        return this;
    }

    /**
     * Changes from one state to another when a condition holds, checked in the order added.
     *
     * @param from the state it applies to
     * @param to the next state
     * @param condition checked every tick
     * @return this machine
     */
    public StateMachine<S> transition(S from, S to, BooleanSupplier condition) {
        transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(new Transition<>(to, condition));
        return this;
    }

    /**
     * Changes to a state from any other state when a condition holds; checked before the per-state transitions.
     *
     * @param to the next state
     * @param condition checked every tick
     * @return this machine
     */
    public StateMachine<S> anyTransition(S to, BooleanSupplier condition) {
        anyTransitions.add(new Transition<>(to, condition));
        return this;
    }

    /**
     * Returns the current state.
     *
     * @return the state
     */
    public S state() {
        return state;
    }

    /**
     * Returns the state before the last change.
     *
     * @return the previous state, the initial state before any change
     */
    public S previousState() {
        return previous;
    }

    /**
     * Returns how long the machine has been in the current state.
     *
     * @return ticks since entering it
     */
    public int ticksInState() {
        return ticksInState;
    }

    /**
     * Returns whether the machine is in a state.
     *
     * @param value the state
     * @return {@code true} if current
     */
    public boolean is(S value) {
        return state.equals(value);
    }

    /**
     * Leaves the current state and enters another at once. Changing to the current state re-enters it.
     *
     * @param next the state
     */
    public void changeState(S next) {
        if (!started) {
            start();
        }
        run(exits.get(state));
        previous = state;
        state = next;
        ticksInState = 0;
        run(enters.get(next));
        if (isAttached() && entity().isSpawned()) {
            engine().events().call(new StateChangeEvent(entity(), this, previous, next));
        }
    }

    /** Checks the transitions and runs the current state's tick code; called every tick when used as a component. */
    public void update() {
        if (!started) {
            start();
        }
        S current = state;
        for (Transition<S> t : anyTransitions) {
            if (!t.to.equals(current) && t.condition.getAsBoolean()) {
                changeState(t.to);
                break;
            }
        }
        if (state.equals(current)) {
            List<Transition<S>> own = transitions.get(current);
            if (own != null) {
                for (Transition<S> t : own) {
                    if (t.condition.getAsBoolean()) {
                        changeState(t.to);
                        break;
                    }
                }
            }
        }
        run(ticks.get(state));
        ticksInState++;
    }

    private void start() {
        started = true;
        run(enters.get(state));
    }

    private static void run(@Nullable List<Runnable> actions) {
        if (actions != null) {
            for (int i = 0; i < actions.size(); i++) {
                actions.get(i).run();
            }
        }
    }

    @Override
    protected void onTick() {
        update();
    }
}
