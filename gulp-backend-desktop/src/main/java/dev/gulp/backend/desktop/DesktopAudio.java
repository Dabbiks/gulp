package dev.gulp.backend.desktop;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED;
import static org.lwjgl.openal.AL10.AL_CHANNELS;
import static org.lwjgl.openal.AL10.AL_FALSE;
import static org.lwjgl.openal.AL10.AL_FORMAT_MONO16;
import static org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;
import static org.lwjgl.openal.AL10.AL_GAIN;
import static org.lwjgl.openal.AL10.AL_LOOPING;
import static org.lwjgl.openal.AL10.AL_PAUSED;
import static org.lwjgl.openal.AL10.AL_PITCH;
import static org.lwjgl.openal.AL10.AL_PLAYING;
import static org.lwjgl.openal.AL10.AL_POSITION;
import static org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.AL_TRUE;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDeleteBuffers;
import static org.lwjgl.openal.AL10.alDeleteSources;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetBufferi;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alListenerf;
import static org.lwjgl.openal.AL10.alSource3f;
import static org.lwjgl.openal.AL10.alSourcePause;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourceQueueBuffers;
import static org.lwjgl.openal.AL10.alSourceStop;
import static org.lwjgl.openal.AL10.alSourceUnqueueBuffers;
import static org.lwjgl.openal.AL10.alSourcef;
import static org.lwjgl.openal.AL10.alSourcei;
import static org.lwjgl.openal.ALC10.alcCloseDevice;
import static org.lwjgl.openal.ALC10.alcCreateContext;
import static org.lwjgl.openal.ALC10.alcDestroyContext;
import static org.lwjgl.openal.ALC10.alcIsExtensionPresent;
import static org.lwjgl.openal.ALC10.alcMakeContextCurrent;
import static org.lwjgl.openal.ALC10.alcOpenDevice;
import static org.lwjgl.openal.EXTEfx.ALC_MAX_AUXILIARY_SENDS;
import static org.lwjgl.openal.EXTEfx.AL_AUXILIARY_SEND_FILTER;
import static org.lwjgl.openal.EXTEfx.AL_BANDPASS_GAINHF;
import static org.lwjgl.openal.EXTEfx.AL_BANDPASS_GAINLF;
import static org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER;
import static org.lwjgl.openal.EXTEfx.AL_EFFECTSLOT_EFFECT;
import static org.lwjgl.openal.EXTEfx.AL_EFFECT_REVERB;
import static org.lwjgl.openal.EXTEfx.AL_EFFECT_TYPE;
import static org.lwjgl.openal.EXTEfx.AL_FILTER_BANDPASS;
import static org.lwjgl.openal.EXTEfx.AL_FILTER_LOWPASS;
import static org.lwjgl.openal.EXTEfx.AL_FILTER_NULL;
import static org.lwjgl.openal.EXTEfx.AL_FILTER_TYPE;
import static org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAIN;
import static org.lwjgl.openal.EXTEfx.AL_REVERB_DECAY_TIME;
import static org.lwjgl.openal.EXTEfx.alAuxiliaryEffectSloti;
import static org.lwjgl.openal.EXTEfx.alDeleteAuxiliaryEffectSlots;
import static org.lwjgl.openal.EXTEfx.alDeleteEffects;
import static org.lwjgl.openal.EXTEfx.alDeleteFilters;
import static org.lwjgl.openal.EXTEfx.alEffectf;
import static org.lwjgl.openal.EXTEfx.alEffecti;
import static org.lwjgl.openal.EXTEfx.alFilterf;
import static org.lwjgl.openal.EXTEfx.alFilteri;
import static org.lwjgl.openal.EXTEfx.alGenAuxiliaryEffectSlots;
import static org.lwjgl.openal.EXTEfx.alGenEffects;
import static org.lwjgl.openal.EXTEfx.alGenFilters;
import static org.lwjgl.system.MemoryUtil.NULL;

import dev.gulp.platform.PlatformAudio;
import java.nio.ShortBuffer;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * Voices on OpenAL Soft sources. Filters and reverb use the EFX extension: each source has a band-pass filter on its
 * direct path and a gain filter on its send to one shared reverb slot. EFX specifies filters by gain at a reference
 * frequency (5 kHz high, 250 Hz low), so cutoffs are mapped to those gains. Without an audio device every voice
 * request fails and the game stays silent.
 */
final class DesktopAudio implements PlatformAudio {

    /** Number of sources. */
    static final int VOICES = 32;

    private static final float HIGH_REFERENCE = 5000f;
    private static final float LOW_REFERENCE = 250f;

