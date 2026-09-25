package dev.gulp.core.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Gradient;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.particle.EmitterConfig;
import dev.gulp.api.particle.EmitterShape;
import dev.gulp.api.particle.ParticleCollision;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.particle.ParticleInstance;
import dev.gulp.api.particle.Particles;
import dev.gulp.core.graphics.DrawImpl;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The particles of one world: CPU simulation over pooled arrays per emitter, advanced every frame in game time and
 * drawn through the batcher in the render layer of each emitter. Simulating and drawing do not allocate once the
 * arrays have grown; spawning an effect allocates its instance.
 */
final class ParticleSystem implements Particles {

    private final WorldImpl world;
    private final List<Instance> instances = new ArrayList<>();
    private final Map<BlendMode, Material> blends = new EnumMap<>(BlendMode.class);
    private int count;
    private int limit = DEFAULT_LIMIT;
    private int seed = 0x2545F491;

    ParticleSystem(WorldImpl world) {
        this.world = world;
    }

    // ------------------------------------------------------------------ API

    @Override
    public int count() {
        return count;
    }

    @Override
    public int instanceCount() {
        return instances.size();
    }

    @Override
    public int limit() {
        return limit;
    }

    @Override
    public void setLimit(int value) {
        limit = Math.max(0, value);
    }

    @Override
    public void clear() {
        for (Instance instance : instances) {
            instance.clearParticles();
            instance.emitting = false;
        }
        instances.clear();
        count = 0;
    }

    ParticleInstance spawn(ParticleEffect effect, float x, float y) {
        Instance instance = new Instance(effect, x, y);
        instances.add(instance);
        return instance;
    }

    ParticleInstance spawn(AssetKey<ParticleEffect> key, float x, float y) {
        return spawn(world.worlds.assets.get(key), x, y);
    }

    // ------------------------------------------------------------------ simulation

    /**
     * Advances every instance.
     *
     * @param seconds game time passed
     * @param alpha tick interpolation, for following entities smoothly
     */
    void advance(float seconds, float alpha) {
        if (seconds <= 0f) {
            return;
        }
        // Instances spawned while advancing (sub-emitters) start on the next frame.
        int n = instances.size();
        for (int i = 0; i < n; i++) {
            instances.get(i).advance(seconds, alpha);
        }
        int kept = 0;
        for (int i = 0; i < instances.size(); i++) {
            Instance instance = instances.get(i);
            if (instance.isAlive()) {
                instances.set(kept++, instance);
            }
        }
        for (int i = instances.size() - 1; i >= kept; i--) {
            instances.remove(i);
        }
    }

    private float random() {
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return (seed >>> 8) * (1f / 16_777_216f);
    }

    private float range(float min, float max) {
        return min + (max - min) * random();
    }

    private boolean solid(float x, float y) {
        return world.tileMap.isSolidAt((int) Math.floor(x), (int) Math.floor(y));
    }

    // ------------------------------------------------------------------ drawing

    void draw(DrawImpl draw, String layer, float[] view) {
        for (int i = 0; i < instances.size(); i++) {
            Instance instance = instances.get(i);
            for (Run run : instance.runs) {
                if (run.size > 0 && run.config.layer().equals(layer)) {
                    run.draw(draw, view);
                }
            }
        }
    }

    private Material blend(BlendMode mode) {
        Material material = blends.get(mode);
        if (material == null) {
            material = Material.DEFAULT.withBlend(mode);
            blends.put(mode, material);
        }
        return material;
    }

    // ------------------------------------------------------------------ instances

    private final class Instance implements ParticleInstance {
        final ParticleEffect effect;
        final Run[] runs;
        float x;
        float y;

        @Nullable Entity followed;

        boolean emitting = true;
        boolean killed;

        Instance(ParticleEffect effect, float x, float y) {
            this.effect = effect;
            this.x = x;
            this.y = y;
            List<EmitterConfig> emitters = effect.emitters();
            runs = new Run[emitters.size()];
            for (int i = 0; i < runs.length; i++) {
                runs[i] = new Run(this, emitters.get(i));
            }
        }

        @Override
        public ParticleEffect effect() {
            return effect;
        }

        @Override
        public void stop() {
            emitting = false;
        }

        @Override
        public void kill() {
            emitting = false;
            killed = true;
            clearParticles();
        }

        void clearParticles() {
            for (Run run : runs) {
                count -= run.size;
                run.size = 0;
            }
        }

        @Override
        public ParticleInstance follow(@Nullable Entity entity) {
            followed = entity;
            if (entity != null) {
                x = entity.position().x();
                y = entity.position().y();
            }
            return this;
        }

        @Override
        public ParticleInstance setPosition(Vec2 position) {
            x = position.x();
            y = position.y();
            return this;
        }

        @Override
        public Vec2 position() {
            return new Vec2(x, y);
        }

