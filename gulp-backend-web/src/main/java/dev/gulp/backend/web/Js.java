package dev.gulp.backend.web;

import org.jspecify.annotations.Nullable;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.ArrayBufferView;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Int16Array;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.webgl.WebGL2RenderingContext;

/** Calls into {@code gulp-runtime.js} ({@code window.gulp}) and a few browser globals. */
final class Js {

    private Js() {}

    /** No arguments. */
    @JSFunctor
    interface Callback extends JSObject {
        void call();
    }

    /** A boolean. */
    @JSFunctor
    interface BooleanCallback extends JSObject {
        void call(boolean value);
    }

    /** A string. */
    @JSFunctor
    interface StringCallback extends JSObject {
        void call(String value);
    }

    /** An array buffer. */
    @JSFunctor
    interface BytesCallback extends JSObject {
        void call(ArrayBuffer bytes);
    }

    /** A decoded image. */
    @JSFunctor
    interface ImageCallback extends JSObject {
        void call(int width, int height, Int8Array rgba);
    }

    /** A frame from requestAnimationFrame. */
    @JSFunctor
    interface FrameCallback extends JSObject {
        void call(double timestamp);
    }

    /** A key event. */
    @JSFunctor
    interface KeyCallback extends JSObject {
        void call(int keyCode, int scanCode, int modifiers, boolean down, boolean repeat);
    }

    /** A code point. */
    @JSFunctor
    interface TextCallback extends JSObject {
        void call(int codePoint);
    }

    /** Mouse movement. */
    @JSFunctor
    interface MoveCallback extends JSObject {
        void call(double x, double y, double deltaX, double deltaY);
    }

    /** A mouse button. */
    @JSFunctor
    interface ButtonCallback extends JSObject {
        void call(int button, boolean down, int modifiers);
    }

    /** A wheel movement. */
    @JSFunctor
    interface WheelCallback extends JSObject {
        void call(double deltaX, double deltaY);
    }

    /** A touch point. */
    @JSFunctor
    interface TouchCallback extends JSObject {
        void call(int pointer, int phase, double x, double y);
    }

    @JSBody(script = "return window.gulp.init();")
    static native @Nullable WebGL2RenderingContext init();

    @JSBody(script = "return window.gulp.width;")
    static native int width();

    @JSBody(script = "return window.gulp.height;")
    static native int height();

    @JSBody(script = "return window.gulp.canvas.width;")
    static native int framebufferWidth();

    @JSBody(script = "return window.gulp.canvas.height;")
    static native int framebufferHeight();

    @JSBody(script = "return window.gulp.dpr;")
    static native double devicePixelRatio();

    @JSBody(params = "handler", script = "window.gulp.onResize(handler);")
    static native void onResize(Callback handler);

    @JSBody(params = "handler", script = "window.gulp.onFocus(handler);")
    static native void onFocus(BooleanCallback handler);

    @JSBody(script = "return window.gulp.isFocused();")
    static native boolean isFocused();

    @JSBody(params = "on", script = "window.gulp.setFullscreen(on);")
    static native void setFullscreen(boolean on);

    @JSBody(script = "return window.gulp.isFullscreen();")
    static native boolean isFullscreen();

    @JSBody(params = "mode", script = "window.gulp.setCursor(mode);")
    static native void setCursor(int mode);

    @JSBody(params = "title", script = "document.title = title;")
    static native void setTitle(String title);

    @JSBody(
            params = {"key", "text", "move", "button", "wheel", "touch"},
            script = "window.gulp.input(key, text, move, button, wheel, touch);")
    static native void input(
            KeyCallback key,
            TextCallback text,
            MoveCallback move,
            ButtonCallback button,
            WheelCallback wheel,
            TouchCallback touch);

    @JSBody(params = "callback", script = "return requestAnimationFrame(callback);")
    static native int requestAnimationFrame(FrameCallback callback);

    @JSBody(script = "return performance.now();")
    static native double now();

    @JSBody(params = "callback", script = "setTimeout(callback, 0);")
    static native void later(Callback callback);

    @JSBody(
            params = {"url", "ok", "missing", "fail"},
            script = "window.gulp.fetchBytes(url, ok, missing, fail);")
    static native void fetchBytes(String url, BytesCallback ok, Callback missing, StringCallback fail);

    @JSBody(
            params = {"ok", "missing"},
            script = "window.gulp.loadManifest(ok, missing);")
    static native void loadManifest(StringCallback ok, Callback missing);

    @JSBody(
            params = {"bytes", "ok", "fail"},
            script = "window.gulp.decodeImage(bytes, ok, fail);")
    static native void decodeImage(Int8Array bytes, ImageCallback ok, StringCallback fail);

    @JSBody(params = "name", script = "window.gulp.dbName = name;")
    static native void setDatabaseName(String name);