    private final long device;
    private final long context;
    private final boolean efx;
    private final int[] sources = new int[VOICES];
    private final int[] directFilters = new int[VOICES];
    private final int[] sendFilters = new int[VOICES];
    private final boolean[] used = new boolean[VOICES];
    private final boolean[] paused = new boolean[VOICES];
    private final boolean[] mono = new boolean[VOICES];
    private int reverbSlot;
    private int reverbEffect;
    private ShortBuffer scratch = MemoryUtil.memAllocShort(1);

    private DesktopAudio(long device, long context, boolean efx) {
        this.device = device;
        this.context = context;
        this.efx = efx;
        if (device == NULL) {
            return;
        }
        for (int i = 0; i < VOICES; i++) {
            sources[i] = alGenSources();
            alSourcei(sources[i], AL_SOURCE_RELATIVE, AL_TRUE);
            if (efx) {
                directFilters[i] = alGenFilters();
                alFilteri(directFilters[i], AL_FILTER_TYPE, AL_FILTER_BANDPASS);
                sendFilters[i] = alGenFilters();
                alFilteri(sendFilters[i], AL_FILTER_TYPE, AL_FILTER_LOWPASS);
            }
        }
        if (efx) {
            reverbEffect = alGenEffects();
            alEffecti(reverbEffect, AL_EFFECT_TYPE, AL_EFFECT_REVERB);
            alEffectf(reverbEffect, AL_REVERB_DECAY_TIME, 2.2f);
            reverbSlot = alGenAuxiliaryEffectSlots();
            alAuxiliaryEffectSloti(reverbSlot, AL_EFFECTSLOT_EFFECT, reverbEffect);
        }
    }

    /**
     * Opens the default audio device.
     *
     * @param log receives a warning when there is no device
     * @return the audio, silent without a device
     */
    static DesktopAudio open(DesktopLog log) {
        long device = NULL;
        try {
            device = alcOpenDevice((java.nio.ByteBuffer) null);
        } catch (Throwable error) {
            log.write(DesktopLog.WARN, "gulp", "OpenAL could not start; the game runs without sound", error);
        }
        if (device == NULL) {
            log.write(DesktopLog.WARN, "gulp", "No audio device; the game runs without sound", null);
            return new DesktopAudio(NULL, NULL, false);
        }
        ALCCapabilities capabilities = ALC.createCapabilities(device);
        boolean efx = alcIsExtensionPresent(device, "ALC_EXT_EFX");
        int[] attributes = efx ? new int[] {ALC_MAX_AUXILIARY_SENDS, 1, 0} : new int[] {0};
        long context = alcCreateContext(device, attributes);
        alcMakeContextCurrent(context);
        AL.createCapabilities(capabilities);
        return new DesktopAudio(device, context, efx);
    }

    void dispose() {
        if (device == NULL) {
            return;
        }
        for (int i = 0; i < VOICES; i++) {
            alSourceStop(sources[i]);
            alSourcei(sources[i], AL_BUFFER, 0);
        }
        alDeleteSources(sources);
        if (efx) {
            alDeleteFilters(directFilters);
            alDeleteFilters(sendFilters);
            alDeleteAuxiliaryEffectSlots(reverbSlot);
            alDeleteEffects(reverbEffect);
        }
        MemoryUtil.memFree(scratch);
        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }

    @Override
    public boolean isUnlocked() {
        return true;
    }

    @Override
    public int createBuffer(ShortBuffer samples, int channels, int sampleRate) {
        if (device == NULL) {
            return 0;
        }
        int buffer = alGenBuffers();
        int format = channels == 2 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16;
        if (samples.isDirect()) {
            alBufferData(buffer, format, samples, sampleRate);
        } else {
            if (scratch.capacity() < samples.remaining()) {
                MemoryUtil.memFree(scratch);
                scratch = MemoryUtil.memAllocShort(samples.remaining());
            }
            scratch.clear();
            scratch.put(samples.duplicate()).flip();
            alBufferData(buffer, format, scratch, sampleRate);
        }
        return buffer;
    }

    @Override
    public void deleteBuffer(int buffer) {
        if (device != NULL && buffer != 0) {
            alDeleteBuffers(buffer);
        }
    }

    @Override
    public int acquireVoice() {
        if (device == NULL) {
            return -1;
        }
        for (int i = 0; i < VOICES; i++) {
            if (!used[i]) {
                used[i] = true;
                paused[i] = false;
                return i;
            }
        }
        return -1;
    }

