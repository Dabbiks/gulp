# Changelog

Format oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/); wersjonowanie semantyczne od 1.0.

## [Unreleased]

### Etap 0 — Fundamenty

#### Dodane

- Repozytorium: licencja Apache 2.0, `README.md`, `CLAUDE.md`, `docs/spec.md` (+ oryginał `docs/spec.pdf`), ADR 0001–0004.
- Build Gradle 9.7.1 z wrapperem i 11 modułami z sekcji 6; `gradle/libs.versions.toml` (LWJGL 3.4.3, TeaVM 0.15.0, JUnit 6.1.3, AssertJ 3.27.7, JSpecify 1.0.1, JaCoCo 0.8.15, Spotless 8.10.2, palantir-java-format 2.98.0).
- `build-logic`: toolchain Java 25, `-Xlint:all -Werror`, doclint dla `gulp-api` i `gulp-platform`, Spotless, JUnit + AssertJ, próg pokrycia 80%.
- CI GitHub Actions: `check` na Linux/Windows/macOS, test dymny okna pod Xvfb na Linuksie, zaczepka na build web (etap 3).
- `gulp-api`: `Gulp.launch`, `Game` (cykl życia), `GameSettings` (okno, VSync, FPS, TPS, kolor tła), `graphics.Color`, SPI `GameLauncher`.
- `gulp-platform`: wszystkie interfejsy SPI z sekcji 7 z Javadoc (`PlatformBackend`, `PlatformLoop`, `Gl`, `PlatformWindow`, `PlatformInput`, `PlatformAudio`, `PlatformFiles`, `PlatformDecoders`, `PlatformExecutor`, `PlatformNet`, `PlatformInfo`, `PlatformModules`) i typy pomocnicze.
- `gulp-core`: tymczasowy `GulpRuntime` (cykl życia + czyszczenie ekranu), zastąpiony przez `Engine` w etapie 1.
- `gulp-backend-headless`: pętla sterowana ręcznie (`step(n)`, symulowany czas), `Gl` bez operacji z licznikami, okno i input do symulacji, pliki w pamięci, stuby dekoderów, atrapy sieci, `HeadlessRunner`.
- `gulp-backend-desktop`: okno GLFW z kontekstem OpenGL 3.3 core, HiDPI, pełny ekran, `Gl` na LWJGL z tłumaczeniem shaderów GLSL ES 3.00 → 3.30 core, executor na wątkach wirtualnych, restart JVM z `-XstartOnFirstThread` na macOS.
- `examples/showcase`: okno z kolorem tła.
