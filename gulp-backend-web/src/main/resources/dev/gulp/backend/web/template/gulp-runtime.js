// Browser glue for the Gulp web backend. Java reaches it through @JSBody calls on window.gulp; everything here passes
// primitives, strings and ArrayBuffers only, so it works the same with the Wasm GC and JavaScript outputs.
(function () {
  "use strict";

  // DOM KeyboardEvent.code -> USB HID usage id (keyboard page), the layout-independent key codes of Gulp.
  const KEYS = {
    Enter: 40, Escape: 41, Backspace: 42, Tab: 43, Space: 44, Minus: 45, Equal: 46, BracketLeft: 47,
    BracketRight: 48, Backslash: 49, Semicolon: 51, Quote: 52, Backquote: 53, Comma: 54, Period: 55, Slash: 56,
    CapsLock: 57, PrintScreen: 70, ScrollLock: 71, Pause: 72, Insert: 73, Home: 74, PageUp: 75, Delete: 76,
    End: 77, PageDown: 78, ArrowRight: 79, ArrowLeft: 80, ArrowDown: 81, ArrowUp: 82, NumLock: 83,
    NumpadDivide: 84, NumpadMultiply: 85, NumpadSubtract: 86, NumpadAdd: 87, NumpadEnter: 88, Numpad0: 98,
    NumpadDecimal: 99, IntlBackslash: 100, ContextMenu: 101, ControlLeft: 224, ShiftLeft: 225, AltLeft: 226,
    MetaLeft: 227, ControlRight: 228, ShiftRight: 229, AltRight: 230, MetaRight: 231
  };
  for (let i = 0; i < 26; i++) KEYS["Key" + String.fromCharCode(65 + i)] = 4 + i;
  for (let i = 1; i <= 9; i++) KEYS["Digit" + i] = 29 + i;
  KEYS.Digit0 = 39;
  for (let i = 1; i <= 12; i++) KEYS["F" + i] = 57 + i;
  for (let i = 1; i <= 9; i++) KEYS["Numpad" + i] = 88 + i;

  // Keys whose browser default (scrolling, focus change) is blocked while the canvas has focus.
  const BLOCKED = new Set(["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Space", "Tab", "PageUp", "PageDown",
    "Home", "End", "Backspace"]);

  // Cursor shapes of the platform layer: arrow, hand, text, crosshair, resize EW, resize NS, move, not allowed.
  const CURSORS = ["default", "pointer", "text", "crosshair", "ew-resize", "ns-resize", "move", "not-allowed"];

  // Java callbacks run inside browser promises; without this, their exceptions would vanish.
  function report(e) {
    console.error("[gulp] Exception in a browser callback", e);
  }

  function modifiers(e) {
    return (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0);
  }

  const gulp = {
    canvas: null,
    dpr: 1,
    width: 1,
    height: 1,
    resizeHandler: null,

    /** Finds the canvas and creates the WebGL2 context; returns null when WebGL2 is unavailable. */
    init() {
      const canvas = document.getElementById("gulp-canvas");
      if (!canvas) throw new Error("No <canvas id=\"gulp-canvas\"> on the page");
      canvas.tabIndex = 0;
      canvas.addEventListener("contextmenu", e => e.preventDefault());
      canvas.addEventListener("pointerdown", () => {
        if (!this.textActive) canvas.focus();
        if (this.wantLock && document.pointerLockElement !== canvas) this.lock();
      });
      this.canvas = canvas;
      const gl = canvas.getContext("webgl2", {
        alpha: false, antialias: false, depth: false, stencil: true, premultipliedAlpha: true,
        preserveDrawingBuffer: false, powerPreference: "high-performance"
      });
      this.measure();
      new ResizeObserver(() => { if (this.measure() && this.resizeHandler) this.resizeHandler(); }).observe(canvas);
      window.addEventListener("resize", () => { if (this.measure() && this.resizeHandler) this.resizeHandler(); });
      canvas.focus();
      return gl;
    },

    /** Matches the drawing buffer to the CSS size times the device pixel ratio; returns whether anything changed. */
    measure() {
      const canvas = this.canvas;
      const rect = canvas.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      const width = Math.max(1, Math.round(rect.width));
      const height = Math.max(1, Math.round(rect.height));
      const pw = Math.max(1, Math.round(rect.width * dpr));
      const ph = Math.max(1, Math.round(rect.height * dpr));
      const changed = width !== this.width || height !== this.height || dpr !== this.dpr
        || canvas.width !== pw || canvas.height !== ph;
      canvas.width = pw;
      canvas.height = ph;
      this.width = width;
      this.height = height;
      this.dpr = dpr;
      return changed;
    },

    onResize(handler) { this.resizeHandler = handler; },

    onFocus(handler) {
      document.addEventListener("visibilitychange", () => handler(!document.hidden));
      window.addEventListener("blur", () => handler(false));
      window.addEventListener("focus", () => handler(!document.hidden));
    },

    isFocused() { return !document.hidden && document.hasFocus(); },

    setFullscreen(on) {
      if (on && !document.fullscreenElement) {
        this.canvas.requestFullscreen().catch(e => console.warn("[gulp] Fullscreen refused: " + e.message));
      } else if (!on && document.fullscreenElement) {
        document.exitFullscreen();
      }
    },

    isFullscreen() { return !!document.fullscreenElement; },

    // Cursor: mode 0 normal, 1 hidden, 2 locked. Pointer lock needs a click, so it is retried on the next one.
    cursorMode: 0,
    cursorCss: "",
    wantLock: false,
    setCursor(mode) {
      this.cursorMode = mode;
      this.applyCursor();
      this.wantLock = mode === 2;
      if (mode === 2) this.lock();
      else if (document.pointerLockElement === this.canvas) document.exitPointerLock();
    },
    lock() {
      try {
        const request = this.canvas.requestPointerLock();
        if (request && request.catch) request.catch(() => {});
      } catch (e) {
        // not allowed before a click
      }
    },
    applyCursor() { this.canvas.style.cursor = this.cursorMode === 0 ? this.cursorCss : "none"; },
    setSystemCursor(shape) {
      this.cursorCss = CURSORS[shape] || "";
      this.applyCursor();
    },
    setCustomCursor(width, height, rgba, hotX, hotY) {
      const canvas = Object.assign(document.createElement("canvas"), { width: width, height: height });
      const pixels = new Uint8ClampedArray(rgba.buffer, rgba.byteOffset, rgba.byteLength);
      canvas.getContext("2d").putImageData(new ImageData(pixels, width, height), 0, 0);
      this.cursorCss = "url(" + canvas.toDataURL() + ") " + hotX + " " + hotY + ", auto";
      this.applyCursor();
    },

    // Text entry: a hidden input element over the text field brings up on-screen keyboards and IME windows.
    textField: null,
    textActive: false,
    textHandler: null,
    keyHandler: null,
    setTextInput(active, x, y, width, height) {
      if (!this.textField) {
        const field = document.createElement("input");
        field.type = "text";
        field.autocomplete = "off";
        field.setAttribute("autocapitalize", "off");
        field.setAttribute("aria-hidden", "true");
        Object.assign(field.style, { position: "absolute", opacity: "0", border: "0", padding: "0",
          fontSize: "16px", zIndex: "-1" });
        field.addEventListener("input", e => {
          if (e.isComposing) return;
          this.sendText(field.value);
          field.value = "";
        });
        field.addEventListener("compositionend", e => {
          this.sendText(e.data || "");
          field.value = "";
        });
        field.addEventListener("keydown", e => { if (this.keyHandler) this.keyHandler(e, true); });
        field.addEventListener("keyup", e => { if (this.keyHandler) this.keyHandler(e, false); });
        document.body.appendChild(field);
        this.textField = field;
      }
      const field = this.textField;
      this.textActive = active;
      if (active) {
        const r = this.canvas.getBoundingClientRect();
        Object.assign(field.style, { left: (r.left + window.scrollX + x) + "px", top: (r.top + window.scrollY + y) + "px",
          width: Math.max(1, width) + "px", height: Math.max(1, height) + "px" });
        field.focus({ preventScroll: true });
      } else {
        field.blur();
        this.canvas.focus();
      }
    },
    sendText(value) {
      if (!this.textHandler) return;
      for (const ch of value) this.textHandler(ch.codePointAt(0));
    },

    // Gamepads: the Gamepad API standard mapping is the layout of the platform layer; axes 4 and 5 are the triggers.
    pads: [null, null, null, null],
    pollPads() {
      const list = navigator.getGamepads ? navigator.getGamepads() : [];
      for (let i = 0; i < 4; i++) {
        const pad = list[i];
        this.pads[i] = pad && pad.connected ? pad : null;
      }
    },
    padConnected(i) { return !!this.pads[i]; },
    padName(i) { return this.pads[i] ? this.pads[i].id : ""; },
    padAxis(i, axis) {
      const pad = this.pads[i];
      if (!pad) return 0;
      if (axis < 4) return pad.axes[axis] || 0;
      const trigger = pad.buttons[axis === 4 ? 6 : 7];
      return trigger ? trigger.value : 0;
    },
    padButton(i, button) {
      const pad = this.pads[i];
      const state = pad && pad.buttons[button];
      return !!state && state.pressed;
    },
    padRumbles(i) { return !!(this.pads[i] && this.pads[i].vibrationActuator); },
    padRumble(i, weak, strong, millis) {
      const pad = this.pads[i];
      if (!pad || !pad.vibrationActuator) return false;
      pad.vibrationActuator.playEffect("dual-rumble",
        { duration: millis, weakMagnitude: weak, strongMagnitude: strong }).catch(() => {});
      return true;
    },

    readClipboard(ok) {
      if (navigator.clipboard && navigator.clipboard.readText) {
        navigator.clipboard.readText().then(text => ok(text || ""), () => ok("")).catch(report);
      } else {
        ok("");
      }
    },
    writeClipboard(text) {
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).catch(() => {});
    },

    /** Registers raw input handlers; the callbacks receive primitives only. */
    input(key, text, move, button, wheel, touch) {
      const canvas = this.canvas;
      this.textHandler = text;
      // Keys typed into the hidden text field give their text through its input event instead.
      this.keyHandler = (e, down) => {
        const fromCanvas = e.target === canvas;
        if (fromCanvas ? BLOCKED.has(e.code) : e.code === "Tab") e.preventDefault();
        if (e.isComposing) return;
        key(KEYS[e.code] || 0, e.keyCode | 0, modifiers(e), down, down && e.repeat);
        if (down && fromCanvas && (e.key.length === 1 || (e.key.length === 2 && e.key.codePointAt(0) > 0xffff))) {
          if (!e.ctrlKey && !e.metaKey) text(e.key.codePointAt(0));
        }
      };
      canvas.addEventListener("keydown", e => this.keyHandler(e, true));
      canvas.addEventListener("keyup", e => this.keyHandler(e, false));
      canvas.addEventListener("pointermove", e => {
        if (e.pointerType !== "mouse") return;
        const r = canvas.getBoundingClientRect();
        move(e.clientX - r.left, e.clientY - r.top, e.movementX, e.movementY);
      });
      const order = [0, 2, 1, 3, 4];
      canvas.addEventListener("mousedown", e => button(order[e.button] ?? e.button, true, modifiers(e)));
      window.addEventListener("mouseup", e => button(order[e.button] ?? e.button, false, modifiers(e)));
      canvas.addEventListener("wheel", e => {
        e.preventDefault();
        const scale = e.deltaMode === 1 ? 1 : e.deltaMode === 2 ? 10 : 1 / 100;
        wheel(e.deltaX * scale, e.deltaY * scale);
      }, { passive: false });
      const touches = (phase) => (e) => {
        e.preventDefault();
        const r = canvas.getBoundingClientRect();
        for (const t of e.changedTouches) touch(t.identifier, phase, t.clientX - r.left, t.clientY - r.top);
      };
      canvas.addEventListener("touchstart", touches(0), { passive: false });
      canvas.addEventListener("touchmove", touches(1), { passive: false });
      canvas.addEventListener("touchend", touches(2), { passive: false });
      canvas.addEventListener("touchcancel", touches(3), { passive: false });
    },

    /** Reads assets/assets.manifest.json and passes the asset paths joined by newlines; missing without a manifest. */
    loadManifest(ok, missing) {
      fetch("assets/assets.manifest.json")
        .then(r => r.ok ? r.json() : null)
        .then(json => json && Array.isArray(json.files) ? ok(json.files.map(f => f.path).join("\n")) : missing(),
          () => missing())
        .catch(report);
    },

    /** Downloads a file as an ArrayBuffer; a 404 calls missing. */
    fetchBytes(url, ok, missing, fail) {
      fetch(url)
        .then(r => {
          if (r.status === 404) return null;
          if (!r.ok) throw new Error("HTTP " + r.status + " for " + url);
          return r.arrayBuffer();
        })
        .then(bytes => bytes === null ? missing() : ok(bytes), e => fail(String(e && e.message || e)))
        .catch(report);
    },


    // Dynamic fonts: the file is registered with the FontFace API and glyphs are drawn with Canvas2D.
    fontCounter: 0,
    glyphCanvas: null,
    lastGlyph: null,
    openFont(bytes, ok, fail) {
      const family = "gulp-font-" + (this.fontCounter++);
      const face = new FontFace(family, bytes);
      face.load().then(loaded => { document.fonts.add(loaded); ok(family); },
        e => fail(String(e && e.message || e))).catch(report);
    },
    glyphContext(family, size) {
      if (!this.glyphCanvas) {
        this.glyphCanvas = document.createElement("canvas");
        this.glyphCanvas.width = 8;
        this.glyphCanvas.height = 8;
      }
      const context = this.glyphCanvas.getContext("2d", { willReadFrequently: true });
      context.font = size + "px \"" + family + "\"";
      return context;
    },
    fontMetrics(family, size) {
      const m = this.glyphContext(family, size).measureText("Hg");
      const ascent = m.fontBoundingBoxAscent ?? m.actualBoundingBoxAscent;
      const descent = m.fontBoundingBoxDescent ?? m.actualBoundingBoxDescent;
      return new Float32Array([ascent, descent, (ascent + descent) * 1.15]);
    },
    measureText(family, size, text) {
      return this.glyphContext(family, size).measureText(text).width;
    },
    rasterize(family, codePoint, size) {
      const text = String.fromCodePoint(codePoint);
      let context = this.glyphContext(family, size);
      const m = context.measureText(text);
      const left = Math.ceil(m.actualBoundingBoxLeft || 0) + 1;
      const ascent = Math.ceil(m.actualBoundingBoxAscent || 0) + 1;
      const width = Math.max(0, left + Math.ceil(m.actualBoundingBoxRight || 0) + 1);
      const height = Math.max(0, ascent + Math.ceil(m.actualBoundingBoxDescent || 0) + 1);
      if (width <= 2 || height <= 2) {
        this.lastGlyph = { header: new Float32Array([0, 0, 0, 0, m.width]), data: new Int8Array(0) };
        return;
      }
      if (this.glyphCanvas.width < width || this.glyphCanvas.height < height) {
        this.glyphCanvas.width = Math.max(this.glyphCanvas.width, width);
        this.glyphCanvas.height = Math.max(this.glyphCanvas.height, height);
        context = this.glyphContext(family, size);
      }
      context.clearRect(0, 0, width, height);
      context.fillStyle = "#fff";
      context.textBaseline = "alphabetic";
      context.fillText(text, left, ascent);
      const pixels = context.getImageData(0, 0, width, height).data;
      const coverage = new Int8Array(width * height);
      for (let i = 0; i < coverage.length; i++) coverage[i] = pixels[i * 4 + 3];
      this.lastGlyph = { header: new Float32Array([width, height, -left, ascent, m.width]), data: coverage };
    },
    /** Decodes an image to straight RGBA. */
    decodeImage(bytes, ok, fail) {
      const blob = new Blob([bytes]);
      createImageBitmap(blob, { premultiplyAlpha: "none", colorSpaceConversion: "none" })
        .then(bitmap => {
          // Safari before 16.4 has no 2D OffscreenCanvas.
          const canvas = typeof OffscreenCanvas === "function"
            ? new OffscreenCanvas(bitmap.width, bitmap.height)
            : Object.assign(document.createElement("canvas"), { width: bitmap.width, height: bitmap.height });
          const context = canvas.getContext("2d", { willReadFrequently: true });
          context.drawImage(bitmap, 0, 0);
          const data = context.getImageData(0, 0, bitmap.width, bitmap.height).data;
          ok(bitmap.width, bitmap.height, new Int8Array(data.buffer, data.byteOffset, data.byteLength));
          bitmap.close();
        }, e => fail(String(e && e.message || e)))
        .catch(report);
    },

    // User data in IndexedDB: one object store of ArrayBuffers per game.
    db: null,
    dbName: "gulp",
    openDb() {
      if (this.db) return this.db;
      this.db = new Promise((resolve, reject) => {
        const request = indexedDB.open(this.dbName, 1);
        request.onupgradeneeded = () => request.result.createObjectStore("files");
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      });
      return this.db;
    },
    store(mode, action, ok, fail) {
      this.openDb().then(db => {
        const tx = db.transaction("files", mode);
        const request = action(tx.objectStore("files"));
        request.onsuccess = () => ok(request.result);
        request.onerror = () => fail(String(request.error));
      }, e => fail(String(e)));
    },
    readData(name, ok, missing, fail) {
      this.store("readonly", s => s.get(name), v => v === undefined ? missing() : ok(v), fail);
    },
    writeData(name, bytes, ok, fail) {
      this.store("readwrite", s => s.put(bytes.slice().buffer, name), () => ok(), fail);
    },
    deleteData(name, ok, fail) {
      this.store("readwrite", s => s.delete(name), () => ok(), fail);
    },
    listData(prefix, ok, fail) {
      this.store("readonly", s => s.getAllKeys(), keys => ok(keys.filter(k => k.startsWith(prefix)).join("\n")), fail);
    },

    // Audio on WebAudio. Each voice: source -> low-pass -> high-pass -> gain -> panner -> master, with a send from
    // the gain to a shared convolution reverb. Buffer sources cannot pause, so pausing remembers the position and
    // resuming starts a new source there; queued stream buffers are scheduled back to back on the audio clock.
    ctx: null,
    master: null,
    voices: [],
    audioBuffers: new Map(),
    nextAudioBuffer: 1,
    streams: new Map(),
    nextStream: 1,
    audioInit(count) {
      const Context = window.AudioContext || window.webkitAudioContext;
      if (!Context) return false;
      const ctx = new Context();
      this.ctx = ctx;
      this.master = ctx.createGain();
      this.master.connect(ctx.destination);
      const reverb = ctx.createConvolver();
      reverb.buffer = this.impulse(2.2);
      reverb.connect(this.master);
      for (let i = 0; i < count; i++) {
        const v = { low: ctx.createBiquadFilter(), high: ctx.createBiquadFilter(), gain: ctx.createGain(),
          pan: ctx.createStereoPanner ? ctx.createStereoPanner() : null, send: ctx.createGain(), source: null,
          buffer: 0, loop: false, pitch: 1, paused: false, playing: false, offset: 0, startedAt: 0, queue: [],
          processed: [], nextTime: 0 };
        v.low.type = "lowpass";
        v.low.frequency.value = ctx.sampleRate / 2;
        v.high.type = "highpass";
        v.high.frequency.value = 1;
        v.low.connect(v.high);
        v.high.connect(v.gain);
        if (v.pan) {
          v.gain.connect(v.pan);
          v.pan.connect(this.master);
        } else {
          v.gain.connect(this.master);
        }
        v.send.gain.value = 0;
        v.gain.connect(v.send);
        v.send.connect(reverb);
        this.voices.push(v);
      }
      const unlock = () => { if (ctx.state !== "running") ctx.resume().catch(() => {}); };
      for (const type of ["pointerdown", "mousedown", "keydown", "touchend"]) window.addEventListener(type, unlock, true);
      return true;
    },
    impulse(seconds) {
      const ctx = this.ctx;
      const length = Math.floor(ctx.sampleRate * seconds);
      const buffer = ctx.createBuffer(2, length, ctx.sampleRate);
      for (let c = 0; c < 2; c++) {
        const data = buffer.getChannelData(c);
        for (let i = 0; i < length; i++) data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, 3);
      }
      return buffer;
    },
    audioUnlocked() { return !!this.ctx && this.ctx.state === "running"; },
    audioBuffer(samples, channels, rate) {
      const frames = Math.max(1, Math.floor(samples.length / channels));
      const buffer = this.ctx.createBuffer(channels, frames, rate);
      for (let c = 0; c < channels; c++) {
        const data = buffer.getChannelData(c);
        for (let i = 0; i < frames; i++) data[i] = (samples[i * channels + c] || 0) / 32768;
      }
      const id = this.nextAudioBuffer++;
      this.audioBuffers.set(id, buffer);
      return id;
    },
    deleteAudioBuffer(id) { this.audioBuffers.delete(id); },
    source(v, buffer, when, offset) {
      const node = this.ctx.createBufferSource();
      node.buffer = buffer;
      node.playbackRate.value = v.pitch;
      node.connect(v.low);
      node.start(when, offset);
      return node;
    },
    silence(v) {
      if (v.source) {
        v.source.onended = null;
        try { v.source.stop(); } catch (e) { /* already stopped */ }
        v.source = null;
      }
      for (const entry of v.queue) {
        if (entry.node) {
          entry.node.onended = null;
          try { entry.node.stop(); } catch (e) { /* already stopped */ }
          entry.node = null;
        }
      }
    },
    voicePlay(i, id, loop) {
      const v = this.voices[i];
      this.silence(v);
      Object.assign(v, { queue: [], processed: [], buffer: id, loop: loop, offset: 0, playing: true, paused: false });
      this.startSingle(v);
    },
    startSingle(v) {
      const buffer = this.audioBuffers.get(v.buffer);
      if (!buffer) { v.playing = false; return; }
      const offset = v.loop ? v.offset % buffer.duration : Math.min(v.offset, buffer.duration);
      const node = this.source(v, buffer, 0, offset);
      node.loop = v.loop;
      v.source = node;
      v.startedAt = this.ctx.currentTime - offset / v.pitch;
      node.onended = () => { if (v.source === node) { v.source = null; v.playing = false; } };
    },
    voiceQueue(i, id) {
      const v = this.voices[i];
      const entry = { id: id, node: null };
      v.queue.push(entry);
      v.buffer = 0;
      v.playing = true;
      if (!v.paused) this.schedule(v, entry);
    },
    schedule(v, entry) {
      const buffer = this.audioBuffers.get(entry.id);
      if (!buffer) { this.processed(v, entry); return; }
      const start = Math.max(this.ctx.currentTime + 0.01, v.nextTime);
      const node = this.source(v, buffer, start, 0);
      entry.node = node;
      v.nextTime = start + buffer.duration / v.pitch;
      node.onended = () => { if (entry.node === node) this.processed(v, entry); };
    },
    processed(v, entry) {
      const index = v.queue.indexOf(entry);
      if (index >= 0) v.queue.splice(index, 1);
      entry.node = null;
      v.processed.push(entry.id);
      if (v.queue.length === 0) v.playing = false;
    },
    voiceUnqueue(i) {
      const v = this.voices[i];
      return v.processed.length ? v.processed.shift() : -1;
    },
    voicePause(i, paused) {
      const v = this.voices[i];
      if (v.paused === paused) return;
      v.paused = paused;
      if (paused) {
        if (v.source) v.offset = (this.ctx.currentTime - v.startedAt) * v.pitch;
        this.silence(v);
        v.nextTime = 0;
      } else if (v.playing) {
        if (v.buffer) this.startSingle(v);
        else for (const entry of v.queue) this.schedule(v, entry);
      }
    },
    voiceLooping(i, loop) {
      const v = this.voices[i];
      v.loop = loop;
      if (v.source) v.source.loop = loop;
    },
    voiceStop(i) {
      const v = this.voices[i];
      this.silence(v);
      Object.assign(v, { queue: [], processed: [], playing: false, paused: false, buffer: 0, nextTime: 0 });
    },
    voicePlaying(i) { return this.voices[i].playing; },
    voiceGain(i, gain) { this.voices[i].gain.gain.value = gain; },
    voicePitch(i, pitch) {
      const v = this.voices[i];
      if (v.source) {
        const offset = (this.ctx.currentTime - v.startedAt) * v.pitch;
        v.startedAt = this.ctx.currentTime - offset / pitch;
        v.source.playbackRate.value = pitch;
      }
      v.pitch = pitch;
    },
    voicePan(i, pan) { if (this.voices[i].pan) this.voices[i].pan.pan.value = pan; },
    voiceFilter(i, low, high) {
      const v = this.voices[i];
      const nyquist = this.ctx.sampleRate / 2;
      v.low.frequency.value = low > 0 ? Math.min(low, nyquist) : nyquist;
      v.high.frequency.value = high > 0 ? Math.min(high, nyquist) : 1;
    },
    voiceReverb(i, send) { this.voices[i].send.gain.value = send; },
    masterGain(gain) { if (this.master) this.master.gain.value = gain; },

    // Decoding goes through decodeAudioData (OGG and WAV where the browser supports them).
    interleave(buffer, start, frames) {
      const channels = Math.min(2, buffer.numberOfChannels);
      const out = new Int16Array(frames * channels);
      for (let c = 0; c < channels; c++) {
        const data = buffer.getChannelData(c);
        for (let i = 0; i < frames; i++) {
          const value = Math.max(-1, Math.min(1, data[start + i]));
          out[i * channels + c] = value < 0 ? value * 32768 : value * 32767;
        }
      }
      return out;
    },
    decodeAudio(bytes, ok, fail) {
      if (!this.ctx) { fail("WebAudio is not available"); return; }
      this.ctx.decodeAudioData(bytes.slice().buffer)
        .then(buffer => ok(Math.min(2, buffer.numberOfChannels), buffer.sampleRate,
          this.interleave(buffer, 0, buffer.length)), e => fail(String(e && e.message || e)))
        .catch(report);
    },
    openStream(bytes, ok, fail) {
      if (!this.ctx) { fail("WebAudio is not available"); return; }
      this.ctx.decodeAudioData(bytes.slice().buffer)
        .then(buffer => {
          const id = this.nextStream++;
          this.streams.set(id, { buffer: buffer, position: 0 });
          ok(id, Math.min(2, buffer.numberOfChannels), buffer.sampleRate, buffer.length);
        }, e => fail(String(e && e.message || e)))
        .catch(report);
    },
    streamRead(id, frames) {
      const stream = this.streams.get(id);
      const count = Math.max(0, Math.min(frames, stream.buffer.length - stream.position));
      const out = this.interleave(stream.buffer, stream.position, count);
      stream.position += count;
      return out;
    },
    streamSeek(id, frame) {
      const stream = this.streams.get(id);
      stream.position = Math.max(0, Math.min(stream.buffer.length, frame));
    },
    streamClose(id) { this.streams.delete(id); },

    /** Called by the engine after the first frame; hides the loading screen. */
    ready() {
      const loading = document.getElementById("gulp-loading");
      if (loading) loading.remove();
    },

    /** Shows a fatal error over the canvas. */
    fatal(message) {
      if (window.gulpShowError) window.gulpShowError(message);
      else console.error(message);
    },

    /** Runs a console command, for example gulp.command("/tps"); set by the backend. */
    command: null
  };

  window.gulp = gulp;
})();
