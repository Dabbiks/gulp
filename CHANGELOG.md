# Changelog

Format oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/); wersjonowanie semantyczne od 1.0.

## [Unreleased]

### Etap 7 — Fizyka, nawigacja i AI

#### Dodane

- **`dev.gulp.api.physics`, poziom kinematyczny:**
  - warstwy i maski: `CollisionLayer`, `CollisionMask`;
  - kształty `Shape`: prostokąt, koło, kapsuła, wielokąt (wklęsłe dzielone automatycznie), odcinek, łańcuch;
  - komponenty `Collider` (także platformy jednokierunkowe), `Mover` i `Trigger`;
  - `Mover`: `moveAndSlide` z podkrokami i osiami X/Y, zbocza, podłoga, ściana i sufit, przyciąganie do podłogi, stopnie, unoszenie przez platformy, platformy jednokierunkowe, coyote time, bufor skoku, `dropThroughPlatform`, tryb top-down;
  - `Trigger`: `TriggerEnterEvent`, `TriggerExitEvent` i `triggered()`.
- **Bryły sztywne:**
  - `Body` (dynamiczne, kinematyczne, statyczne) z gęstością lub masą, tarciem, sprężystością, tłumieniem, `fixedRotation`, `bullet`, skalą grawitacji i usypianiem;
  - siły, impulsy, moment obrotowy;
  - złącza `distance`, `rope`, `revolute`, `prismatic`, `weld`, `wheel`, `mouse`, `motor` z limitami, silnikami i sprężynami;
  - eventy `EntityCollideEvent`, `EntityCollideEndEvent`, `PreCollideEvent` i `EntityLandEvent`.
- **`Physics` świata (`world.physics()`):** grawitacja, podkroki, macierz kolizji, `raycast`, `raycastAll`, `shapeCast`, `overlapPoint`, `overlapCircle`, `overlapRect`, `isSolidTile`, tworzenie złączy.
- **`dev.gulp.api.nav`:**
  - `NavGrid` (`world.navGrid()`) prosto z mapy kafelków, z blokadami `block(rect, owner)`;
  - A* w 4 lub 8 kierunkach, Jump Point Search, wygładzanie, `requestPath` rozłożone na ticki, pola przepływu `FlowField`;
  - A* na dowolnym grafie (`Graph`, `PathFinder`), `Path` (z punktów, krzywej, obiektu mapy);
  - komponenty `NavAgent` (z omijaniem innych agentów) i `PathFollower`;
  - eventy `NavTargetReachedEvent`, `NavPathFailedEvent`, `PathEndEvent`.
- **`dev.gulp.api.ai`:**
  - `StateMachine` ze stanami, przejściami i `StateChangeEvent`;
  - `BehaviorTree` z węzłami sequence, selector, parallel i dekoratorami inverter, repeat, cooldown, timeout, untilSuccess;
  - `Steering`: seek, flee, arrive, wander, pursue, evade, separation, cohesion, alignment, followPath, avoidObstacles.
- **Komponenty `Health` i `SoundEmitter`:**
  - `Health` wysyła `EntityDamageEvent` (anulowalny, ze zmienialną wartością), `EntityHealEvent` i `EntityDeathEvent`;
  - `DamageType` z typem `gulp:generic`.
- **Mapy i kafelki:**
  - `TileType.passable` i `TileType.navCost`;
  - `MapObject.points()` z linii łamanych i wielokątów Tiled oraz pól punktów LDtk.
- **Debug:** `world.showDebug(DebugView, boolean)` rysuje kształty, kontakty, złącza, triggery, siatkę nawigacji, ścieżki i sterowanie.
- **Przykłady:**
  - `examples/platformer` w strukturze modułów z sekcji 21: gracz na `Mover`, monety jako triggery na warstwie `pickup` z dźwiękiem, ślimaki do nadepnięcia, winda na ścieżce z mapy, serca;
  - nowy `examples/sandbox`: stos skrzyń, piramida, most z łańcucha, wahadło, samochód na złączach kół, przeciąganie myszą;
  - w `examples/topdown` ślimaki wędrują i gonią gracza po ścieżkach z `NavGrid`, a gracz i drzewa mają kolizje.
