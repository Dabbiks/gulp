package com.example.mygame;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.ComponentInfo;
import dev.gulp.api.entity.Save;

/** Moves the player with the input actions; the step count is saved with the entity. */
@ComponentInfo(key = "mygame:walker")
public final class Walker extends Component {

    /** Units per second. */
    static final float SPEED = 6f;

    /** Ticks spent walking, saved in save slots. */
    @Save
    int steps;

    /** Creates the component. */
    public Walker() {}

    @Override
    protected void onTick() {
        var input = input();
        float dx = input.axis(MyGame.moveLeft, MyGame.moveX);
        float dy = input.axis(MyGame.moveUp, MyGame.moveY);
        if (dx != 0f || dy != 0f) {
            float dt = 1f / engine().targetTps();
            entity().setPosition(entity().x() + dx * SPEED * dt, entity().y() + dy * SPEED * dt);
            steps++;
        }
    }

    /**
     * Returns the ticks spent walking.
     *
     * @return the count
     */
    public int steps() {
        return steps;
    }
}
