/**
 * Game AI: {@link dev.gulp.api.ai.StateMachine}, {@link dev.gulp.api.ai.BehaviorTree} and weighted {@link
 * dev.gulp.api.ai.Steering} behaviours, all components ticked with their entity. Agents talk to each other through
 * events.
 *
 * <pre>{@code
 * enemy.add(new StateMachine<>(Mode.IDLE).transition(Mode.IDLE, Mode.CHASE, this::seesPlayer));
 * bird.add(new Steering().wander(0.5f).separation(1f, 1.2f));
 * }</pre>
 */
@NullMarked
package dev.gulp.api.ai;

import org.jspecify.annotations.NullMarked;
