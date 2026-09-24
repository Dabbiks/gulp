package dev.gulp.core.world;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.Trigger;
import dev.gulp.api.spi.PhysicsAccess;
import java.util.List;

/** The engine side of {@link PhysicsAccess}: routes each component to the physics of its entity's world. */
public final class PhysicsBackend implements PhysicsAccess.Backend {

    /** Creates the backend. */
    public PhysicsBackend() {}

    private static WorldImpl world(Component component) {
        return (WorldImpl) component.entity().world();
    }

    @Override
    public void attach(Component component) {
        world(component).physics.attach(component);
    }

    @Override
    public void detach(Component component) {
        if (component.isAttached()) {
            world(component).physics.detach(component);
        }
    }

    @Override
    public PhysicsAccess.BodyHandle attachBody(Body body) {
        return world(body).physics.attachBody(body);
    }

    @Override
    public void moveAndSlide(Mover mover, float velocityX, float velocityY, PhysicsAccess.MoveResult out) {
        world(mover).physics.moveAndSlide(mover, velocityX, velocityY, out);
    }

    @Override
    public List<Entity> triggered(Trigger trigger) {
        return world(trigger).physics.triggered(trigger);
    }
}