- ADR 0012.

#### Zmienione

- `CollisionLayer` i `DamageType` są klasami z `of(Key)`, a nie pustymi interfejsami. Silnik rejestruje `gulp:default`, `gulp:tiles` i `gulp:generic`.
- `TileMapImpl` liczy rewizję zmian; nawigacja szuka od nowa, gdy się zmieni.

#### Naprawione

- Desktop: gra bez własnego folderu `assets` padała przy starcie, bo hot reload próbował obserwować nieistniejący katalog.

### Etap 6 — Świat i encje

#### Dodane

- `dev.gulp.api.world`:
  - `Worlds` (`load`, `register`, `switchTo` z przejściem, `unload`, `active`) i `World` (warstwy, kamery, encje, zapytania, `tileMap`, `parallax`);
  - `WorldSettings`, `Location`;
  - źródła `WorldSource`: pusty, generator, Tiled, LDtk; do tego `spawn`, `tile`, `spawner` i `onLoad`;
  - eventy `WorldLoad`, `WorldUnload`, `WorldSwitch`.
- `dev.gulp.api.entity`:
  - `Entity` z hierarchią (`attach`), tagami, danymi, interpolacją pozycji i `PauseMode`;
  - `EntityType` z dziedziczeniem, `Component` z cyklem życia i `tickOrder`, `EntityQuery` na siatce przestrzennej;
  - eventy `EntitySpawn`, `EntityRemove`, `EntityTeleport`, `EntityClick`, `EntityHoverEnter/Exit`, `EntityScreenEnter/Exit`, także celowane na jedną encję.
- Komponenty: `SpriteComponent`, `Lifetime`, `Follow`, `WorldText`, `Interactable`.
- `TileMap`:
  - orientacje ortogonalna, izometryczna, izometryczna przesunięta i heksagonalne;
  - warstwy, `TileType` z kształtami kolizji, animacjami i tickami, `TileState`, `TileSet`;
  - flagi obrotu, event `TileChange`.
- Chunki 32×32:
  - ładowanie wokół kamer i biletów `keepLoaded`, `ChunkGenerator` poza tickiem;
  - cache siatek renderowania, eventy `ChunkLoad` i `ChunkUnload`.
- `Terrain.sixteen` i `Terrain.blob` (47 kafelków), `ObjectSpawner`, `MapObject`, `Parallax`.
- Importy map:
  - Tiled w XML (`.tmx`, `.tsx`) i JSON (`.tmj`, `.tsj`), z warstwami w CSV, XML i Base64, także skompresowanymi zlib, gzip i zstd;
  - LDtk (`.ldtk`, `.ldtkl`, IntGrid z auto-layerami).
- Własne dekodery DEFLATE (zlib, gzip) i Zstandard w `gulp-core`, działające pod TeaVM.
- `Texture.upload(Pixmap)`: podmiana całej tekstury, także na inny rozmiar (sekcja 12.2).
- `Camera`:
  - podążanie z wygładzaniem, martwą strefą i wyprzedzeniem;
  - granice, wstrząsy (`trauma`), `zoomTo`, `panTo`;
  - viewport dla wielu kamer.
- Przejścia `Transitions`: `fade`, `fadeColor`, `slide`, `circleWipe`, `pixelate`, `shader`. Silnik rejestruje `gulp:fade`, `gulp:slide`, `gulp:circle_wipe` i `gulp:pixelate`.
- `GameAssets.Maps` z plików `maps/` (mapy jako klucze tekstowe, obrazy kafelków jako tekstury).
- Przykłady:
  - `examples/topdown`: nieskończony świat z szumu, brzegi z `Terrain`, drzewa do ścięcia kliknięciem, zoom;
  - `examples/platformer`: dwa poziomy LDtk z paralaksą i monetami.
- ADR 0011.

#### Zmienione

