package dev.gulp.api.anim;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Plays tweens one after another or together. */
final class GroupTween extends Tween {

    private final boolean sequential;
    private final List<Tween> children;
    private final boolean[] finished;
    private int index;
    private int cycle;

    private GroupTween(boolean sequential, List<Tween> children) {
        this.sequential = sequential;
        this.children = children;
        this.finished = new boolean[children.size()];
    }

    static GroupTween sequence(Tween... tweens) {
        return new GroupTween(true, List.of(tweens));
    }

    static GroupTween parallel(Tween... tweens) {
        return new GroupTween(false, List.of(tweens));
    }

    @Override
    public Tween then(Tween next) {
        if (!sequential || repeatCount() != 0) {
            return super.then(next);
        }
        List<Tween> list = new ArrayList<>(children);
        list.add(next);
        GroupTween longer = new GroupTween(true, list);
        longer.realtime = realtime;
        return longer;
    }

    @Override
    public float duration() {
        float total = 0f;
        for (Tween child : children) {
            float length = child.totalDuration();
            total = sequential ? total + length : Math.max(total, length);
        }
        return total;
    }

    @Override
    void reset() {
        index = 0;
        cycle = 0;
        restartChildren();
    }

    private void restartChildren() {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).restart();
            finished[i] = false;
        }
    }

    @Override
    float play(float seconds) {
        float left = seconds;
        int n = children.size();
        while (true) {
            float before = left;
            if (sequential) {
                while (index < n) {
                    float rest = children.get(index).advance(left);
                    if (rest < 0f) {
                        return -1f;
                    }
                    left = rest;
                    index++;
                }
            } else {
                float smallest = left;
                boolean all = true;
                for (int i = 0; i < n; i++) {
                    if (finished[i]) {
                        continue;
                    }
                    float rest = children.get(i).advance(left);
                    if (rest < 0f) {
                        all = false;
                    } else {
                        finished[i] = true;
                        smallest = Math.min(smallest, rest);
                    }
                }
                if (!all) {
                    return -1f;
                }
                left = smallest;
            }
            cycle++;
            int repeat = repeatCount();
            if (repeat >= 0 && cycle > repeat) {
                return left;
            }
            if (left >= before) {
                // An endless group of instant tweens: one cycle per frame.
                index = 0;
                restartChildren();
                return -1f;
            }
            index = 0;
            restartChildren();
        }
    }

    @Override
    @Nullable Object target() {
        Object shared = null;
        for (Tween child : children) {
            Object target = child.target();
            if (target == null || (shared != null && shared != target)) {
                return null;
            }
            shared = target;
        }
        return shared;
    }

    @Override
    boolean involves(Object target) {
        for (Tween child : children) {
            if (child.involves(target)) {
                return true;
            }
        }
        return false;
    }
}
