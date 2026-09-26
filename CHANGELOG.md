# Changelog

Format oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/); wersjonowanie semantyczne od 1.0.

## [Unreleased]

### Etap 11 — Jakość i narzędzia

#### Dodane

- **Narzędzia deweloperskie (`dev.gulp.api.debug`):**
  - `debug()` z nakładką F3 (FPS i wykres czasu klatki, TPS i czas ticka, encje, chunki, cząsteczki, dźwięki, wywołania rysowania, zmiany tekstur, pamięć, kursor i kafelek);
  - przełączniki F3 + C/N/G/L/U/E i `/debug <flaga> [on|off]`: kolizje, nawigacja, granice chunków, światła, inspektor UI, inspektor encji (kliknięta encja z komponentami, polami `@Save`, tagami i danymi na żywo);
  - `debug().draw()` (`line`, `rect`, `circle`, `arrow`, `text` z czasem życia w tickach);
  - profiler (moduły, handlery, zadania, typy komponentów, własne sekcje) z raportem `ProfileReport` i `/profile start|stop`;
  - `Stats`;
  - `GameSettings.debugTools`;
  - nowe widoki `DebugView.CHUNKS` i `LIGHTS`.
- **Awarie i logi:**
  - raporty awarii (`crash-reports/crash-<data>.txt` na desktopie, nakładka z przyciskiem kopiowania na webie) z modułami, systemem, GPU, wersjami i ostatnimi liniami logu;
  - czytelny ekran błędu zamiast cichego zamknięcia;
  - rotacja `logs/latest.log` do archiwów `.log.gz` (10 ostatnich);
  - `PlatformLog.crash`.
- **Polityka błędów (20.3):**
  - komponent wyłączany po 3 błędach `onTick` z rzędu;
  - zastępniki brakujących zasobów (szachownica, cisza, font domyślny) z ostrzeżeniem;
  - odtwarzanie zasobów GPU po utracie kontekstu WebGL (`GlJournal`);
  - `HeadlessGl` odrzuca shadery z `#error`, żeby testować zastępczy shader;
  - testy wszystkich punktów (`ErrorPolicyTest`, `GlJournalTest`, `DesktopLogTest`).
- **Plugin Gradle:**
  - `packageDesktop` (jdeps, jlink, jpackage: MSI/DMG/DEB/RPM albo zipowany `app-image`) z blokiem `gulp { desktop { ... } }`;
  - `checkApiUsage` w `check`.
