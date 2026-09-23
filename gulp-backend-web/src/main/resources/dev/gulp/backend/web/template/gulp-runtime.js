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
      canvas.addEventListener("pointerdown", () => canvas.focus());
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

    setCursor(mode) {
      // 0 normal, 1 hidden, 2 locked
      this.canvas.style.cursor = mode === 0 ? "" : "none";
      if (mode === 2) this.canvas.requestPointerLock();
      else if (document.pointerLockElement === this.canvas) document.exitPointerLock();
    },

    /** Registers raw input handlers; the callbacks receive primitives only. */
    input(key, text, move, button, wheel, touch) {
      const canvas = this.canvas;
      canvas.addEventListener("keydown", e => {
        if (BLOCKED.has(e.code)) e.preventDefault();
        key(KEYS[e.code] || 0, e.keyCode | 0, modifiers(e), true, e.repeat);
        if (e.key.length === 1 || (e.key.length === 2 && e.key.codePointAt(0) > 0xffff)) {
          if (!e.ctrlKey && !e.metaKey) text(e.key.codePointAt(0));
        }
      });
      canvas.addEventListener("keyup", e => {
        if (BLOCKED.has(e.code)) e.preventDefault();
        key(KEYS[e.code] || 0, e.keyCode | 0, modifiers(e), false, false);
      });
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
