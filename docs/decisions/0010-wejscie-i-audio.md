# 0010. Wejście i audio (etap 5): rozstrzygnięcia szczegółów

- Status: przyjęta
- Data: 2026-09-24
- Etap: 5

## Kontekst

Sekcja 16 opisuje akcje wejścia, urządzenia i audio. Nie przesądza jednak typów przypisań, modelu stanów „per tick”, formatu zapisu przypisań, podziału miksowania między Javę a platformę ani zachowania na platformach bez dźwięku. Poniżej decyzje podjęte przy implementacji.

## Decyzje: wejście

**Przypisania.**
- `Binding` to zamknięta hierarchia czterech typów: `KeyboardKey` (rekord z kodem USB HID), `MouseButton`, `GamepadButton` i `AxisDirection` (jeden kierunek osi, z `GamepadAxis.LEFT_X.negative()`).
- `Keys` to stałe `KeyboardKey`. Klawisze są nazwane według pozycji w układzie US, więc WASD działa na AZERTY i QWERTZ.
- Każde przypisanie ma stabilny identyfikator (`key:space`, `mouse:left`, `pad:south`, `axis:left_x-`) i `Binding.parse`.
- Przyciski pada są nazwane pozycją (`SOUTH`, `EAST`, …) w układzie standardowym W3C. Ten sam układ ma SPI na każdym backendzie: 17 przycisków, 6 osi, triggery 0..1.

**Stany per tick.**
- Naciśnięcie klawisza lub przycisku jest zatrzaskiwane do najbliższego ticka. Dzięki temu naciśnięcie i puszczenie między tickami daje `justPressed` w jednym ticku i `justReleased` w następnym.
- Stany akcji liczą się przed każdym tickiem gry, a gdy gra jest zapauzowana, przed tickiem czasu rzeczywistego.
- Akcja łączy wszystkie przypisania: klawiaturę, mysz i wszystkie podłączone pady. Każdy pad z osobna jest dostępny przez `input.gamepad(i)`.
- Siła akcji to maksimum z przypisań. Oś ma martwą strefę akcji (domyślnie 0,2) i jest przeskalowana do 0..1. Akcja jest wciśnięta od połowy siły.
- `vector` liczy martwą strefę po promieniu na surowych wartościach, a długość wyniku nie przekracza 1.
- `mouseDeltaX/Y` i `scrollX/Y` to sumy z ostatniego ticka. `MouseMoveEvent`, `PanEvent` i `PinchEvent` są wysyłane najwyżej raz na klatkę.

**Zestawy akcji.**
- Akcja należy do jednego `ActionSet` (domyślnie `GAMEPLAY`).
- Wyłączony zestaw daje stan „puszczony”, a zwolnienie trzymanej akcji wysyła `justReleased`.
- Wyłączanie gameplay przez `Screen` z `blocksGameplayInput` przyjdzie z UI w etapie 9. Konsumpcję przez UI zapewniają już `InputEvent.isConsumedByUi()` i `consumeByUi()`.

**Zmiana przypisań.**
- Przypisania są w slotach (indeks domyślnej listy). `rebind` na slocie o jeden za ostatnim dodaje nowy slot.
- Zmienione przypisania trafiają do `preferences()` pod kluczem `input.bindings.<klucz akcji>` jako identyfikatory rozdzielone przecinkami. Pusty element oznacza pusty slot.
- Powrót do przypisań domyślnych usuwa klucz.
- `captureNextInput` łapie klawisz, przycisk myszy, przycisk pada albo wychylenie gałki ponad połowę. Złapane wejście nie uruchamia akcji, dopóki nie zostanie puszczone.
- `conflicts` zwraca inne akcje z tego samego zestawu, które używają danego przypisania.