    @JSBody(
            params = {"name", "ok", "missing", "fail"},
            script = "window.gulp.readData(name, ok, missing, fail);")
    static native void readData(String name, BytesCallback ok, Callback missing, StringCallback fail);

    @JSBody(
            params = {"name", "bytes", "ok", "fail"},
            script = "window.gulp.writeData(name, bytes, ok, fail);")
    static native void writeData(String name, Int8Array bytes, Callback ok, StringCallback fail);

    @JSBody(
            params = {"name", "ok", "fail"},
            script = "window.gulp.deleteData(name, ok, fail);")
    static native void deleteData(String name, Callback ok, StringCallback fail);

    @JSBody(
            params = {"prefix", "ok", "fail"},
            script = "window.gulp.listData(prefix, ok, fail);")
    static native void listData(String prefix, StringCallback ok, StringCallback fail);

    @JSBody(script = "window.gulp.ready();")
    static native void ready();

    @JSBody(params = "message", script = "window.gulp.fatal(message);")
    static native void fatal(String message);

    @JSBody(params = "handler", script = "window.gulp.command = handler;")
    static native void onCommand(StringCallback handler);

    @JSBody(params = "message", script = "console.log(message);")
    static native void log(String message);

    @JSBody(params = "message", script = "console.warn(message);")
    static native void warn(String message);

    @JSBody(params = "message", script = "console.error(message);")
    static native void error(String message);

    @JSBody(script = "return navigator.language || 'en';")
    static native String language();

    @JSBody(script = "return navigator.userAgent;")
    static native String userAgent();

    @JSBody(script = "return navigator.platform || '';")
    static native String platform();

    @JSBody(script = "return screen.width;")
    static native int screenWidth();

    @JSBody(script = "return screen.height;")
    static native int screenHeight();

    @JSBody(
            script = "return location.hostname === 'localhost' || location.hostname === '127.0.0.1'"
                    + " || new URLSearchParams(location.search).has('dev');")
    static native boolean isDevelopment();

    @JSBody(
            params = {"gl", "name"},
            script = "var v = gl.getParameter(name); if (name === 0x1F01) {"
                    + " var e = gl.getExtension('WEBGL_debug_renderer_info');"
                    + " if (e) v = gl.getParameter(e.UNMASKED_RENDERER_WEBGL); }"
                    + " return v == null ? null : String(v);")
    static native @Nullable String parameterString(WebGL2RenderingContext gl, int name);

    @JSBody(params = "value", script = "return value === true ? 1 : value === false ? 0 : (value | 0);")
    static native int parameterInt(JSObject value);

    @JSBody(params = "bytes", script = "return new Int8Array(bytes);")
    static native Int8Array bytes(ArrayBuffer bytes);

    @JSBody(params = "view", script = "return new Uint8Array(view.buffer, view.byteOffset, view.byteLength);")
    static native ArrayBufferView unsigned(ArrayBufferView view);

    @JSBody(
            params = {"bytes", "ok", "fail"},
            script = "window.gulp.openFont(bytes, ok, fail);")
    static native void openFont(Int8Array bytes, StringCallback ok, StringCallback fail);

    @JSBody(
            params = {"family", "size"},
            script = "return window.gulp.fontMetrics(family, size);")
    static native Float32Array fontMetrics(String family, float size);

    @JSBody(
            params = {"family", "size", "text"},
            script = "return window.gulp.measureText(family, size, text);")
    static native double measure(String family, float size, String text);

    @JSBody(
            params = {"family", "codePoint", "size"},
            script = "window.gulp.rasterize(family, codePoint, size);")
    static native void rasterize(String family, int codePoint, float size);

    @JSBody(script = "return window.gulp.lastGlyph.header;")
    static native Float32Array lastGlyphHeader();

    @JSBody(script = "return window.gulp.lastGlyph.data;")
    static native Int8Array lastGlyphData();

    /** Decoded audio: channels, rate and interleaved samples. */
    @JSFunctor
    interface AudioCallback extends JSObject {
        void call(int channels, int sampleRate, Int16Array samples);
    }

    /** An opened stream: id, channels, rate and length in frames. */
    @JSFunctor
    interface StreamCallback extends JSObject {
        void call(int id, int channels, int sampleRate, double frames);
    }

    @JSBody(params = "shape", script = "window.gulp.setSystemCursor(shape);")
    static native void setSystemCursor(int shape);

    @JSBody(
            params = {"width", "height", "rgba", "hotX", "hotY"},
            script = "window.gulp.setCustomCursor(width, height, rgba, hotX, hotY);")
    static native void setCustomCursor(int width, int height, Int8Array rgba, int hotX, int hotY);