- `PauseMode` przeniesiono z `dev.gulp.api.audio` do `dev.gulp.api`.
- `display().camera()` zwraca główną kamerę aktywnego świata; `mouseWorld` uwzględnia viewport kamery.
- Spotless formatuje tylko `src/**/*.java`.

#### Naprawione

- Procesor adnotacji nie zapisywał indeksu, gdy w module był sam `@ModuleInfo` bez listenerów. Silnik zgłaszał wtedy brak deskryptora modułu.
- Spotless przeglądał `build/` w trakcie równoległej kompilacji i losowo przerywał `check`.
- `circleWipe` zostawiał szwy między trójkątami maski.

### Etap 5 — Input i audio

#### Dodane

- `dev.gulp.api.input`:
  - `Input` ze stanami akcji liczonymi per tick: `pressed`, `justPressed`, `justReleased`, `heldTicks`, `strength`, `axis`, `vector` (martwa strefa po promieniu);
  - `InputAction` (sloty przypisań, zestaw, martwa strefa), `ActionSet` (`GAMEPLAY`, `MENU`, własne);
  - przypisania `Binding`: `Keys` (kody USB HID według pozycji), `MouseButton`, `GamepadButton`, `GamepadAxis.positive()/negative()`, z identyfikatorami i `Binding.parse`;
  - `InputBindings`: `rebind`, `unbind`, `reset`, `conflicts`, `captureNextInput`, z zapisem w preferencjach;
  - `BindingGlyph` i `ControllerFamily`: etykiety i ścieżki ikon dla Xbox, PlayStation, Nintendo i klawiatury;
  - urządzenia: klawiatura, mysz (`mouseWorld`), dotyk z gestami, `Gamepad` (martwe strefy, wibracje), kursory (`SystemCursor`, własne z `Pixmap` lub regionu, `CursorMode`), `Clipboard`, `startTextInput` dla IME i klawiatury ekranowej;
  - eventy `KeyPress`, `KeyRelease`, `KeyRepeat`, `CharTyped`, `MouseButtonPress`, `MouseButtonRelease`, `MouseMove`, `MouseScroll`, `GamepadConnect`, `GamepadDisconnect`, `ActionPress`, `ActionRelease`, `Tap`, `DoubleTap`, `LongPress`, `Pan`, `Pinch`, `Swipe` z `isConsumedByUi()`.
- `dev.gulp.api.audio`:
  - `Audio`: `play`, `playAt` (tłumienie i panorama), `stream(PcmSource)`, szyny, słuchacz, pula 32 głosów z priorytetami;
  - `Sound` (warianty, zakresy głośności i wysokości, `maxInstances`, `minInterval`, `PauseMode`);
  - `Bus` (głośność, wyciszenie, low-pass, high-pass, pogłos, ściszanie innej szyny);
  - `MusicPlayer` (fade, crossfade, playlisty, pauza, pozycja), `Music` z punktami pętli w ramkach, `AudioClip`, `Playback`, `MusicEndEvent`.
- `Preferences` (`engine.preferences()`, `Owner.preferences()`) z automatycznym zapisem do `preferences.json` lub IndexedDB.
- `AssetType.AUDIO` i `MUSIC`, `AssetKey.audio/music`, `GameAssets.Sounds` i `GameAssets.Music`; dźwięki z `Registries.SOUND` ładują się z grupą startową.
- Desktop:
  - gamepady przez GLFW z bazą mapowań SDL i podłączaniem w locie;
  - kursory systemowe i własne, IME z GLFW 3.5, schowek;
  - OpenAL Soft z filtrami i pogłosem EFX;
  - dekodowanie OGG przez stb_vorbis i WAV, muzyka strumieniowana.
- Web:
  - Gamepad API z wibracjami, kursory CSS i własne, pointer lock ponawiany przy kliknięciu;
  - ukryte pole tekstowe dla IME i klawiatury ekranowej, schowek;
  - WebAudio z filtrami, panoramą i pogłosem, odblokowanie przy pierwszym geście, dekodowanie przez `decodeAudioData`;
  - bez WebAudio gra działa bez dźwięku.