**Ikony przypisań.**
- `binding.glyph()` zwraca `BindingGlyph` z etykietą (np. A, Cross albo B dla `SOUTH` na Xbox, PlayStation i Nintendo) oraz ścieżką ikony (`input/xbox/south`).
- Rodzinę kontrolera zgaduje `ControllerFamily.fromName` z nazwy pada. `input.controllerFamily()` zwraca rodzinę urządzenia użytego ostatnio.
- Silnik nie dostarcza obrazków ikon: motyw UI (etap 9) szuka ścieżki we własnym atlasie, a bez ikony rysuje etykietę na klawiszu. Grafika kontrolerów to osobne licencje i duża paczka.

**Dotyk.**
- Pierwszy palec steruje myszą: pozycją i lewym przyciskiem, z eventami.
- Gesty są rozpoznawane w `gulp-core`, więc działają tak samo wszędzie i da się je testować headless:
  - tap: najwyżej 0,3 s, najwyżej 12 pkt ruchu;
  - double tap: najwyżej 0,35 s i 40 pkt od poprzedniego;
  - long press: 0,5 s bez ruchu;
  - pan;
  - pinch: dwa pierwsze palce;
  - swipe: najwyżej 0,4 s, co najmniej 60 pkt i 400 pkt/s.
- Wirtualny joystick to widget UI (etap 9).

**Kursory i tekst.**
- `Cursor` to `SystemCursor` (8 kształtów) albo obraz z `Pixmap` lub `TextureRegion`. Region jest odczytywany z GPU przez FBO.
- `startTextInput(Rect)`:
  - na desktopie włącza IME GLFW 3.5 (`GLFW_IME`, prostokąt preedit);
  - na webie ustawia ukryte pole `<input>` nad polem tekstowym i przenosi na nie fokus, co otwiera klawiaturę ekranową. Tekst przychodzi ze zdarzeń `input` i `compositionend`.

**Gamepady.**
- Desktop używa API gamepadów GLFW z wbudowaną bazą mapowań SDL. Joysticki 0–3 to sloty, a triggery są przeliczane z −1..1 na 0..1. GLFW nie ma wibracji, więc `supportsRumble()` zwraca `false`.
- Web używa Gamepad API w układzie `standard` i wibracji `vibrationActuator.playEffect("dual-rumble")`, jeśli przeglądarka ją ma.
- Stan padów jest odpytywany raz na klatkę, a podłączenia wykrywa porównanie z poprzednią klatką.

## Decyzje: audio

**Mikser w Javie nad pulą głosów.** Zgodnie z sekcją 5 o wszystkim decyduje `gulp-core`, a platforma tylko odtwarza bufory na 32 głosach:
- szyny (`master`, `music`, `sfx`, `ui`, `voice`, własne);
- głośność, wyciszenie, ściszanie (`voice` ścisza `music` do 0,35, płynnie w około 0,2 s);
- filtry i pogłos;
- pauza według `PauseMode`;
- pozycja: tłumienie liniowe między `minDistance` a `maxDistance` i panorama z przesunięcia w poziomie. Słuchaczem jest kamera ekranu albo `setListener`.

Każda wartość jest wysyłana do platformy tylko wtedy, gdy się zmieniła.

**Limity i priorytety.**
- `Sound` ma listę plików (losowany wariant), zakres głośności i wysokości, `maxInstances`, `minInterval` (domyślnie 0,03 s), szynę, priorytet i odległości.
- Przy pełnej puli milknie głos o najniższym priorytecie, potem najcichszy, potem najstarszy. Jeśli ofiara ma wyższy priorytet niż nowy dźwięk, nowy jest pomijany. Muzyka ma najwyższy priorytet.
- Pominięty dźwięk zwraca `Playback`, który „nie gra”, zamiast `null`.

**Zasoby.**
- `AssetType.AUDIO` (`AudioClip`, zdekodowany w pamięci) i `AssetType.MUSIC` (`Music`, plik zakodowany w pamięci, dekodowany w kawałkach).
- `generateAssetKeys` tworzy z folderów `sounds/` i `music/` klasy `GameAssets.Sounds` i `GameAssets.Music`.
- Pliki dźwięków zarejestrowanych w `Registries.SOUND` dochodzą do grupy startowej, więc `audio().play(PICKUP)` działa od `onStart`.

