package dev.gulp.core.audio;

import dev.gulp.api.Logger;
import dev.gulp.api.PauseMode;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetLoader;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.audio.Audio;
import dev.gulp.api.audio.AudioClip;
import dev.gulp.api.audio.Bus;
import dev.gulp.api.audio.Music;
import dev.gulp.api.audio.MusicPlayer;
import dev.gulp.api.audio.PcmSource;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.data.Preferences;
import dev.gulp.api.event.Event;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.asset.AssetsImpl;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.PlatformAudio;
import dev.gulp.platform.PlatformAudioStream;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@link Audio}: all mixing decisions in Java over the platform voice pool. Every frame {@link #update} fades voices,
 * applies bus volumes, ducking, filters, positions and game pause, feeds streams and frees voices that ended.
 */
public final class AudioImpl implements Audio {

    /** Size of the voice pool. */
    public static final int MAX_VOICES = 32;

    private static final int MUSIC_PRIORITY = Integer.MAX_VALUE;
    private static final Playback SKIPPED = new SkippedPlayback();

    private final PlatformAudio platform;
    private final AssetsImpl assets;
    private final EventBus events;
    private final Preferences preferences;
    private final Supplier<Vec2> camera;
    private final Logger logger;
    private final Map<String, BusImpl> buses = new HashMap<>();
    private final List<BusImpl> busList = new ArrayList<>();
    private final BusImpl master;
    private final List<Voice> active = new ArrayList<>();
    private final Map<Sound, float[]> lastStarts = new HashMap<>();
    private final Set<AssetKey<AudioClip>> warned = new HashSet<>();
    private final MusicPlayerImpl music;
    private @Nullable Vec2 listener;
    private float listenerX;
    private float listenerY;
    private long order;
    private float time;
    private long random = 0x9E3779B97F4A7C15L;
    private boolean gamePaused;

    /**
     * Creates the audio system with the built-in buses.
     *
     * @param platform the voice pool
     * @param assets for clips of sounds
     * @param events where {@code MusicEndEvent} goes
     * @param preferences where bus volumes are kept
     * @param camera position of the display camera, the default listener
     * @param logger receives playback problems
     */
    public AudioImpl(
            PlatformAudio platform,
            AssetsImpl assets,
            EventBus events,
            Preferences preferences,
            Supplier<Vec2> camera,
            Logger logger) {
        this.platform = platform;
        this.assets = assets;
        this.events = events;
        this.preferences = preferences;
        this.camera = camera;
        this.logger = logger;
        this.master = new BusImpl(MASTER, null, preferences);
        buses.put(MASTER, master);
        busList.add(master);
        createBus(MUSIC);
        createBus(SFX);
        createBus(UI);
        createBus(VOICE).duck(MUSIC, 0.35f);
        this.music = new MusicPlayerImpl(this);
    }

    /**
     * Registers the loaders of {@link AssetType#AUDIO} and {@link AssetType#MUSIC}.
     *
     * @param decoders audio decoders
     */
    public void registerLoaders(PlatformDecoders decoders) {
        assets.registerLoader(AssetType.AUDIO, new AssetLoader<>() {
            @Override
            public Promise<AudioClip> load(dev.gulp.api.asset.AssetLoadContext context) {
                String name = context.key().key().toString();
                return context.bytes().flatMap(bytes -> {
                    PromiseImpl<AudioClip> promise = assets.newPromise();
                    decoders.decodeAudio(direct(bytes), new PlatformCallback<>() {
                        @Override
                        public void success(DecodedAudio decoded) {
                            int channels = decoded.channels();
                            long frames = decoded.samples().remaining() / channels;
                            int buffer = platform.createBuffer(decoded.samples(), channels, decoded.sampleRate());
                            promise.complete(new AudioClipImpl(name, buffer, frames, channels, decoded.sampleRate()));
                        }

                        @Override
                        public void failure(Throwable error) {
                            promise.fail(error);
                        }
                    });
                    return promise;
                });
            }

            @Override
            public void dispose(AudioClip asset) {
                if (asset instanceof AudioClipImpl clip && !clip.disposed) {
                    clip.disposed = true;
                    for (int i = active.size() - 1; i >= 0; i--) {
                        if (active.get(i) instanceof ClipVoice voice && voice.clip == clip) {
                            finish(voice, false);
                        }
                    }
                    platform.deleteBuffer(clip.buffer);
                }
            }
        });
        assets.registerLoader(AssetType.MUSIC, new AssetLoader<>() {
            @Override
            public Promise<Music> load(dev.gulp.api.asset.AssetLoadContext context) {
                String name = context.key().key().toString();
                return context.bytes().flatMap(bytes -> {
                    PromiseImpl<Music> promise = assets.newPromise();
                    ByteBuffer encoded = direct(bytes);
                    decoders.openAudioStream(encoded.duplicate(), new PlatformCallback<>() {
                        @Override
                        public void success(PlatformAudioStream stream) {
                            promise.complete(new MusicImpl(name, encoded, decoders, stream));
                        }

                        @Override
                        public void failure(Throwable error) {
                            promise.fail(error);
                        }
                    });
                    return promise;
                });
            }

            @Override
            public void dispose(Music asset) {
                if (asset instanceof MusicImpl track) {
                    for (int i = active.size() - 1; i >= 0; i--) {
                        if (active.get(i) instanceof StreamVoice voice && voice.music == track) {
                            finish(voice, false);
                        }
                    }
                    track.dispose();
                }
            }
        });
    }

    private static ByteBuffer direct(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        buffer.put(bytes).flip();
        return buffer;
    }

    /** Reads bus volumes from preferences; called once they are loaded. */
    public void loadSettings() {
        for (BusImpl bus : busList) {
            bus.loadSettings();
        }
    }

    /**
     * Returns the files of registered sounds, so they load with the startup group.
     *
     * @param sounds the registered sounds
     * @return the clip keys
     */
    public static List<AssetKey<AudioClip>> filesOf(Iterable<Sound> sounds) {
        List<AssetKey<AudioClip>> files = new ArrayList<>();
        for (Sound sound : sounds) {
            files.addAll(sound.files());
        }
        return files;
    }

    // ------------------------------------------------------------------ frame

    /**
     * Advances fades, applies volumes and pause, feeds streams and frees ended voices.
     *
     * @param seconds time since the last frame
     * @param paused whether the game is paused
     */
    public void update(float seconds, boolean paused) {
        time += seconds;
        gamePaused = paused;
        Vec2 position = listener != null ? listener : camera.get();
        listenerX = position.x();
        listenerY = position.y();
        for (int i = 0; i < busList.size(); i++) {
            BusImpl bus = busList.get(i);
            bus.playing = 0;
            bus.duckTarget = 1f;
        }
        for (int i = 0; i < active.size(); i++) {
            active.get(i).bus.playing++;
        }
        for (int i = 0; i < busList.size(); i++) {
            BusImpl bus = busList.get(i);
            if (bus.playing > 0 && !bus.ducks.isEmpty()) {
                for (Map.Entry<String, Float> duck : bus.ducks.entrySet()) {
                    BusImpl target = buses.get(duck.getKey());
                    if (target != null) {
                        target.duckTarget = Math.min(target.duckTarget, duck.getValue());
                    }
                }
            }
        }
        for (int i = 0; i < busList.size(); i++) {
            busList.get(i).updateDuck(seconds);
        }
        for (int i = active.size() - 1; i >= 0; i--) {
            if (i >= active.size()) {
                continue;
            }
            Voice voice = active.get(i);
            voice.advanceFade(seconds);
            if (voice.stopAfterFade && voice.fade <= 0f) {
                finish(voice, false);
                continue;
            }
            apply(voice);
            if (!voice.poll()) {
                finish(voice, true);
            }
        }
        music.update();
    }

    private void apply(Voice voice) {
        boolean pause = voice.userPaused || (gamePaused && voice.pauseMode == PauseMode.GAME);
        if (pause != voice.platformPaused) {
            voice.platformPaused = pause;
            platform.setPaused(voice.handle, pause);
        }
        float gain = voice.volume * voice.fade * voice.bus.effectiveVolume();
        float pan = 0f;
        if (voice.positioned) {
            float dx = voice.x - listenerX;
            float dy = voice.y - listenerY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > voice.minDistance) {
                gain *= Math.max(0f, 1f - (distance - voice.minDistance) / (voice.maxDistance - voice.minDistance));
            }
            pan = Math.max(-1f, Math.min(1f, dx / (voice.maxDistance * 0.5f)));
        }
        voice.gain = gain;
        if (gain != voice.appliedGain) {
            voice.appliedGain = gain;
            platform.setGain(voice.handle, gain);
        }
        if (pan != voice.appliedPan) {
            voice.appliedPan = pan;
            platform.setPan(voice.handle, pan);
        }
        if (voice.pitch != voice.appliedPitch) {
            voice.appliedPitch = voice.pitch;
            platform.setPitch(voice.handle, voice.pitch);
        }
        float lowpass = voice.bus.effectiveLowpass();
        float highpass = voice.bus.effectiveHighpass();
        if (lowpass != voice.appliedLowpass || highpass != voice.appliedHighpass) {
            voice.appliedLowpass = lowpass;
            voice.appliedHighpass = highpass;
            platform.setFilter(voice.handle, lowpass, highpass);
        }
        float reverb = voice.bus.effectiveReverb();
        if (reverb != voice.appliedReverb) {
            voice.appliedReverb = reverb;
            platform.setReverb(voice.handle, reverb);
        }
    }

    void finish(Voice voice, boolean natural) {
        if (voice.ended) {
            return;
        }
        voice.ended = true;
        active.remove(voice);
        voice.release();
        if (voice instanceof StreamVoice stream && stream.music != null) {
            music.ended(stream, natural);
        }
    }

    // ------------------------------------------------------------------ starting voices

    private int acquire(int priority) {
        if (active.size() >= MAX_VOICES && !steal(priority)) {
            return -1;
        }
        int handle = platform.acquireVoice();
        if (handle < 0 && steal(priority)) {
            handle = platform.acquireVoice();
        }
        return handle;
    }

    /** Stops the quietest, oldest voice of the lowest priority, unless it outranks the new sound. */
    private boolean steal(int priority) {
        Voice victim = null;
        for (int i = 0; i < active.size(); i++) {
            Voice voice = active.get(i);
            if (victim == null
                    || voice.priority < victim.priority
                    || (voice.priority == victim.priority
                            && (voice.gain < victim.gain
                                    || (voice.gain == victim.gain && voice.order < victim.order)))) {
                victim = voice;
            }
        }
        if (victim == null || victim.priority > priority) {
            return false;
        }
        finish(victim, false);
        return true;
    }

    private @Nullable ClipVoice startClip(
            AudioClipImpl clip,
            BusImpl bus,
            @Nullable Sound sound,
            float volume,
            float pitch,
            int priority,
            PauseMode pauseMode,
            boolean positioned,
            float x,
            float y) {
        int handle = acquire(priority);
        if (handle < 0) {
            return null;
        }
        ClipVoice voice =
                new ClipVoice(this, platform, handle, bus, clip, sound, pauseMode, priority, order++, volume, pitch);
        if (positioned && sound != null) {
            voice.positioned = true;
            voice.x = x;
            voice.y = y;
            voice.minDistance = sound.minDistance();
            voice.maxDistance = sound.maxDistance();
        }
        active.add(voice);
        apply(voice);
        platform.play(handle, clip.buffer, false);
        return voice;
    }

    @Nullable StreamVoice startMusic(StreamVoice.MusicSource source, MusicImpl track, float volume) {
        int handle = acquire(MUSIC_PRIORITY);
        if (handle < 0) {
            source.close();
            return null;
        }
        StreamVoice voice = new StreamVoice(
                this,
                platform,
                handle,
                busOrThrow(MUSIC),
                source,
                track,
                PauseMode.ALWAYS,
                MUSIC_PRIORITY,
                order++,
                volume);
        active.add(voice);
        apply(voice);
        voice.poll();
        return voice;
    }

    void loadMusic(AssetKey<Music> key, Consumer<Music> then) {
        assets.load(key).thenSync(then).onFailure(error -> logger.error("Could not load music " + key.key(), error));
    }

    void fire(Event event) {
        if (events.hasListeners(event.getClass())) {
            events.call(event);
        }
    }

    Logger logger() {
        return logger;
    }

    float randomFloat() {
        random ^= random << 13;
        random ^= random >>> 7;
        random ^= random << 17;
        return (random >>> 40) / (float) (1 << 24);
    }

    int randomInt(int bound) {
        return Math.min(bound - 1, (int) (randomFloat() * bound));
    }

    // ------------------------------------------------------------------ Audio

    @Override
    public Playback play(Sound sound) {
        return play(sound, 1f, 1f, false, 0f, 0f);
    }

    @Override
    public Playback play(Sound sound, float volume, float pitch) {
        return play(sound, volume, pitch, false, 0f, 0f);
    }

    @Override
    public Playback playAt(Sound sound, float x, float y) {
        return play(sound, 1f, 1f, true, x, y);
    }

    private Playback play(Sound sound, float volume, float pitch, boolean positioned, float x, float y) {
        if (!platform.isUnlocked()) {
            return SKIPPED;
        }
        float[] lastStart = lastStarts.get(sound);
        if (lastStart == null) {
            lastStart = new float[] {Float.NEGATIVE_INFINITY};
            lastStarts.put(sound, lastStart);
        }
        if (time - lastStart[0] < sound.minInterval()) {
            return SKIPPED;
        }
        if (sound.maxInstances() > 0) {
            int playing = 0;
            for (int i = 0; i < active.size(); i++) {
                if (sound.equals(active.get(i).sound)) {
                    playing++;
                }
            }
            if (playing >= sound.maxInstances()) {
                return SKIPPED;
            }
        }
        List<AssetKey<AudioClip>> files = sound.files();
        AssetKey<AudioClip> file = files.get(files.size() == 1 ? 0 : randomInt(files.size()));
        AudioClip clip = assets.getIfLoaded(file);
        if (!(clip instanceof AudioClipImpl loaded)) {
            if (warned.add(file)) {
                logger.warn("Sound " + sound.key() + " played before " + file.key()
                        + " was loaded; register sounds in onLoad so their files load with the startup group");
                assets.load(file);
            }
            return SKIPPED;
        }
        BusImpl bus = busOrThrow(sound.bus());
        float v = lerp(sound.minVolume(), sound.maxVolume()) * volume;
        float p = lerp(sound.minPitch(), sound.maxPitch()) * pitch;
        ClipVoice voice = startClip(loaded, bus, sound, v, p, sound.priority(), sound.pauseMode(), positioned, x, y);
        if (voice == null) {
            return SKIPPED;
        }
        lastStart[0] = time;
        return voice;
    }

    private float lerp(float min, float max) {
        return min == max ? min : min + (max - min) * randomFloat();
    }

    @Override
    public Playback play(AudioClip clip, String bus) {
        if (!platform.isUnlocked() || !(clip instanceof AudioClipImpl loaded) || loaded.disposed) {
            return SKIPPED;
        }
        BusImpl target = busOrThrow(bus);
        PauseMode mode = UI.equals(bus) ? PauseMode.ALWAYS : PauseMode.GAME;
        ClipVoice voice = startClip(loaded, target, null, 1f, 1f, 0, mode, false, 0f, 0f);
        return voice != null ? voice : SKIPPED;
    }

    @Override
    public Playback stream(PcmSource source, String bus) {
        BusImpl target = busOrThrow(bus);
        StreamVoice.PcmAdapter adapter = new StreamVoice.PcmAdapter(source);
        if (!platform.isUnlocked()) {
            return SKIPPED;
        }
        int handle = acquire(0);
        if (handle < 0) {
            return SKIPPED;
        }
        PauseMode mode = UI.equals(bus) ? PauseMode.ALWAYS : PauseMode.GAME;
        StreamVoice voice = new StreamVoice(this, platform, handle, target, adapter, null, mode, 0, order++, 1f);
        active.add(voice);
        apply(voice);
        voice.poll();
        return voice;
    }

    @Override
    public MusicPlayer music() {
        return music;
    }

    private BusImpl busOrThrow(String name) {
        BusImpl bus = buses.get(name);
        if (bus == null) {
            throw new IllegalArgumentException("No bus named '" + name + "'; create it with audio().createBus");
        }
        return bus;
    }

    @Override
    public Bus bus(String name) {
        return busOrThrow(name);
    }

    @Override
    public Bus createBus(String name) {
        BusImpl existing = buses.get(name);
        if (existing != null) {
            return existing;
        }
        if (!name.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid bus name '" + name + "': use [a-z0-9_.-]+");
        }
        BusImpl bus = new BusImpl(name, master, preferences);
        bus.loadSettings();
        buses.put(name, bus);
        busList.add(bus);
        return bus;
    }

    @Override
    public List<Bus> buses() {
        return new ArrayList<>(busList);
    }

    @Override
    public void setListener(@Nullable Vec2 position) {
        listener = position;
    }

    @Override
    public Vec2 listener() {
        return listener != null ? listener : camera.get();
    }

    @Override
    public boolean isUnlocked() {
        return platform.isUnlocked();
    }

    @Override
    public int activeVoices() {
        return active.size();
    }

    @Override
    public int maxVoices() {
        return MAX_VOICES;
    }

    @Override
    public void stopAll() {
        for (int i = active.size() - 1; i >= 0; i--) {
            if (i < active.size() && active.get(i).priority != MUSIC_PRIORITY) {
                finish(active.get(i), false);
            }
        }
    }

    /** Stops everything, music included, when the engine stops. */
    public void dispose() {
        music.stop(0f);
        for (int i = active.size() - 1; i >= 0; i--) {
            finish(active.get(i), false);
        }
    }

    /** A playback that never started. */
    private static final class SkippedPlayback implements Playback {
        @Override
        public boolean isPlaying() {
            return false;
        }

        @Override
        public void stop() {}

        @Override
        public void fadeOut(float seconds) {}

        @Override
        public float volume() {
            return 0f;
        }

        @Override
        public Playback setVolume(float volume) {
            return this;
        }

        @Override
        public float pitch() {
            return 1f;
        }

        @Override
        public Playback setPitch(float pitch) {
            return this;
        }

        @Override
        public boolean isLooping() {
            return false;
        }

        @Override
        public Playback setLooping(boolean looping) {
            return this;
        }

        @Override
        public Playback setPosition(float x, float y) {
            return this;
        }
    }
}