- Headless: symulowany zegar audio, stany filtrów, pauzy, kursora i klawiatury ekranowej, prawdziwe dekodowanie WAV.
- `WavDecoder` w `gulp-core` (PCM 8/16/24/32 bit, float).
- Showcase: ekran „Wejście i dźwięk” (Tab przełącza ekrany):
  - ruch, skok i strzał z klawiatury, myszy lub pada;
  - zmiana przypisania skoku (R), która przetrwa restart;
  - muzyka w pętli (M), ton proceduralny (T), filtr (L), głośność (1/2), kursory (C).
- ADR 0010.

#### Zmienione

- Spotless nie sprawdza źródeł generowanych (`build/**`). `GameAssets` używa pełnych nazw typów, więc nie ma nieużywanych importów.

#### Naprawione

- Hot reload bez manifestu zasobów (np. uruchomienie z IDE) czytał plik o pierwszym rozszerzeniu typu zamiast pliku, z którego zasób wczytano, więc nie przeładowywał dźwięków WAV ani fontów TTF.
- Desktop: równoległe zapisy tego samego pliku danych użytkownika (np. opóźniony zapis preferencji i zapis przy zamknięciu) mogły się ścigać o plik tymczasowy i zostawić starszą treść; teraz zapisy jednego pliku są szeregowane i wygrywa ostatni.
- Test przekrojowy `CrossStageTest` łączy etapy 1–5 w jednej grze (moduły, usługi, konfiguracja, komendy, rysowanie, zasoby, tekst, przeładowanie, resource packi, akcje, dźwięk, pauza, zwolnione tempo, wyłączanie modułów, restart).

### Etap 4 — Zasoby, tekst i fonty

#### Dodane

- `dev.gulp.api.text`:
  - `Font` (MSDF, bitmapowy, dynamiczny) z łańcuchem fallbacków;
  - `FontFamily` z udawanym bold i italic, gdy brakuje wariantu;
  - `TextStyle` (rozmiar, kolor, obrys, cień, odstępy, bold, italic);
  - `Text` (tekst sformatowany, `translatable`, obrazki w tekście, linki, efekty);
  - `Markup` (`[b]`, `[i]`, `[color]`, `[size]`, `[font]`, `[img]`, `[link]`, `[wave]`, `[shake]`, `[rainbow]`, `[pulse]`, `[fade]`, `[[`);
  - `TextLayout` i `TextBox` (zawijanie po słowach i znakach, wyrównanie, limit linii, wielokropek);
  - `TextLinkClickEvent`, `TextRevealCompleteEvent`.
- `Draw.text(...)` w siedmiu wariantach, w tym punkt z wyrównaniem, prostokąt z zawijaniem i gotowy układ z `visibleCharacters`.
- `graphics().layout`, `graphics().defaultFont()`, `graphics().gridFont(...)`.
- Font domyślny Fira Sans (SIL OFL) jako MSDF: łacina rozszerzona, grecki, cyrylica.
- Shader MSDF z antyaliasingiem w pikselach ekranu, obrysem, cieniem i pogrubieniem.
- Fonty dynamiczne: FreeType na desktopie, Canvas2D i FontFace API na webie. Glify trafiają do stron cache osobno dla każdego rozmiaru.
- Zasoby:
  - `AssetType.FONT`, `ATLAS`, `REGION`, `AssetKey.font/atlas/region`;
  - `TextureAtlas`;
  - `assets().region("ns:sprites/…")`;
  - resource packi (`assets().resourcePacks()`, `ResourcePackChangeEvent`);
  - atlasy pakowane przy ładowaniu w trybie deweloperskim;
  - czytanie atlasów w formacie TexturePackera (hash, array, wielostronicowy).
- Hot reload na desktopie: tekstury i atlasy są podmieniane w miejscu, reszta zasobów, konfiguracje i tłumaczenia są wczytywane na nowo. Do tego `AssetReloadEvent`.
- `dev.gulp.api.i18n`:
  - `Translations` z `tr(...)`, łańcuchem locale → język → domyślny i `setLocale`;
  - `LocaleChangeEvent`;
  - `GameSettings.locale` i `defaultLocale`;
  - `Engine.translations()` i `Owner.tr(...)`.