- **Wydajność:**
  - `examples/bench` (sprite'y, encje, cząsteczki, UI, mapa) z trybem automatycznym i testami budżetów (`:examples:bench:bench`);
  - `AllocationTest` (zero alokacji na klatkę w rysowaniu, fizyce, eventach i zadaniach).
- **Szablon:** `templates/game` i `./gradlew :gulp-tools:newGame` (`NewProject`) — nowy projekt z wrapperem, testem, pakowaniem desktop i web.
- **CI:**
  - benchmarki;
  - pakowanie projektu z szablonu na Linuksie, Windowsie i macOS oraz web;
  - limit 5 MB gzip dla `game.wasm`;
  - utrata i odzyskanie kontekstu w teście dymnym web.

#### Zmienione

- `Contact` jest klasą wielokrotnego użytku (ważną do następnego ruchu) z `pointX/Y` i `normalX/Y`.
- `Mover` ma `velocityX()` i `velocityY()`, a wektory tworzy dopiero przy odczycie.
- `TickStartEvent` i `TickEndEvent` są używane wielokrotnie (SPI `EventAccess`).
- Brakująca tekstura, region, dźwięk lub font nie kończą się już błędem `load`, tylko zastępnikiem.
- `/profile` i `/debug` są komendami wbudowanymi.

#### Naprawione

- Alokacje co klatkę w UI (`UiAction.values()`), shaderach, siatce przestrzennej, pętlach świata i fizyce.
- Rysowanie 50 000 sprite'ów przyspieszyło około dwukrotnie: jedno przejście na klatkę zamiast jednego na warstwę, sortowanie na tablicach prostych, szybka ścieżka quadów.
- Przestarzała składnia `by tasks.registering` w skryptach Gradle.

### Etap 10 — Dane

#### Dodane

- **Zapisy gry (`dev.gulp.api.save`):**
  - `saves().slot(name)` zwraca `SaveSlot` z metodami `save`, `save(title)`, `load`, `delete`, `exists`, `metadata`, `exportData`, `importData`, `exportFile`, `importFile`;
  - `saves().list()`, `migration(from, step)`, `version()`, `autosave(slot, interval)`, `stopAutosave`, `isBusy`;
  - `SaveMetadata` (tytuł, data, czas gry w tickach, wersja, miniatura) oraz eventy `GameSaveEvent` i `GameLoadEvent`;
  - `GameSettings.saveVersion(n)`.
- **Co się zapisuje:**
  - trwałe światy (`WorldSettings.persistent`) z danymi, zmienionymi chunkami w plikach regionów (32 × 32 chunki), stanami kafelków i trwałymi encjami (UUID, transformacja, nazwa, tagi, dane);
  - komponenty z `@ComponentInfo` i polami `@Save`, dla których procesor generuje `ComponentState` bez refleksji;
  - dane modułów i aktywny świat.
- **Format:** tagowany format binarny z kompresją DEFLATE, pisaną samodzielnie i czytelną dla JDK oraz przeglądarki (`Tags`, `Deflate`).
- **Autozapis i przenoszenie:** autozapis w czasie rzeczywistym i przy ukryciu karty na webie; eksport i import slotu (web: pobieranie i wgrywanie; desktop: foldery `exports/` i `imports/`).
- **Preferencje:** silnik pamięta język (`gulp.locale`), tryb okna, VSync i monitor (`gulp.window.*`) oraz skalę UI (`gulp.ui.scale`) i przywraca je przed `onStart`.
- **Lokalizacja:**
  - podzbiór ICU w `tr` i `Text.translatable`: `{0}`, `{nazwa}`, `number`, `date`, `plural` (reguły CLDR dla pl, ru, uk, cs, sk, fr, CJK i innych), `select`;
  - `translations().format`, `formatNumber`, `formatDate`;
  - teksty UI odświeżają się po `setLocale` (`UiAccess.Backend.textRevision`).
- **Sieć (`dev.gulp.api.net`):**
  - `http().get`, `post`, `request(HttpRequest)` z limitem czasu i `HttpResponse` (`text`, `json`, `bytes`, nagłówki);
  - `http().connect(url)` zwraca `WebSocket` z kolejką przed otwarciem;
  - `platform().openUrl` tylko dla http(s).
- **Backendy:**
  - desktop: `HttpClient` i `WebSocket` z JDK;
  - web: `fetch`, `WebSocket`, pobieranie i wgrywanie plików przez `gulp-runtime.js`;
  - headless: `HeadlessNet.fail`, `hang`, `refuseSocket`, `sockets()` oraz `HeadlessFiles.offered` i `putPickable`.
- **Kodeki:** `Codec.VEC2`, `DataType.VEC2`.
- **Przykłady:**
  - platformówka zapisuje postęp (monety, poziom, punkt odrodzenia) z przyciskiem „Continue”, autozapisem i testem `ProgressSaveTest`;
  - showcase ma wybór języka w ustawieniach.

#### Zmienione

- `ComponentType` ma pole `persistent`.
- `EntityImpl.id` nie jest `final` (odtwarzanie UUID przy wczytaniu).
- `GulpEngine.translations()` zwraca `TranslationsImpl`.

#### Naprawione

- `readPixels` na webie podaje `Uint8Array`, więc zrzut ekranu (i miniatura zapisu) działa bez ostrzeżenia WebGL.
- `HttpRequest.Builder.timeout` odrzuca zero i wartości ujemne.

### Etap 9 — UI

#### Dodane

- **`dev.gulp.api.ui`, podstawy:**
  - `Node` (typ fluent `Node<N>`): rozmiar (`width`, `height`, `minSize`, `maxSize`), flagi (`fill`, `expand(ratio)`, `grow`, `shrink*`, `alignX/Y`), kotwice (`anchor`, `offset`, `fillParent`), wygląd (`visible`, `alpha`, `scale`, `rotation`, `tint`, `variant`, `style`, `theme`), interakcja (`enabled`, `focusable`, `mouseFilter`, `tooltip`, `cursor`, `focusNeighbor`, `draggable`, `dropTarget`, `contextMenu`), `find`, `findAll` i zdarzenia węzła (`onClick`, `onHover`, `onFocus`, `on`);
  - silnik layoutu z pomiarem od dołu i rozmieszczeniem od góry, unieważnianiem tylko brudnych poddrzew, drugim przebiegiem dla zawijanego tekstu i skalą UI (0,75–2, w preferencjach);
  - `Insets`, `Size`, `Anchor` (9 punktów i presety `*_WIDE`, `FULL`), `Align`, `Direction`, `MouseFilter`, `WidgetState`.
- **Kontenery:** `column`, `row` (`gap`, `align`, `justify`), `grid`, `stack`, `center`, `margin`, `panel`, `scroll` (kółko, przeciąganie, pasek, prawa gałka, podjazd do fokusu), `split`, `flow`, `tabs` i `tab`, `foldable` z `FoldGroup`, `aspect`, `spacer`.
- **Widgety:**
  - `label`, `richText` (markup, linki, `reveal`), `image`, `button`, `iconButton`;
  - `checkbox`, `radio` z `ButtonGroup`, `toggle`, `slider`, `progressBar` (poziomy i kołowy), `spinBox`;
  - `textField` i `textArea` (kursor, zaznaczanie, schowek, filtr, walidacja, placeholder, hasło, IME);
  - `dropdown`, `listView` (wirtualny, zaznaczanie pojedyncze i wielokrotne), `tree` z `TreeItem`;
  - `dialog`, `modal`, `window` (przeciągane, zamykane), `colorPicker`, `itemGrid` (sloty z przeciąganiem);
  - `separator`, `keybindButton`, `virtualJoystick`, `menu` i `item` (menu kontekstowe), `ui().toast(...)`.
- **Stan:** `State`, `Computed` (z automatycznym śledzeniem zależności), `ListState` (zmiany z indeksem), `Observable`; powiązania jedno- i dwukierunkowe (`bind(State)`), należące do węzła.
- **Motywy:**
  - `Theme` z budowniczym (baza, typ, stan, wariant, dziedziczenie przez `parent`);
  - `Style` i `StyleBox` (kolor z zaokrągleniem, obramowaniem i cieniem, nine-patch);
  - wbudowane `DARK`, `LIGHT`, `PIXEL` z `withAccent` i `highContrast`;
  - płynne przejścia stanów.
- **Ekrany:**
  - `Screen` (`build`, `onOpen`, `onClose`, `onBack`, `defaultFocus`, `pausesGame`, `blocksGameplayInput`, `dimBackground`);
  - przejścia `ScreenTransition` (`fade`, `slideUp`, `scale`);
  - `ui().open`, `push`, `pop`, `close`, `current`;
  - `ScreenOpenEvent` (anulowalny) i `ScreenCloseEvent`;
  - ekran jest `Owner`.
- **Warstwy:** `ui().hud()` (węzły z kotwicami, znikają z właścicielem), komponent `WorldUi` (węzeł przypięty do encji), `ui().overlay()` z `OverlayArea` (`center`, `anchor`).
- **Fokus i pad:**
  - automatyczny graf fokusu z geometrii i Tab w kolejności drzewa;
  - wbudowane akcje `gulp:ui_accept`, `ui_cancel`, `ui_up`, `ui_down`, `ui_left`, `ui_right`, `ui_next_tab`, `ui_prev_tab`;
  - przeciąganie padem, menu kontekstowe pod przyciskiem X;
  - podpowiedzi przy fokusie.
- **Wejście:**
  - UI dostaje wejście przed grą, a wejście użyte przez UI ma `isConsumedByUi()` i nie trafia do akcji gameplay;
  - `Input.setVirtualStrength`;
  - `PointMapper.toLogicalX/Y`.
- **Narzędzia:**
  - konsola deweloperska w grze (`~`) z historią i uzupełnianiem;
  - inspektor UI (F12 w trybie deweloperskim, `ui().inspector(true)`).
- **Tweeny UI:** `Props.NODE_OFFSET`, `NODE_SIZE`, `NODE_ALPHA`, `NODE_SCALE`, `NODE_ROTATION`, `NODE_COLOR` (w czasie rzeczywistym).
- **`Draw`:** `roundedRect`, `rectOutline` i czterokolorowy `gradientRect` na liczbach bez alokacji; `text(String, Vec2, TextStyle, TextAlign)`, `rect(Rect, Color)`.
- **Przykłady:**
  - `examples/ui-gallery`: każdy kontener i widget, przełączanie motywu, kontrastu, akcentu i skali;
  - showcase: menu główne, pauza pod Escape, ustawienia (obraz, dźwięk, sterowanie) bez współrzędnych;
  - platformówka: menu z sekcji 18.1, HUD z sekcji 21 (`State`, `ui().hud()`), własny motyw.
- Testy `UiLayoutTest`, `UiStateTest`, `UiScreensTest`, `UiInputTest`, `UiToolsTest`, `UiDropdownMouseTest`; test dymny web zamyka menu startowe.
- ADR 0015.

#### Zmienione

- `Theme` jest klasą z budowniczym, a nie pustym interfejsem.
- W trybie `VIEWPORT` warstwy ekranowe rysują się po przeskalowaniu, w rozdzielczości okna (sekcja 12.7); odświeżony wzorzec testu wizualnego `viewport`.
- Na warstwie `overlay` ekrany UI rysują się nad rysowaniem gry.
- `Draw.clip` nie alokuje.

#### Naprawione

- Web: kliknięcie bez wcześniejszego ruchu wskaźnika trafiało w starą pozycję.

### Przegląd po etapie 8

#### Dodane

- `display().window()` (`GameWindow`): tytuł, rozmiar, pełny ekran, okno bez ramki, VSync, lista monitorów i wybór monitora w trakcie gry; `GameSettings.borderless(...)` i `monitor(...)`.
- Komendy `/spawn <typ> [x y]`, `/tp <x> <y>`, `/reload assets`; argument `Arguments.entityType`; `CommandContext.reply(Text)`.
- `World.raycast(...)`, `TileType.data()` z `Builder.data(...)`.
- `FloatList`, `IntIntMap`, `Stopwatch`, `RollingAverage` w `gulp-core`.
- Test integracyjny `CrossWorldTest` (etapy 6–8 razem).
- ADR 0014.

#### Zmienione

- Komenda gry zastępuje komendę wbudowaną o tej samej nazwie.
- `examples/platformer`: gracz z `Animator` i regułami `auto()`, monety z animacją `spin` i tweenem przy zebraniu (jak w sekcji 21).

#### Naprawione

- Teleport encji do innego świata zostawiał jej collider, ciało, światło i emiter w starym świecie.
- Tick światów alokował kopię listy światów co tick.

### Etap 8 — Animacje, tweeny, cząsteczki, światło

#### Dodane

- **`dev.gulp.api.anim`, tweeny i timeline:**
  - `Property` (getter, setter, interpolator, bez refleksji), `Interpolators` (`FLOAT`, `INT`, `VEC2`, `COLOR`, `ANGLE`) i `Props`:
    - encja: `POSITION`, `X`, `Y`, `ROTATION`, `SCALE`, `ALPHA`, `TINT`, `SPRITE_OFFSET`, `EFFECT`;
    - kamera: `CAMERA_POSITION`, `CAMERA_ZOOM`, `CAMERA_ROTATION`;
    - dźwięk: `VOLUME`, `BUS_VOLUME`;
    - światło: `LIGHT_INTENSITY`, `LIGHT_RADIUS`, `LIGHT_COLOR`;
    - efekty post-process mają stałe we własnych klasach;
  - `Tweens`:
    - tworzenie: `to`, `from`, `by`, `fromTo`, `custom`;
    - ustawienia: `ease`, `delay`, `repeat`, `yoyo`, `onUpdate`, `onComplete`, `realtime`, `then`;
    - kompozycja: `sequence`, `parallel`, `wait`, `call`;
    - sterowanie: `pause`, `resume`, `kill`, `killAll`, `isRunning`, `progress`, `owner`;
    - gotowe efekty: `punchScale`, `flash`, `fadeIn`, `fadeOut`, `pulse`, `bob`, `shake` (encji i kamery);
  - `Timeline`: ścieżki klatek kluczowych z ease na odcinek, zdarzenia `at`, długość, pętla, prędkość, `play`, `stop`, `seek`, `reverse`.
- **Animacje klatkowe:**
  - `SpriteAnimation` z trybami `ONCE`, `LOOP`, `PING_PONG`, `REVERSED`, `LOOP_RANDOM` i `onFrame`;
  - `AnimationSet` z Aseprite (`AssetKey.animations`, folder `animations/`: tagi, czasy klatek, kierunek) lub z atlasu (`fromAtlas`);
  - komponent `Animator`: `play`, `playOnce(...).then(...)`, `speed`, `current`, `isFinished`, reguły `auto().when(...).otherwise(...)`;
  - eventy `AnimationEndEvent` i `AnimationFrameEvent`.
- **`dev.gulp.api.particle`:**
  - `ParticleEffect` z emiterów `EmitterConfig`: `rate`, `burst`, czas trwania, pętla, kształty `EmitterShape` (punkt, koło, pierścień, prostokąt, odcinek), kierunek i rozrzut, prędkość, grawitacja, opór, obrót, czas życia;
  - krzywe `FloatCurve` rozmiaru i przezroczystości, gradient `Gradient` koloru;
  - region lub klatki, `BlendMode`, przestrzeń lokalna, pod-emitery `onDeath`, kolizja z kafelkami (`BOUNCE`, `DIE`), limit na emiter;
  - `world.spawnParticles(effect, position | location)` zwraca `ParticleInstance` (`stop`, `kill`, `follow`); `world.particles()` z limitem 20 000;
  - komponent `ParticleEmitter`;
  - efekty z JSON w `particles/` (`AssetKey.particles`), z hot reloadem.
- **Światło 2D:**
  - `world.lighting().ambient(color)`; `Light.point`, `spot` i `directional` z kolorem, promieniem, intensywnością, zanikiem i cieniami;
  - komponenty `LightSource` i `Occluder`;
  - cienie z kształtów i kafelków kolizyjnych (mapa cieni 1D liczona na CPU).
- **Post-processing:**
  - `world.postEffects()` i `display().postEffects()`;
  - efekty `Bloom`, `Vignette`, `ColorGrade` (LUT), `Blur`, `Pixelate`, `ChromaticAberration`, `Crt`, `CustomEffect(Shader)`, wszystkie z właściwościami do tweenów.
- **Materiały gotowe:**
  - `Materials.FLASH`, `OUTLINE`, `DISSOLVE`, `GRAYSCALE`, `TINT` z parametrem `Draw.effect(Color)`;
  - `SpriteComponent.material` i `effect`.
- **Plugin Gradle:** `GameAssets.Animations` i `GameAssets.Particles`.
- **Showcase, ekran „juice” (Tab):**
  - bohater z Aseprite z automatycznym wyborem animacji;
  - pochodnie z ogniem i migoczącym światłem, skrzynie z cieniami, światło pod myszą;
  - bloom, winieta, CRT i pikselizacja;
  - Space: iskry z pod-emiterami, błysk, sprężynowanie i wstrząs kamery;
  - JSON cząsteczek przeładowuje się w trakcie gry.
- Scena wizualna `materials`; test dymny web przełącza na ekran juice.
- ADR 0013.

#### Zmienione

- Wierzchołek batchera ma 24 bajty (doszły parametry materiału `a_params`/`v_params`); własne shadery mogą czytać `v_params`.
- `ParticleEffect` jest klasą z emiterami, a nie pustym interfejsem.
- Ekrany 0 i 1 showcase rysują w pustym świecie `plain`.

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
