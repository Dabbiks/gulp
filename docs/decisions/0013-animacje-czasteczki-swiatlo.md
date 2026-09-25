# 0013. Animacje, cząsteczki, światło i post-processing (etap 8): rozstrzygnięcia szczegółów

- Status: przyjęta
- Data: 2026-09-25
- Etap: 8

## Kontekst

Sekcja 17 opisuje tweeny, timeline'y, animacje klatkowe i cząsteczki, a sekcje 12.8–12.9 łańcuch post-processingu, światło 2D i gotowe materiały (roadmapa). Nie przesądzają jednak:
- kiedy i w jakim czasie biegną animacje;
- jak API w `gulp-api` uruchamia je w silniku;
- formatu JSON cząsteczek i sposobu działania hot reloadu;
- techniki cieni i miejsca światła w kolejności rysowania;
- zestawu materiałów gotowych i sposobu przekazania im parametrów.

Poniżej decyzje podjęte przy implementacji.

## Decyzje: tweeny i timeline

- **Czas.**
  - Tweeny i timeline'y biegną raz na klatkę, w czasie gry (skalowanym, zatrzymanym w pauzie), a z `realtime()` w czasie rzeczywistym.
  - Nie biegną w tickach, żeby ruch był płynny przy każdej częstotliwości odświeżania. Zmieniają więc stan między tickami, ale zawsze na głównym wątku.
- **Most do rdzenia.**
  - `Animation` (zamknięta hierarchia `Tween`, `Timeline`) ukrywa metody krokowe. Rdzeń woła je przez SPI `AnimationAccess`, na wzorcu `PhysicsAccess`.
  - Rdzeń (`AnimationSystem`) trzyma listę uruchomionych animacji. Animacja uruchomiona w trakcie przebiegu dołącza w następnej klatce, a ponowne `start()` nie dodaje jej drugi raz.
- **Sprzątanie.**
  - Animacja, której celem jest usunięta encja albo której właściciel (`owner(Owner)`) jest wyłączony, jest zabijana.
  - Wyjątek w animacji jest logowany i zatrzymuje tylko ją.
- **Semantyka `Tween`.**
  - Domyślny ease to `OUT_QUAD`.
  - `from` i `to` odczytują wartość początkową, gdy mija opóźnienie. Doszło `fromTo` (obie wartości podane) i `custom(target, seconds, t -> ...)` dla efektów bez właściwości.
  - `then(next)` zwraca sekwencję, więc `onComplete` w łańcuchu z sekcji 17.2 działa po całym łańcuchu.
  - `ease`, `yoyo` i `onUpdate` dotyczą pojedynczych tweenów; `delay`, `repeat` i `onComplete` także grup.
- **Gotowe efekty.**
  - `bob` i `shake` encji przesuwają obraz (`SpriteComponent.offset`, właściwość `Props.SPRITE_OFFSET`), a nie encję, więc nie ruszają logiki ani fizyki.
  - `shake` kamery używa jej istniejącego wstrząsu z traumą.
  - `flash` przełącza materiał sprite'a na `Materials.FLASH` i wygasza parametr efektu.
- **Timeline.**
  - Buduje się go w kodzie: `track(target, property, keys -> keys.key(t, v, ease))` i `at(t, runnable)`.
  - Ease klucza dotyczy odcinka dochodzącego do niego. Zdarzenia odpalają się przy przejściu w dowolnym kierunku.
  - `stop()` zatrzymuje, zostawiając wartości; `seek` nie odpala zdarzeń.
