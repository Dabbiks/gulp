package dev.gulp.core.audio;

import dev.gulp.api.audio.PauseMode;
import dev.gulp.api.audio.PcmSource;
import dev.gulp.api.audio.Playback;
import dev.gulp.platform.PlatformAudio;
import dev.gulp.platform.PlatformAudioStream;
import java.nio.ShortBuffer;
import org.jspecify.annotations.Nullable;

/**
 * A voice fed with short buffers from a source: music or procedural PCM. Keeps {@value #QUEUED} buffers of {@value
 * #CHUNK_FRAMES} frames queued, refilled once per frame.
 */
final class StreamVoice extends Voice {

    static final int CHUNK_FRAMES = 4096;
    static final int QUEUED = 3;

    /** Where samples come from. */
    interface Source {
        int channels();

        int sampleRate();

        /**
         * Reads samples.
         *
         * @param out destination, filled from position to limit
         * @return frames read, {@code 0} if none are ready yet, {@code -1} at the end
         */
        int read(ShortBuffer out);

        /** Position in the track of the next frame read, for music. */
        long position();

        void close();
    }

    private final PlatformAudio platform;
    final Source source;
    final @Nullable MusicImpl music;
    private final ShortBuffer chunk;
    private final int[] buffers = new int[QUEUED];
    private final long[] starts = new long[QUEUED];
    private final int[] lengths = new int[QUEUED];
    private int head;
    private int queued;
    private boolean sourceEnded;
    private long played;

    StreamVoice(
            AudioImpl audio,
            PlatformAudio platform,
            int handle,
            BusImpl bus,
            Source source,
            @Nullable MusicImpl music,
            PauseMode pauseMode,
            int priority,
            long order,
            float volume) {
        super(audio, handle, bus, null, pauseMode, priority, order, volume, 1f);
        this.platform = platform;
        this.source = source;
        this.music = music;
        this.chunk = ShortBuffer.allocate(CHUNK_FRAMES * source.channels());
        this.played = source.position();
    }

    @Override
    boolean poll() {
        int processed;
        while (queued > 0 && (processed = platform.unqueueProcessed(handle)) != -1) {
            if (processed == buffers[head]) {
                played = starts[head] + lengths[head];
                head = (head + 1) % QUEUED;
                queued--;
            }
            platform.deleteBuffer(processed);
        }
        while (!sourceEnded && queued < QUEUED) {
            chunk.clear();
            long start = source.position();
            int frames = source.read(chunk);
            if (frames < 0) {
                sourceEnded = true;
                break;
            }
            if (frames == 0) {
                break;
            }
            chunk.flip();
            int buffer = platform.createBuffer(chunk, source.channels(), source.sampleRate());
            int slot = (head + queued) % QUEUED;
            buffers[slot] = buffer;
            starts[slot] = start;
            lengths[slot] = frames;
            queued++;
            platform.queue(handle, buffer);
        }
        return !(sourceEnded && queued == 0);
    }

    /** Position of the frame heard last. */
    long playedFrame() {
        return played;
    }

    @Override
    void release() {
        platform.releaseVoice(handle);
        for (int i = 0; i < queued; i++) {
            platform.deleteBuffer(buffers[(head + i) % QUEUED]);
        }
        queued = 0;
        source.close();
    }

    @Override
    public Playback setLooping(boolean on) {
        return this;
    }

    /** Procedural samples as floats, converted to 16-bit. */
    static final class PcmAdapter implements Source {
        private final PcmSource pcm;
        private final float[] scratch;
        private long position;

        PcmAdapter(PcmSource pcm) {
            if (pcm.channels() < 1 || pcm.channels() > 2 || pcm.sampleRate() <= 0) {
                throw new IllegalArgumentException("PCM source needs 1 or 2 channels and a positive rate, got "
                        + pcm.channels() + " and " + pcm.sampleRate());
            }
            this.pcm = pcm;
            this.scratch = new float[CHUNK_FRAMES * pcm.channels()];
        }

        @Override
        public int channels() {
            return pcm.channels();
        }

        @Override
        public int sampleRate() {
            return pcm.sampleRate();
        }

        @Override
        public int read(ShortBuffer out) {
            int frames = Math.min(CHUNK_FRAMES, out.remaining() / pcm.channels());
            int read = pcm.read(scratch, frames);
            if (read < 0) {
                return -1;
            }
            read = Math.min(read, frames);
            for (int i = 0; i < read * pcm.channels(); i++) {
                float value = Math.max(-1f, Math.min(1f, scratch[i]));
                out.put((short) (value * 32767f));
            }
            position += read;
            return read;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public void close() {
            // the game owns the source
        }
    }

    /** A music track with loop points. */
    static final class MusicSource implements Source {
        private final MusicImpl music;
        private final PlatformAudioStream stream;
        private final boolean looping;
        private long position;

        MusicSource(MusicImpl music, PlatformAudioStream stream, boolean looping) {
            this.music = music;
            this.stream = stream;
            this.looping = looping;
        }

        @Override
        public int channels() {
            return stream.channels();
        }

        @Override
        public int sampleRate() {
            return stream.sampleRate();
        }

        @Override
        public int read(ShortBuffer out) {
            int channels = stream.channels();
            int total = 0;
            int limit = out.limit();
            while (out.position() < limit) {
                long end = looping ? music.loopEnd() : music.frames();
                if (position >= end) {
                    if (!looping) {
                        break;
                    }
                    stream.seek(music.loopStart());
                    position = music.loopStart();
                    end = music.loopEnd();
                }
                int wanted = (int) Math.min((limit - out.position()) / channels, end - position);
                out.limit(out.position() + wanted * channels);
                int read = stream.read(out);
                out.limit(limit);
                if (read <= 0) {
                    if (!looping || position == music.loopStart()) {
                        position = end;
                        break;
                    }
                    position = end;
                    continue;
                }
                position += read;
                total += read;
            }
            return total == 0 ? -1 : total;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public void close() {
            music.giveBack(stream);
        }
    }
}
