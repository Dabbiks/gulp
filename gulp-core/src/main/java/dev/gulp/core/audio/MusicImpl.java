package dev.gulp.core.audio;

import dev.gulp.api.audio.Music;
import dev.gulp.platform.PlatformAudioStream;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * A music track: the encoded file stays in memory and is decoded while playing. One decoder is kept open between plays;
 * a second one opens when the track plays twice at once (crossfading into itself).
 */
final class MusicImpl implements Music {

    private final String name;
    private final ByteBuffer encoded;
    private final PlatformDecoders decoders;
    private final long frames;
    private final int channels;
    private final int sampleRate;
    private boolean looping = true;
    private long loopStart;
    private long loopEnd;
    private @Nullable PlatformAudioStream spare;
    private boolean disposed;

    MusicImpl(String name, ByteBuffer encoded, PlatformDecoders decoders, PlatformAudioStream first) {
        this.name = name;
        this.encoded = encoded;
        this.decoders = decoders;
        this.frames = first.frames();
        this.channels = first.channels();
        this.sampleRate = first.sampleRate();
        this.loopEnd = frames;
        this.spare = first;
    }

    void takeStream(Consumer<PlatformAudioStream> consumer, Consumer<Throwable> failed) {
        PlatformAudioStream stream = spare;
        if (stream != null) {
            spare = null;
            stream.seek(0);
            consumer.accept(stream);
            return;
        }
        decoders.openAudioStream(encoded.duplicate(), new PlatformCallback<>() {
            @Override
            public void success(PlatformAudioStream value) {
                consumer.accept(value);
            }

            @Override
            public void failure(Throwable error) {
                failed.accept(error);
            }
        });
    }

    void giveBack(PlatformAudioStream stream) {
        if (spare == null && !disposed) {
            spare = stream;
        } else {
            stream.close();
        }
    }

    void dispose() {
        disposed = true;
        PlatformAudioStream stream = spare;
        spare = null;
        if (stream != null) {
            stream.close();
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public float duration() {
        return frames / (float) sampleRate;
    }

    @Override
    public long frames() {
        return frames;
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public Music setLooping(boolean on) {
        looping = on;
        return this;
    }

    @Override
    public long loopStart() {
        return loopStart;
    }

    @Override
    public long loopEnd() {
        return loopEnd;
    }

    @Override
    public Music setLoopPoints(long start, long end) {
        if (start < 0 || end > frames || start >= end) {
            throw new IllegalArgumentException(
                    "Loop points " + start + ".." + end + " must satisfy 0 <= start < end <= " + frames);
        }
        loopStart = start;
        loopEnd = end;
        return this;
    }

    @Override
    public String toString() {
        return "Music[" + name + ", " + duration() + " s]";
    }
}
