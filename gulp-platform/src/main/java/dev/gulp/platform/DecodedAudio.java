package dev.gulp.platform;

import java.nio.ShortBuffer;

/**
 * Audio decoded to interleaved 16-bit PCM.
 *
 * <pre>{@code
 * int buffer = audio.createBuffer(decoded.samples(), decoded.channels(), decoded.sampleRate());
 * }</pre>
 *
 * @param channels 1 or 2
 * @param sampleRate samples per second per channel
 * @param samples interleaved samples, direct and in native order
 */
public record DecodedAudio(int channels, int sampleRate, ShortBuffer samples) {}
