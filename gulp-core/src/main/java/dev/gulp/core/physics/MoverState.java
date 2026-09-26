package dev.gulp.core.physics;

import dev.gulp.api.entity.Entity;
import org.jspecify.annotations.Nullable;

/** What the physics remembers about a mover between moves: touches for begin/end events and the platform it stands on. */
final class MoverState {

    long[] previous = new long[8];
    int previousCount;
    long[] current = new long[8];
    int currentCount;

    @Nullable Entity lastFloor;

    /** Contacts reused by this mover's moves; the mover's list is valid until its next move. */
    final java.util.List<dev.gulp.api.physics.Contact> contactPool = new java.util.ArrayList<>();

    int contactsUsed;

    dev.gulp.api.physics.Contact nextContact() {
        if (contactsUsed == contactPool.size()) {
            contactPool.add(new dev.gulp.api.physics.Contact(
                    null, null, dev.gulp.api.math.Vec2.ZERO, dev.gulp.api.math.Vec2.ZERO));
        }
        return contactPool.get(contactsUsed++);
    }

    float lastFloorX;
    float lastFloorY;

    boolean wasTouching(long key) {
        for (int i = 0; i < previousCount; i++) {
            if (previous[i] == key) {
                return true;
            }
        }
        return false;
    }

    boolean touches(long key) {
        for (int i = 0; i < currentCount; i++) {
            if (current[i] == key) {
                return true;
            }
        }
        return false;
    }

    /** Records a touch; returns {@code false} if it was already recorded in this move. */
    boolean touch(long key) {
        if (touches(key)) {
            return false;
        }
        if (currentCount == current.length) {
            current = java.util.Arrays.copyOf(current, current.length * 2);
        }
        current[currentCount++] = key;
        return true;
    }

    /** Makes this move's touches the previous ones. */
    void swap() {
        long[] old = previous;
        previous = current;
        previousCount = currentCount;
        current = old;
        currentCount = 0;
    }
}
