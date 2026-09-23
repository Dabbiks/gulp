# Changelog

Format oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/); wersjonowanie semantyczne od 1.0.

## [Unreleased]

### Etap 1 — Rdzeń

#### Dodane

- `Engine` i `Gulp.engine()`: pętla ze stałym krokiem (akumulator, maks. 5 ticków nadrabiania, `alpha` interpolacji), osobne ticki gry i czasu rzeczywistego, pauza, `timeScale`, mierzone TPS, zatrzymanie, eventy cyklu życia (`GameStart/Stop`, `ModuleEnable/Disable`, `TickStart/End`, `Pause/Resume`, `FocusGained/Lost`, `WindowResize`).
- `Game` i `GameModule` jako `Owner`: skróty `listen`, `on`, `run`, `later`, `every`, `command`, `key`, `config`, `logger`, `data`, `require`; `GameSettings.modules(...)` i konsola deweloperska.
- Moduły: `@ModuleInfo`, sortowanie topologiczne z pełną ścieżką cyklu, stany `LOADED`/`ENABLED`/`DISABLED`/`FAILED`, włączanie i wyłączanie w trakcie gry (kaskadowo), automatyczne usuwanie listenerów, zadań, komend i usług właściciela.
- Eventy: priorytety z `MONITOR`, `Cancellable`, `ignoreCancelled`, dziedziczenie po klasach eventów, subskrypcje lambda i przypięte do celu (`TargetedEvent`), izolacja wyjątków, limit zagnieżdżenia 64, dispatch bez alokacji.
- Scheduler: `run`, `later`, `every` (ticki lub `Duration`), widoki `owner(...)` i `realtime()`, `TaskRunnable`, `Sequence`, `async().thenSync()` z `Promise`, anulowanie zadania po 3 błędach z rzędu, planowanie z dowolnego wątku.
- `Key`, `Registry`, `Registries` z 10 rejestrami wbudowanymi (typy-zaślepki), zamrażanie po fazie ładowania.
- `Services` z priorytetami i eventami rejestracji.
- Dane: model `JsonValue`, parser i zapis JSON, parser i zapis podzbioru YAML, `Config` (domyślne z zasobów + nadpisania użytkownika, sekcje, zapis, przeładowanie, `ConfigReloadEvent`), `Codec` z kombinatorami, `DataType`, `DataContainer`.
- `gulp-processor`: dispatchery listenerów, deskryptory i walidacja modułów (id, duplikaty, cykle), codeki `@Serializable`, indeks w `META-INF/services`.
- Komendy: builder z aliasami, argumentami typowanymi (liczby z zakresem, słowa, tekst do końca linii, wybór, klucze, enumy), podkomendy, podpowiedzi; `/help`, `/tps`, `/modules`, `/module enable|disable`, `/reload config`, `/timescale`; konsola z historią i terminal na desktopie.
- `Logger` per właściciel (`logs/latest.log` na desktopie), `@ThreadSafe`, `Pool`, `IntList`, `IntMap`.
- SPI platformy: `PlatformLog`, `PlatformConsole`; desktop: `PlatformFiles` (zasoby z katalogu lub classpath, dane w katalogu aplikacji), log, konsola terminala; headless: log, konsola i rejestr kodu generowanego.
- Showcase: moduł `core` z konfiguracją, zadaniem cyklicznym, sekwencją, listenerem i komendą `/hello`.
- ADR 0005 (pokrycie ze wszystkich testów) i 0006 (rozstrzygnięcia rdzenia).

#### Zmienione

- `GulpRuntime` z etapu 0 zastąpiony przez `GulpEngine`; `DesktopBackend.create` przyjmuje id gry.
- Build: `-Xlint:all,-processing`; weryfikacja pokrycia liczy testy `gulp-api`, `gulp-core` i `gulp-backend-headless` razem.

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