    @JSBody(
            params = {"active", "x", "y", "width", "height"},
            script = "window.gulp.setTextInput(active, x, y, width, height);")
    static native void setTextInput(boolean active, float x, float y, float width, float height);

    @JSBody(script = "window.gulp.pollPads();")
    static native void pollPads();

    @JSBody(params = "i", script = "return window.gulp.padConnected(i);")
    static native boolean padConnected(int i);

    @JSBody(params = "i", script = "return window.gulp.padName(i);")
    static native String padName(int i);

    @JSBody(
            params = {"i", "axis"},
            script = "return window.gulp.padAxis(i, axis);")
    static native double padAxis(int i, int axis);

    @JSBody(
            params = {"i", "button"},
            script = "return window.gulp.padButton(i, button);")
    static native boolean padButton(int i, int button);

    @JSBody(params = "i", script = "return window.gulp.padRumbles(i);")
    static native boolean padRumbles(int i);

    @JSBody(
            params = {"i", "weak", "strong", "millis"},
            script = "return window.gulp.padRumble(i, weak, strong, millis);")
    static native boolean padRumble(int i, float weak, float strong, int millis);

    @JSBody(params = "ok", script = "window.gulp.readClipboard(ok);")
    static native void readClipboard(StringCallback ok);

    @JSBody(params = "text", script = "window.gulp.writeClipboard(text);")
    static native void writeClipboard(String text);

    @JSBody(params = "count", script = "return window.gulp.audioInit(count);")
    static native boolean audioInit(int count);

    @JSBody(script = "return window.gulp.audioUnlocked();")
    static native boolean audioUnlocked();

    @JSBody(
            params = {"samples", "channels", "rate"},
            script = "return window.gulp.audioBuffer(samples, channels, rate);")
    static native int audioBuffer(Int16Array samples, int channels, int rate);

    @JSBody(params = "id", script = "window.gulp.deleteAudioBuffer(id);")
    static native void deleteAudioBuffer(int id);

    @JSBody(
            params = {"i", "id", "loop"},
            script = "window.gulp.voicePlay(i, id, loop);")
    static native void voicePlay(int voice, int buffer, boolean loop);

    @JSBody(
            params = {"i", "id"},
            script = "window.gulp.voiceQueue(i, id);")
    static native void voiceQueue(int voice, int buffer);

    @JSBody(params = "i", script = "return window.gulp.voiceUnqueue(i);")
    static native int voiceUnqueue(int voice);

    @JSBody(
            params = {"i", "paused"},
            script = "window.gulp.voicePause(i, paused);")
    static native void voicePause(int voice, boolean paused);

    @JSBody(
            params = {"i", "loop"},
            script = "window.gulp.voiceLooping(i, loop);")
    static native void voiceLooping(int voice, boolean loop);

    @JSBody(params = "i", script = "window.gulp.voiceStop(i);")
    static native void voiceStop(int voice);

    @JSBody(params = "i", script = "return window.gulp.voicePlaying(i);")
    static native boolean voicePlaying(int voice);

    @JSBody(
            params = {"i", "value"},
            script = "window.gulp.voiceGain(i, value);")
    static native void voiceGain(int voice, float value);

    @JSBody(
            params = {"i", "value"},
            script = "window.gulp.voicePitch(i, value);")
    static native void voicePitch(int voice, float value);

    @JSBody(
            params = {"i", "value"},
            script = "window.gulp.voicePan(i, value);")
    static native void voicePan(int voice, float value);

    @JSBody(
            params = {"i", "low", "high"},
            script = "window.gulp.voiceFilter(i, low, high);")
    static native void voiceFilter(int voice, float low, float high);

    @JSBody(
            params = {"i", "value"},
            script = "window.gulp.voiceReverb(i, value);")
    static native void voiceReverb(int voice, float value);

    @JSBody(params = "value", script = "window.gulp.masterGain(value);")
    static native void masterGain(float value);

    @JSBody(
            params = {"bytes", "ok", "fail"},
            script = "window.gulp.decodeAudio(bytes, ok, fail);")
    static native void decodeAudio(Int8Array bytes, AudioCallback ok, StringCallback fail);

    @JSBody(
            params = {"bytes", "ok", "fail"},
            script = "window.gulp.openStream(bytes, ok, fail);")
    static native void openStream(Int8Array bytes, StreamCallback ok, StringCallback fail);

    @JSBody(
            params = {"id", "frames"},
            script = "return window.gulp.streamRead(id, frames);")
    static native Int16Array streamRead(int id, int frames);

    @JSBody(
            params = {"id", "frame"},
            script = "window.gulp.streamSeek(id, frame);")
    static native void streamSeek(int id, double frame);

    @JSBody(params = "id", script = "window.gulp.streamClose(id);")
    static native void streamClose(int id);
}
