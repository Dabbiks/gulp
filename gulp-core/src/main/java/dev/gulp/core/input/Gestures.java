package dev.gulp.core.input;

import dev.gulp.api.input.DoubleTapEvent;
import dev.gulp.api.input.LongPressEvent;
import dev.gulp.api.input.PanEvent;
import dev.gulp.api.input.PinchEvent;
import dev.gulp.api.input.SwipeEvent;
import dev.gulp.api.input.TapEvent;
import dev.gulp.api.input.Touch;
import dev.gulp.core.event.EventBus;
import java.util.List;

/**
 * Recognises tap, double tap, long press, pan, pinch and swipe from touch points. Distances are in logical points,
 * times in seconds; pan and pinch are reported at most once per frame.
 */
final class Gestures {

    static final float TAP_SLOP = 12f;
    static final float TAP_TIME = 0.3f;
    static final float DOUBLE_TAP_TIME = 0.35f;
    static final float DOUBLE_TAP_SLOP = 40f;
    static final float LONG_PRESS_TIME = 0.5f;
    static final float SWIPE_MAX_TIME = 0.4f;
    static final float SWIPE_MIN_DISTANCE = 60f;
    static final float SWIPE_MIN_SPEED = 400f;

    private final EventBus events;
    private boolean enabled;

    private int pointer = -1;
    private float downX;
    private float downY;
    private float downTime;
    private float lastX;
    private float lastY;
    private boolean moved;
    private boolean multi;
    private boolean longFired;
    private float lastTapTime = -1f;
    private float lastTapX;
    private float lastTapY;
    private float panX;
    private float panY;
    private float panDeltaX;
    private float panDeltaY;
    private boolean panPending;
    private float pinchDistance;

    Gestures(EventBus events) {
        this.events = events;
    }

    void setEnabled(boolean on) {
        enabled = on;
    }

    void down(int id, float x, float y, float now, int touching) {
        if (touching == 1) {
            pointer = id;
            downX = x;
            downY = y;
            lastX = x;
            lastY = y;
            downTime = now;
            moved = false;
            multi = false;
            longFired = false;
        } else {
            multi = true;
        }
    }

    void move(int id, float x, float y) {
        if (id != pointer || multi) {
            return;
        }
        if (!moved && Math.abs(x - downX) + Math.abs(y - downY) > TAP_SLOP) {
            moved = true;
        }
        if (moved) {
            panDeltaX += x - lastX;
            panDeltaY += y - lastY;
            panX = x;
            panY = y;
            panPending = true;
        }
        lastX = x;
        lastY = y;
    }

    void up(int id, float x, float y, float now) {
        if (id != pointer) {
            return;
        }
        pointer = -1;
        float duration = now - downTime;
        if (multi || longFired || !enabled) {
            return;
        }
        if (!moved && duration <= TAP_TIME) {
            if (events.hasListeners(TapEvent.class)) {
                events.call(new TapEvent(x, y));
            }
            boolean isDouble = lastTapTime >= 0f
                    && now - lastTapTime <= DOUBLE_TAP_TIME
                    && Math.abs(x - lastTapX) + Math.abs(y - lastTapY) <= DOUBLE_TAP_SLOP;
            if (isDouble) {
                lastTapTime = -1f;
                if (events.hasListeners(DoubleTapEvent.class)) {
                    events.call(new DoubleTapEvent(x, y));
                }
            } else {
                lastTapTime = now;
                lastTapX = x;
                lastTapY = y;
            }
            return;
        }
        float dx = x - downX;
        float dy = y - downY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float speed = distance / Math.max(duration, 1e-3f);
        if (moved
                && duration <= SWIPE_MAX_TIME
                && distance >= SWIPE_MIN_DISTANCE
                && speed >= SWIPE_MIN_SPEED
                && events.hasListeners(SwipeEvent.class)) {
            events.call(new SwipeEvent(dx / distance, dy / distance, speed));
        }
    }

    void frame(float now, List<? extends Touch> touches) {
        if (!enabled) {
            panPending = false;
            panDeltaX = 0f;
            panDeltaY = 0f;
            return;
        }
        if (pointer != -1 && !moved && !multi && !longFired && now - downTime >= LONG_PRESS_TIME) {
            longFired = true;
            if (events.hasListeners(LongPressEvent.class)) {
                events.call(new LongPressEvent(downX, downY));
            }
        }
        if (panPending) {
            panPending = false;
            if (events.hasListeners(PanEvent.class)) {
                events.call(new PanEvent(panX, panY, panDeltaX, panDeltaY));
            }
            panDeltaX = 0f;
            panDeltaY = 0f;
        }
        if (touches.size() >= 2) {
            Touch a = touches.get(0);
            Touch b = touches.get(1);
            float dx = b.x() - a.x();
            float dy = b.y() - a.y();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (pinchDistance > 0f && distance > 0f && distance != pinchDistance) {
                if (events.hasListeners(PinchEvent.class)) {
                    events.call(new PinchEvent((a.x() + b.x()) / 2f, (a.y() + b.y()) / 2f, distance / pinchDistance));
                }
            }
            pinchDistance = distance;
        } else {
            pinchDistance = 0f;
        }
    }
}