**Strumienie.**
- Muzyka i `stream(PcmSource)` używają kolejki trzech buforów po 4096 ramek, uzupełnianej raz na klatkę.
- Punkty pętli są w ramkach. Przy końcu pętli dekoder przeskakuje do jej początku, więc pętla jest dokładna co do ramki.
- Playlista ignoruje pętle utworów, a z crossfade zaczyna następny utwór, gdy do końca zostaje czas przejścia.
- `MusicEndEvent` jest wysyłany, gdy utwór bez pętli dojdzie do końca albo gdy playlista przechodzi dalej.

**Desktop.**
- OpenAL Soft (LWJGL), 32 źródła. Dekodowanie: OGG przez stb_vorbis (całość na wykonawcy, strumień na wątku głównym), WAV przez `WavDecoder` z `gulp-core` (PCM 8/16/24/32 bit i float, mono i stereo).
- Filtry i pogłos przez EFX: filtr pasmowy na ścieżce bezpośredniej i filtr wzmocnienia na wysyłce do jednego slotu pogłosu. EFX podaje filtry jako wzmocnienie przy częstotliwości odniesienia (5 kHz i 250 Hz), więc częstotliwość odcięcia jest przeliczana na wzmocnienie w przybliżeniu.
- Panorama dotyczy tylko buforów mono.
- Bez urządzenia audio gra działa bez dźwięku, a w logu jest ostrzeżenie.

**Web.**
- WebAudio. Każdy głos: filtr dolno- i górnoprzepustowy (biquad), wzmocnienie, `StereoPanner` i wysyłka do wspólnego `ConvolverNode` z generowaną odpowiedzią impulsową.
- `AudioBufferSourceNode` nie ma pauzy: pauza zapamiętuje pozycję, a wznowienie tworzy nowe źródło od tej pozycji.
- Bufory strumienia są planowane jeden po drugim na zegarze `AudioContext`.
- Kontekst odblokowuje się sam przy pierwszym kliknięciu, klawiszu lub dotyku. Wcześniej `isUnlocked()` zwraca `false`: dźwięki są pomijane, a muzyka czeka i startuje po odblokowaniu.
- Dekoduje przeglądarka (`decodeAudioData`), więc muzyka jest trzymana w przeglądarce w całości i czytana kawałkami.
- Bez WebAudio (np. WebKit headless na Windows) WAV dekoduje Java, inne formaty dają ciszę, a gra działa bez dźwięku.

**Czego nie ma.**
- Nagrywania z mikrofonu (w sekcji 16.3 opcjonalne): nie jest w zadaniach etapu 5.
- Osobnych filtrów dla szyny: filtr szyny trafia do każdego jej głosu, a `master` łączy się z nim (najniższy low-pass, najwyższy high-pass).

## Decyzje: preferencje

- `Preferences` (w `dev.gulp.api.data`) to płaski magazyn typów tekst, liczba całkowita, liczba zmiennoprzecinkowa i flaga.
- Plik to `preferences.json` w danych użytkownika (na webie IndexedDB). Wczytuje się razem z konfiguracją, przed `onLoad`.
- Zapis następuje pół sekundy po ostatniej zmianie i przy zamknięciu. Silnik przechowuje tam przypisania (`input.bindings.*`) oraz głośność i wyciszenie szyn (`audio.<szyna>.volume`, `.muted`).
- Pełny zestaw ustawień wbudowanych przyjdzie w etapie 10.

## Konsekwencje

- `PlatformAudio` dostało `setFilter`, `setReverb` i `setLooping`, a `PlatformDecoders` — `openAudioStream` z nowym `PlatformAudioStream`.
- `PlatformInput` dostało `supportsRumble` i `setTextInput`, a `PlatformWindow` — kursory systemowe i własne.
- Headless ma symulowany zegar audio: głosy kończą się po czasie bufora, a strumienie zużywają kolejkę. Są też liczniki, stan filtrów, pauzy i kursora oraz `setUnlocked(false)` do testów odblokowania.
- Spotless nie formatuje już źródeł generowanych (`build/**`).
