package dev.gulp.core.world;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Light;
import dev.gulp.api.render.Lighting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lighting settings of one world; drawn by {@link LightRenderer}. */
final class LightingImpl implements Lighting {

    private Color ambient = Color.WHITE;
    private boolean enabled;
    private boolean tileShadows = true;
    final List<Light> lights = new ArrayList<>();
    private final List<Light> view = Collections.unmodifiableList(lights);

    @Override
    public Color ambient() {
        return ambient;
    }

    @Override
    public Lighting ambient(Color color) {
        ambient = color;
        enabled = true;
        return this;
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Light add(Light light) {
        if (!lights.contains(light)) {
            lights.add(light);
        }
        return light;
    }

    @Override
    public boolean remove(Light light) {
        return lights.remove(light);
    }

    @Override
    public List<Light> lights() {
        return view;
    }

    @Override
    public boolean tileShadows() {
        return tileShadows;
    }

    @Override
    public void setTileShadows(boolean value) {
        tileShadows = value;
    }
}