- **`Props`.**
  - Właściwości encji, kamery, dźwięku (`VOLUME` dla `Playback`, `BUS_VOLUME` dla szyn) i światła.
  - Dodatkowo `SPRITE_OFFSET` i `EFFECT` (parametr materiału sprite'a).
  - Efekty post-process mają stałe we własnych klasach (`Bloom.INTENSITY` i podobne).
  - Właściwości węzłów UI przyjdą z etapem 9.

## Decyzje: animacje klatkowe

- **`Animator`.**
  - Animator jest komponentem w pakiecie `dev.gulp.api.anim`, bo należy do animacji, nie do encji.
  - Klatki liczy w tickach (`1 / targetTps` na tick) i ustawia region `SpriteComponent`.
  - `playOnce` wymusza jedno przejście i wstrzymuje reguły `auto()` do końca. `then(name)` wybiera, co grać potem.
  - `AnimationFrameEvent` wysyłany jest tylko wtedy, gdy ktoś go słucha.
- **Zdarzenia na klatkach.** `SpriteAnimation.onFrame(i, entity -> ...)` dostaje animowaną encję, bo jeden zestaw animacji dzieli wiele encji.
- **Import Aseprite.**
  - `AssetType.ANIMATIONS` (folder `animations/`) czyta eksport JSON (tablica albo hash klatek) z arkuszem obok. Arkusz dostaje filtr `NEAREST`.
  - Tagi stają się nazwami animacji. Kierunki: `forward` → `LOOP`, `pingpong` → `PING_PONG`, `reverse` → klatki odwrócone.
  - Tag z `"repeat": "1"` daje `ONCE`. Bez tagów wszystkie klatki tworzą animację `default`.
- **Z atlasu.** `AnimationSet.fromAtlas(atlas, "player/", fps, "run", ...)` bierze regiony `player/run_0..N`.

## Decyzje: cząsteczki

- **Symulacja.**
  - Na CPU, w klatkach, w czasie gry. Każdy emiter instancji ma własne tablice (x, y, prędkość, obrót, wiek, życie); martwe cząstki są zamieniane z ostatnią.
  - Po rozgrzaniu symulacja i rysowanie nie alokują. Alokuje tylko start efektu (także pod-emitera).
  - Limit świata to domyślnie 20 000 (`Particles.setLimit`). Nowe cząstki ponad limit są pomijane.
- **Rysowanie.**
  - W warstwie renderowania emitera (domyślnie `effects`), przez batcher, szybką ścieżką `DrawImpl.sprite`, bez obiektów `Color` na cząstkę.
  - Bez regionu cząstka to miękka kropka generowana przez silnik.
  - Kilka regionów (`frames`) gra się przez czas życia.
- **Kolizja z kafelkami.** Każdy kształt kolizyjny liczy się jak pełna komórka. `BOUNCE` odbija osobno w osi X i Y, `DIE` zabija i uruchamia pod-emiter.
- **Przestrzeń lokalna.** Cząstki `local(true)` przesuwają się o ruch instancji; `follow(entity)` bierze pozycję interpolowaną.
- **JSON (`particles/*.json`, `AssetType.PARTICLES`).**
  - Format: `{"emitters": [ {...} ]}`. Klucze emitera odpowiadają metodom `EmitterConfig.Builder`: `rate`, `bursts: [{count, at}]`, `duration`, `loop`, `shape: {type, radius|thickness|width|height|length}`, `direction`, `spread`, `speed`, `gravity: [x, y]`, `drag`, `spin`, `rotation`, `lifetime`, `size`, `color`, `alpha`, `region`/`frames`, `blend`, `local`, `onDeath`, `collision`, `bounce`, `max`, `layer`.
  - Zakres to `[min, max]` albo liczba; krzywa `[[t, v], ...]` albo liczba; gradient to lista kolorów, `[[t, "#hex"], ...]` albo jeden kolor.
  - `onDeath` to zagnieżdżony efekt albo klucz innego pliku (`particles/ember`, ładowany jako zależność). Klucze bez przestrzeni nazw są w przestrzeni pliku.
- **Hot reload.** Efekt uruchomiony przez klucz (`spawnParticles(AssetKey, ...)`, `new ParticleEmitter(AssetKey)`) bierze przy każdym starcie bieżącą wersję zasobu. Działające pętle gra może zrestartować w `AssetReloadEvent`, jak robi showcase.

## Decyzje: światło

- **Mapa światła.**
  - Dla każdej kamery rysowana jest w połowie rozdzielczości, zaczynając od koloru otoczenia.
  - Światła rysuje się addytywnie jako wachlarze trójkątów. Shader wygasza kolor z odległością jako `(1 - d/r)^falloff`.
  - Mapę mnoży się (`MULTIPLY`) przez to, co narysowano. Wyłączone światło świata nic nie kosztuje.
- **Kolejność.** Mapa światła nakłada się przed warstwą świata `effects`, więc cząsteczki ognia i iskry świecą w ciemności (albo po ostatniej warstwie, gdy `effects` nie ma).
- **Cienie: mapa cieni 1D na CPU.**
  - Dla każdego światła z cieniami liczona jest odległość do najbliższej krawędzi w 720 kierunkach.
  - Krawędzie pochodzą z kształtów `Occluder` (koło jako 12-kąt, kapsuła jako prostokąt, obrót encji) oraz z odsłoniętych krawędzi kafelków kolizyjnych, scalonych w długie odcinki.
  - Światło wchodzi 0,3 jednostki w krawędź, żeby ściany zwrócone do niego były oświetlone.
  - Wybraliśmy CPU zamiast rzutowania 1D na GPU, bo działa tak samo w WebGL 2 i w TeaVM, bez tekstur zmiennoprzecinkowych.
- **Rodzaje świateł.** `spot` przycina wachlarz do stożka; `directional` wypełnia widok bez cieni.

## Decyzje: post-processing i materiały

- **Łańcuchy.**
  - `world.postEffects()` przetwarza warstwy świata każdej kamery (przed tekstem nad encjami i warstwami ekranu).
  - `display().postEffects()` przetwarza całą klatkę, przed ekranem ładowania.
  - Każdy efekt to przebieg pełnoekranowy między buforami FBO. Ostatni zapisuje do celu.
  - Bloom to jasny wycinek, rozmycie w połowie rozdzielczości i dodanie. `Blur` to dwa przebiegi Gaussa.
  - `Custom(Shader)` z sekcji 12.8 nazywa się `CustomEffect`, bo `Custom` w API byłoby niejasne. Dostaje `u_texture`, `v_texCoord`, `u_resolution` i `u_time`.
  - `ColorGrade` bierze LUT 256×16 (16 plastrów niebieskiego).
- **Parametry materiałów.**
  - Wierzchołek batchera ma 24 bajty: doszły 4 bajty `a_params`, ustawiane przez nowe `Draw.effect(Color)`.
  - Wbudowane materiały `Materials.FLASH`, `OUTLINE`, `DISSOLVE`, `GRAYSCALE`, `TINT` czytają z nich kolor (rgb) i ilość (a).
  - `SpriteComponent` ma `material` i `effect`. `Material.builtInShader()` nazywa shader, który rdzeń kompiluje przy pierwszym użyciu.

## Odłożone i ograniczenia

- **Mapy normalnych dla sprite'ów** są po 1.0, zgodnie z sekcją 12.9.
- **Cienie są twarde.** Miękkość daje tylko mapa światła w połowie rozdzielczości z filtrem liniowym. Kafelki o kształtach innych niż pełne rzucają cień jak pełna komórka, tak samo jak przy kolizji cząsteczek.
- **Wiele kamer z viewportami.** Łańcuch świata zapisuje wynik w prostokąt kamery, a mapa światła jest liczona na kamerę. Przycinanie viewportów zostaje takie jak w etapie 6.
- **Showcase używa świata.** Ekrany 0 i 1 rysują w pustym świecie `plain` z kamerą wyświetlacza, a ekran 2 w świecie `juice`, bo światło, cząsteczki i łańcuch świata należą do świata.

## Konsekwencje

- Jedną linijką animuje się dowolną właściwość, animacje z Aseprite gra się po nazwie, a cząsteczki i światła działają identycznie na desktopie i w przeglądarce (TeaVM, WebGL 2).
- Nie ma do tego refleksji, wątków ani alokacji w gorących ścieżkach.
- Scena „juice” w showcase (Tab do ekranu 2) sprawdza całość na obu platformach. Test dymny web przełącza na nią ekran, a scena wizualna `materials` pilnuje materiałów na prawdziwym GL.
