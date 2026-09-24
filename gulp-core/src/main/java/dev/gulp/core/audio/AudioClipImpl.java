package dev.gulp.core.audio;

import dev.gulp.api.audio.AudioClip;

/** A clip uploaded to a platform buffer. */
final class AudioClipImpl implements AudioClip {

    final String name;
    final int buffer;
    private final long frames;
    private final int channels;
    private final int sampleRate;
    boolean disposed;

    AudioClipImpl(String name, int buffer, long frames, int channels, int sampleRate) {
        this.name = name;
        this.buffer = buffer;
        this.frames = frames;
        this.channels = channels;
        this.sampleRate = sampleRate;
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
    public String toString() {
        return "AudioClip[" + name + ", " + duration() + " s]";
    }
}