- `gulp-tools`:
  - generator MSDF (LWJGL msdfgen, kerning z GPOS) i fontów BMFont;
  - packer atlasów (wspólny `AtlasBuilder` i `RectPacker` z `gulp-core`);
  - CLI `GulpTools`.
- Plugin:
  - `packAssets` (atlasy z folderów `sprites/`, fonty z `gulp { assets { msdfFont(...); bitmapFont(...) } }`);
  - `generateAssetKeys` (klasa `GameAssets`);
  - `bundleResourcePacks`;
  - manifest obejmuje zasoby z bibliotek;
  - `runDesktop` czyta zasoby prosto ze źródeł (hot reload).
- SPI: `PlatformFiles.listResourcePacks`, `readResourcePackFile`, `watchAssets`, `ResourcePackInfo`. `PlatformDecoders.openFont` jest teraz asynchroniczne.
- Showcase: moduł `text` pokazuje polski tekst we wszystkich typach fontów, efekty markupu, monetę z atlasu w tekście, tłumaczenia (pl i en) i hot reload `lang/pl_pl.json` oraz `textures/banner.png`.
- ADR 0009.

#### Naprawione

- Web nie widział manifestu zasobów: bramka odrzucała sam plik manifestu.
- Nazwy w `gulp-runtime.js`: pomiar tekstu nadpisywał dopasowanie rozmiaru canvasu. Test dymny sprawdza teraz rozmiar bufora canvasu.
- Zadania TeaVM mają cały classpath jako wejście i nie korzystają z build cache.

### Etap 3 — Web

#### Dodane

- `gulp-backend-web` na TeaVM 0.15 (Wasm GC + fallback JS):
  - pętla na `requestAnimationFrame`;
  - `Gl` na WebGL2;
  - canvas z `devicePixelRatio`, zmianą rozmiaru i pełnym ekranem;
  - fokus z Page Visibility API;
  - surowe zdarzenia klawiatury, myszy, kółka i dotyku, z blokadą domyślnych akcji przeglądarki, gdy canvas ma fokus;
  - pliki przez `fetch`, dane użytkownika w IndexedDB, dekodowanie obrazów przez `createImageBitmap`;
  - wykonawca kooperacyjny;
  - komendy konsoli z narzędzi deweloperskich (`gulp.command("/tps")`).
- Szablon strony z ekranem ładowania, paskiem postępu pobierania, wykrywaniem Wasm GC i automatycznym fallbackiem na JS (`?target=js|wasm` wymusza cel).
- Surowe wejście na desktopie (klawiatura, tekst, mysz, kółko, schowek). Kody klawiszy to identyfikatory USB HID, wspólne dla wszystkich backendów.
- `dev.gulp.api.asset`:
  - `AssetKey`, `AssetType` (tekstura, obraz, tekst, bajty), własne `AssetLoader` z zależnościami;
  - `Assets` z `get`, `load`, `isLoaded`, `unload`, `progress`, grupami (`add`, `addFolder` z manifestu) i liczeniem referencji;
  - grupa `startup` ładowana przed `onStart` z podmienialnym `LoadingScreen`;
  - eventy `AssetLoadEvent`, `AssetLoadFailedEvent`, `AssetGroupLoadedEvent`, `AssetReloadEvent`;
  - `Engine.assets()`.
- `Promise.flatMap`.
- Plugin `dev.gulp.game`:
  - `runDesktop`, `buildWeb`, `runWeb` (serwer na `localhost:8080`, z `--continuous` przebudowa i przeładowanie strony po zmianie), `stopWeb`, `packageWeb`, `generateAssetManifest`;
  - konfiguracje `desktopRuntime` i `webRuntime`;
  - blok `gulp { web { target; jsFallback; port } }`.