    @Override
    public void releaseVoice(int voice) {
        int source = sources[voice];
        alSourceStop(source);
        alSourcei(source, AL_BUFFER, 0);
        alSourcei(source, AL_LOOPING, AL_FALSE);
        alSourcef(source, AL_GAIN, 1f);
        alSourcef(source, AL_PITCH, 1f);
        alSource3f(source, AL_POSITION, 0f, 0f, 0f);
        if (efx) {
            alSourcei(source, AL_DIRECT_FILTER, AL_FILTER_NULL);
            alSource3i(source, 0, AL_FILTER_NULL);
        }
        used[voice] = false;
        paused[voice] = false;
    }

    private void alSource3i(int source, int slot, int filter) {
        org.lwjgl.openal.AL11.alSource3i(source, AL_AUXILIARY_SEND_FILTER, slot, 0, filter);
    }

    private void rememberLayout(int voice, int buffer) {
        mono[voice] = alGetBufferi(buffer, AL_CHANNELS) != 2;
    }

    @Override
    public void play(int voice, int buffer, boolean loop) {
        int source = sources[voice];
        alSourceStop(source);
        alSourcei(source, AL_BUFFER, buffer);
        alSourcei(source, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
        rememberLayout(voice, buffer);
        paused[voice] = false;
        alSourcePlay(source);
    }

    @Override
    public void queue(int voice, int buffer) {
        int source = sources[voice];
        alSourceQueueBuffers(source, buffer);
        rememberLayout(voice, buffer);
        // A streaming source stops when it runs dry; queueing restarts it unless the game paused it.
        if (!paused[voice] && alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(source);
        }
    }

    @Override
    public int unqueueProcessed(int voice) {
        int source = sources[voice];
        return alGetSourcei(source, AL_BUFFERS_PROCESSED) > 0 ? alSourceUnqueueBuffers(source) : -1;
    }

    @Override
    public void setPaused(int voice, boolean pause) {
        int source = sources[voice];
        paused[voice] = pause;
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        if (pause && state == AL_PLAYING) {
            alSourcePause(source);
        } else if (!pause && state == AL_PAUSED) {
            alSourcePlay(source);
        }
    }

    @Override
    public void setLooping(int voice, boolean loop) {
        alSourcei(sources[voice], AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
    }

    @Override
    public void stop(int voice) {
        alSourceStop(sources[voice]);
    }

    @Override
    public boolean isPlaying(int voice) {
        int state = alGetSourcei(sources[voice], AL_SOURCE_STATE);
        return state == AL_PLAYING || state == AL_PAUSED || paused[voice];
    }

    @Override
    public void setGain(int voice, float gain) {
        alSourcef(sources[voice], AL_GAIN, gain);
    }

    @Override
    public void setPitch(int voice, float pitch) {
        alSourcef(sources[voice], AL_PITCH, Math.max(0.01f, pitch));
    }

    @Override
    public void setPan(int voice, float pan) {
        // Mono sources are placed on a half circle in front of the listener; stereo buffers keep their channels.
        if (mono[voice]) {
            float x = Math.max(-1f, Math.min(1f, pan));
            alSource3f(sources[voice], AL_POSITION, x, 0f, (float) -Math.sqrt(1f - x * x));
        }
    }

    @Override
    public void setFilter(int voice, float lowpassHertz, float highpassHertz) {
        if (!efx) {
            return;
        }
        int source = sources[voice];
        if (lowpassHertz <= 0f && highpassHertz <= 0f) {
            alSourcei(source, AL_DIRECT_FILTER, AL_FILTER_NULL);
            return;
        }
        float gainHigh = lowpassHertz > 0f ? clamp(lowpassHertz / HIGH_REFERENCE) : 1f;
        float gainLow = highpassHertz > 0f ? clamp(LOW_REFERENCE / highpassHertz) : 1f;
        int filter = directFilters[voice];
        alFilterf(filter, AL_BANDPASS_GAINHF, gainHigh);
        alFilterf(filter, AL_BANDPASS_GAINLF, gainLow);
        alSourcei(source, AL_DIRECT_FILTER, filter);
    }

    private static float clamp(float value) {
        return Math.max(0.01f, Math.min(1f, value));
    }

    @Override
    public void setReverb(int voice, float send) {
        if (!efx) {
            return;
        }
        if (send <= 0f) {
            alSource3i(sources[voice], 0, AL_FILTER_NULL);
            return;
        }
        int filter = sendFilters[voice];
        alFilterf(filter, AL_LOWPASS_GAIN, Math.min(1f, send));
        alSource3i(sources[voice], reverbSlot, filter);
    }

    @Override
    public void setMasterGain(float gain) {
        if (device != NULL) {
            alListenerf(AL_GAIN, gain);
        }
    }
}