        @Override
        public boolean isAlive() {
            if (killed) {
                return false;
            }
            if (isEmitting()) {
                return true;
            }
            return particleCount() > 0;
        }

        @Override
        public boolean isEmitting() {
            if (!emitting || killed) {
                return false;
            }
            for (Run run : runs) {
                if (!run.done) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int particleCount() {
            int total = 0;
            for (Run run : runs) {
                total += run.size;
            }
            return total;
        }

        void advance(float seconds, float alpha) {
            float oldX = x;
            float oldY = y;
            Entity target = followed;
            if (target != null) {
                if (target.isRemoved()) {
                    followed = null;
                    emitting = false;
                } else if (target instanceof EntityImpl impl) {
                    x = impl.renderX(alpha);
                    y = impl.renderY(alpha);
                } else {
                    x = target.position().x();
                    y = target.position().y();
                }
            }
            float dx = x - oldX;
            float dy = y - oldY;
            for (Run run : runs) {
                run.advance(seconds, dx, dy);
            }
        }
    }

    // ------------------------------------------------------------------ emitters

    private final class Run {
        final Instance instance;
        final EmitterConfig config;
        float time;
        float owed;
        int nextBurst;
        boolean done;

        @Nullable TextureRegion[] frames;

        boolean requested;

        int size;
        float[] px = new float[16];
        float[] py = new float[16];
        float[] vx = new float[16];
        float[] vy = new float[16];
        float[] rot = new float[16];
        float[] spin = new float[16];
        float[] age = new float[16];
        float[] life = new float[16];

        Run(Instance instance, EmitterConfig config) {
            this.instance = instance;
            this.config = config;
        }

        void advance(float seconds, float dx, float dy) {
            if (config.isLocal() && (dx != 0f || dy != 0f)) {
                for (int i = 0; i < size; i++) {
                    px[i] += dx;
                    py[i] += dy;
                }
            }
            simulate(seconds);
            if (!done && instance.emitting) {
                emit(seconds);
            } else if (!instance.emitting) {
                done = true;
            }
        }

        private void emit(float seconds) {
            float before = time;
            time += seconds;
            List<EmitterConfig.Burst> bursts = config.bursts();
            float duration = config.duration();
            while (nextBurst < bursts.size() && bursts.get(nextBurst).at() <= time) {
                EmitterConfig.Burst burst = bursts.get(nextBurst++);
                for (int i = 0; i < burst.count(); i++) {
                    spawnOne();
                }
            }
            if (config.rate() > 0f) {
                float active = Math.max(0f, Math.min(time, duration <= 0f ? time : duration) - before);
                owed += config.rate() * active;
                int n = (int) owed;
                owed -= n;
                for (int i = 0; i < n; i++) {
                    spawnOne();
                }
            }
            if (time >= duration) {
                if (config.loop() && duration > 0f) {
                    time -= duration;
                    nextBurst = 0;
                } else {
                    done = true;
                }
            }
        }

        private void spawnOne() {
            int max = config.maxParticles();
            if (count >= limit || (max > 0 && size >= max)) {
                return;
            }
            if (size == px.length) {
                grow();
            }
            float ox = 0f;
            float oy = 0f;
            switch (config.shape()) {
                case EmitterShape.Point p -> {}
                case EmitterShape.Circle c -> {
                    float r = c.radius() * (float) Math.sqrt(random());
                    float a = random() * 6.2831855f;
                    ox = (float) Math.cos(a) * r;
                    oy = (float) Math.sin(a) * r;
                }
                case EmitterShape.Ring ring -> {
                    float r = ring.radius() + (random() - 0.5f) * ring.thickness();
                    float a = random() * 6.2831855f;
                    ox = (float) Math.cos(a) * r;
                    oy = (float) Math.sin(a) * r;
                }
                case EmitterShape.Rect rect -> {
                    ox = (random() - 0.5f) * rect.width();
                    oy = (random() - 0.5f) * rect.height();
                }
                case EmitterShape.Line line -> ox = (random() - 0.5f) * line.length();
            }
            int i = size++;
            count++;
            px[i] = instance.x + ox;
            py[i] = instance.y + oy;
            double angle = Math.toRadians(config.direction() + (random() - 0.5f) * config.spread());
            float speed = range(config.speedMin(), config.speedMax());
            vx[i] = (float) Math.cos(angle) * speed;
            vy[i] = (float) Math.sin(angle) * speed;
            rot[i] = range(config.rotationMin(), config.rotationMax());
            spin[i] = range(config.spinMin(), config.spinMax());
            age[i] = 0f;
            life[i] = range(config.lifetimeMin(), config.lifetimeMax());
        }

        private void grow() {
            int n = px.length * 2;
            px = java.util.Arrays.copyOf(px, n);
            py = java.util.Arrays.copyOf(py, n);
            vx = java.util.Arrays.copyOf(vx, n);
            vy = java.util.Arrays.copyOf(vy, n);
            rot = java.util.Arrays.copyOf(rot, n);
            spin = java.util.Arrays.copyOf(spin, n);
            age = java.util.Arrays.copyOf(age, n);
            life = java.util.Arrays.copyOf(life, n);
        }

        private void simulate(float seconds) {
            float gx = config.gravity().x() * seconds;
            float gy = config.gravity().y() * seconds;
            float keep = Math.max(0f, 1f - config.drag() * seconds);
            ParticleCollision collision = config.collision();
            float bounce = config.bounce();
            for (int i = 0; i < size; ) {
                age[i] += seconds;
                if (age[i] >= life[i]) {
                    die(i);
                    continue;
                }
                vx[i] = (vx[i] + gx) * keep;
                vy[i] = (vy[i] + gy) * keep;
                float nx = px[i] + vx[i] * seconds;
                float ny = py[i] + vy[i] * seconds;
                if (collision != ParticleCollision.NONE) {
                    if (solid(nx, ny)) {
                        if (collision == ParticleCollision.DIE) {
                            px[i] = nx;
                            py[i] = ny;
                            die(i);
                            continue;
                        }
                        if (solid(nx, py[i])) {
                            vx[i] = -vx[i] * bounce;
                            nx = px[i];
                        }
                        if (solid(px[i], ny)) {
                            vy[i] = -vy[i] * bounce;
                            ny = py[i];
                        }
                        if (solid(nx, ny)) {
                            nx = px[i];
                            ny = py[i];
                            vx[i] = -vx[i] * bounce;
                            vy[i] = -vy[i] * bounce;
                        }
                    }
                }
                px[i] = nx;
                py[i] = ny;
                rot[i] += spin[i] * seconds;
                i++;
            }
        }

        private void die(int i) {
            ParticleEffect after = config.onDeath();
            if (after != null && !instance.killed) {
                spawn(after, px[i], py[i]);
            }
            int last = --size;
            count--;
            px[i] = px[last];
            py[i] = py[last];
            vx[i] = vx[last];
            vy[i] = vy[last];
            rot[i] = rot[last];
            spin[i] = spin[last];
            age[i] = age[last];
            life[i] = life[last];
        }

        // -------------------------------------------------------------- drawing

        private @Nullable TextureRegion[] frames(DrawImpl draw) {
            TextureRegion[] resolved = frames;
            if (resolved != null) {
                return resolved;
            }
            List<AssetKey<TextureRegion>> keys = config.frames();
            if (keys.isEmpty()) {
                frames = new TextureRegion[] {draw.graphics().softDot()};
                return frames;
            }
            TextureRegion[] result = new TextureRegion[keys.size()];
            for (int i = 0; i < result.length; i++) {
                TextureRegion region = world.worlds.assets.getIfLoaded(keys.get(i));
                if (region == null) {
                    if (!requested) {
                        requested = true;
                        world.worlds.assets.load(keys.get(i)).thenSync(loaded -> requested = false);
                    }
                    return null;
                }
                result[i] = region;
            }
            frames = result;
            return result;
        }

        void draw(DrawImpl draw, float[] view) {
            TextureRegion[] images = frames(draw);
            if (images == null) {
                return;
            }
            draw.material(blend(config.blend()));
            Gradient gradient = config.color();
            for (int i = 0; i < size; i++) {
                float t = age[i] / life[i];
                float half = config.size().at(t) * 0.5f;
                float x = px[i];
                float y = py[i];
                if (x + half < view[0] || x - half > view[2] || y + half < view[1] || y - half > view[3]) {
                    continue;
                }
                TextureRegion region =
                        images.length == 1 ? images[0] : images[Math.min(images.length - 1, (int) (t * images.length))];
                float r;
                float g;
                float b;
                float a;
                int stops = gradient.size();
                if (stops == 1 || t <= gradient.time(0)) {
                    Color c = gradient.color(0);
                    r = c.r();
                    g = c.g();
                    b = c.b();
                    a = c.a();
                } else if (t >= gradient.time(stops - 1)) {
                    Color c = gradient.color(stops - 1);
                    r = c.r();
                    g = c.g();
                    b = c.b();
                    a = c.a();
                } else {
                    int k = 1;
                    while (gradient.time(k) < t) {
                        k++;
                    }
                    float span = gradient.time(k) - gradient.time(k - 1);
                    float f = span <= 0f ? 1f : (t - gradient.time(k - 1)) / span;
                    Color c0 = gradient.color(k - 1);
                    Color c1 = gradient.color(k);
                    r = c0.r() + (c1.r() - c0.r()) * f;
                    g = c0.g() + (c1.g() - c0.g()) * f;
                    b = c0.b() + (c1.b() - c0.b()) * f;
                    a = c0.a() + (c1.a() - c0.a()) * f;
                }
                a *= config.alpha().at(t);
                draw.sprite(region, x, y, half, half, rot[i], r, g, b, a);
            }
            draw.material(Material.DEFAULT);
        }
    }
}