- Test dymny `:examples:showcase:webSmokeTest` (Playwright for Java): Chromium, Firefox i WebKit, każdy jako Wasm GC i jako JS. W CI działa w osobnym jobie `web`, który wypisuje też rozmiar paczki.
- Showcase ładuje monetę z PNG przez grupę `startup` i rysuje monety oraz piłki w osobnych batchach. Działa w przeglądarce bez zmian w kodzie gry.
- ADR 0008 (web, zasoby, plugin).

#### Zmienione

- `gulp-gradle-plugin` jest teraz included buildem; główne `check` i `spotlessApply` obejmują też jego zadania.
- Showcase uruchamia się przez `runDesktop` zamiast `run`; CI też.
- Brak zasobu na webie i w danych użytkownika zgłasza `FileNotFoundException`, jak na desktopie.


### Etap 2 — Matematyka i grafika

#### Dodane

- `dev.gulp.api.math`: `Mathf`, `Vec2`, `MutableVec2`, `Rect`, `Circle`, `Segment`, `Capsule`, `Polygon`, `Affine2`, `Mat3`, `Transform2D`, `Interpolation` i `Ease` (41 krzywych, `cubicBezier`), krzywe `Bezier` / `CatmullRom` / `BSpline` z parametryzacją długością łuku, `Intersect` (w tym SAT), `Geometry` (triangulacja, otoczka wypukła, upraszczanie, dekompozycja), `Rng` (xoshiro256**), `Noise` (Perlin, simplex, fBm, warping), `Grid`, `GridPos`.
- `dev.gulp.api.graphics`: `Texture`, `TextureRegion` (przycięcie i obrót z atlasu, flip), `Pixmap` (rysowanie, skalowanie, zapis PNG), `Shader` z include'ami (`gulp:common.glsl`) i fallbackiem na shader domyślny, `Material`, `BlendMode`, `FrameBuffer`, `Mesh2D`, `NinePatch`, `Graphics`; nowe stałe i operacje `Color`.
- `dev.gulp.api.render`: `Draw` (obrazy, nine-patch, kafelkowanie, siatki, kształty z antyaliasingiem, stos transformacji, kolor i alfa, materiały, `clip`, `into(fbo)`), `Camera`, `Display` (rozdzielczość bazowa, `StretchMode`, `AspectMode`, skalowanie całkowite, HiDPI, pasy, zrzut ekranu, FPS, `RenderStats`), `RenderLayer` z domyślnymi warstwami, eventy `PreRender`, `RenderLayer`, `PostRender`.
- `Engine.graphics()`, `Engine.display()`; ustawienia `baseResolution`, `stretchMode`, `aspectMode`, `integerScaling`, `pixelSnap`, `letterboxColor`, `pixelsPerUnit`.
- `gulp-core`: batcher (16 384 quady na wywołanie, podwójne bufory, bez alokacji), renderer z warstwami i trybem `VIEWPORT`, zwalnianie zasobów GPU przy zatrzymaniu.
- Desktop: dekodowanie obrazów przez stb_image (`lwjgl-stb`).
- Testy wizualne z obrazami wzorcowymi (`./gradlew :gulp-backend-desktop:visualTest`, w CI pod Xvfb + Mesa): kształty, obrazy, transformacje, clip, bufory pozaekranowe, kamera, tryb `VIEWPORT`.
- Showcase: moduł `sprites` z 10 000 odbijających się sprite'ami, interpolacją i kształtami UI (`/sprites <n>`, `/zoom <x>`); na desktopie 2 wywołania rysowania na klatkę przy 144 FPS (vsync).
- ADR 0007 (konwencje grafiki).

#### Zmienione

- Renderowanie w `GulpEngine` przeszło z samego czyszczenia ekranu na `Renderer`; headless liczy dwa czyszczenia na klatkę (pasy i obszar gry).

#### Naprawione

- `PreRenderEvent` jest wysyłany przed wyliczeniem układu ekranu, więc zmiany `Display` i kamery w tym evencie działają w tej samej klatce (wykryte testem wizualnym).
- Dekodowanie obrazów na desktopie zwalniało pamięć stb pod złym adresem (uszkodzenie sterty); wykryte testem wizualnym.


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
