package dev.gulp.core.graphics;

import dev.gulp.api.render.PostEffect;
import dev.gulp.api.render.PostEffects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A post-processing chain of a world or of the display. */
public final class PostEffectsImpl implements PostEffects {

    final List<PostEffect> effects = new ArrayList<>();
    private final List<PostEffect> view = Collections.unmodifiableList(effects);

    @Override
    public <E extends PostEffect> E add(E effect) {
        if (!effects.contains(effect)) {
            effects.add(effect);
        }
        return effect;
    }

    @Override
    public boolean remove(PostEffect effect) {
        return effects.remove(effect);
    }

    @Override
    public void clear() {
        effects.clear();
    }

    @Override
    public List<PostEffect> effects() {
        return view;
    }

    /**
     * Returns whether any effect is enabled.
     *
     * @return {@code true} if the chain changes the picture
     */
    public boolean isActive() {
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i).isEnabled()) {
                return true;
            }
        }
        return false;
    }
}
