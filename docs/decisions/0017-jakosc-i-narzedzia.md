# ADR 0017 — Jakość i narzędzia

Status: przyjęta (etap 11)

## Kontekst

Etap 11 dodaje narzędzia deweloperskie (20.1), raporty awarii i rotację logów (20.2), testy polityki błędów (20.3), budżety wydajności z benchmarkami i testami alokacji (20.5), pakowanie w pluginie Gradle i szablon nowego projektu. Kilka szczegółów dokument zostawia otwartych, a niektóre budżety wymagały zmian w rdzeniu.

## Decyzje

### Narzędzia deweloperskie (`dev.gulp.api.debug`)

- `debug()` zwraca `Debug`:
  - `draw()` — kształty w świecie z czasem życia w tickach;
  - `profiler()` i `section(name)`;
  - `stats()` — rekord `Stats`;
  - nakładka F3;
  - `DebugFlag`: `COLLISION`, `NAVIGATION`, `CHUNKS`, `LIGHTS`, `UI_INSPECTOR`, `ENTITY_INSPECTOR`;
  - inspektor encji.
- Narzędzia działają w buildzie deweloperskim, a w produkcji po `GameSettings.debugTools(true)`. Wyłączone są no-opami: rysowanie i sekcje profilera nic nie kosztują, więc wywołania mogą zostać w kodzie.
- F3 zwolnione bez innego klawisza przełącza nakładkę. F3 + litera przełącza flagę:
  - C — kolizje;
  - N — nawigacja;
  - G — chunki;
  - L — światła;
  - U — inspektor UI;
  - E — inspektor encji.

  Klawisze trafiają do narzędzi przed UI i grą (`InputImpl.DebugHook`). `/debug <flaga> [on|off]` i `/debug overlay` robią to samo.
- Flagi rysują istniejące `DebugView` wszystkich światów oraz dwa nowe widoki: `CHUNKS` (granice i współrzędne) i `LIGHTS` (promienie świateł). Własne `world.showDebug(...)` gry działa dalej niezależnie.
- Profiler mierzy:
  - każdy handler eventu;
  - zadania każdego właściciela;
  - `onTick` każdego typu komponentu;
  - całe ticki i klatki;
  - sekcje gry.

  Moduły są sumą swoich handlerów i zadań. Gdy profiler stoi, silnik sprawdza tylko jedno pole. Sekcje używają stosu czasów, bez alokacji. W `try (var _ = debug().section("ai"))` przykłady używają zmiennej nienazwanej, bo `-Xlint:try` ostrzega o nieużytym zasobie.
- `/profile stop` zapisuje raport (`ProfileReport.text()`) do logu i do `profiles/profile-<data>.txt` w danych użytkownika. Na webie trafia do IndexedDB, bo innego pliku nie ma.

### Logi i awarie

- Desktop:
  - `logs/latest.log` przy starcie przechodzi do `logs/<data>-<n>.log.gz`;
  - zostaje 10 archiwów;
  - raport awarii ląduje w `crash-reports/crash-<data>.txt` obok katalogu logów.
- SPI dostało `PlatformLog.crash(name, report)`:
  - desktop zapisuje plik;
  - web pokazuje raport w nakładce błędu z przyciskiem kopiowania;
  - headless zbiera raporty do testów.
- Raport (`CrashReport`) zawiera:
  - opis i stos wywołań z przyczynami (bez `java.io`, pod TeaVM);
  - grę, wersję silnika (`GulpEngine.VERSION`, podbijana przy wydaniu) i backend;
  - moduły ze stanem;
  - system, GPU i wersję Javy;
  - 50 ostatnich linii logu (`RecentLog`).
- Błąd fatalny to wyjątek startu gry albo wyjątek z pętli, który przeszedł przez wszystkie inne zabezpieczenia. Po nim desktop pokazuje czytelny ekran błędu (`CrashScreen`, rysowany drogą ekranu ładowania) do zamknięcia okna. Web zatrzymuje pętlę i pokazuje nakładkę.

### Polityka błędów (20.3)

- Komponent, którego `onTick` rzuci 3 razy z rzędu, jest wyłączany, tak jak wcześniej zadania. Handlery eventów tylko logują z właścicielem.
- **Brakujący zasób** nie kończy się już porażką, tylko zastępnikiem i ostrzeżeniem:
  - tekstura i region: magentowa szachownica;
  - dźwięk: 0,1 s ciszy;
  - font: font domyślny.

  Zastępnika nigdy się nie zwalnia. `AssetLoadFailedEvent` nadal przychodzi. Typy bez sensownego zastępnika (tekst, bajty, atlasy, muzyka, mapy) dalej zawodzą. Dlatego dwa stare testy zmieniły oczekiwania.
- Utrata kontekstu WebGL jest obsłużona w backendzie web, bez udziału rdzenia:
  - `GlJournal` pamięta, czego potrzebuje każdy obiekt GL: dane tekstur (z uzupełnianiem `texSubImage2D`), parametry, mipmapy, źródła shaderów (także już usuniętych, na potrzeby programów), wiązania atrybutów i bloków, ostatnie wartości uniformów, układy VAO, dane buforów z `bufferData`, załączniki framebufferów i renderbuffery;
  - po `webglcontextrestored` odtwarza obiekty pod tymi samymi numerami, a rdzeń niczego nie zauważa;
  - danych strumieniowanych przez `bufferSubData` (wierzchołki i indeksy batchera) dziennik nie kopiuje, bo silnik wysyła je co klatkę;
  - koszt: kopia pikseli tekstur w pamięci JS;
  - w czasie utraty pętla nie wykonuje klatek.

### Pakowanie

- `packageDesktop`:
  - `jdeps` wybiera moduły (plus stały zestaw: `java.base`, `java.logging`, `java.net.http`, `jdk.unsupported`);
  - `jlink` robi runtime;
  - `jpackage` robi instalator;
  - narzędzia pochodzą z toolchaina Javy buildu;
  - natywne biblioteki LWJGL innych systemów są pomijane.
- Domyślny typ paczki:
  - Windows: MSI, gdy WiX jest w `PATH`;
  - macOS: DMG;
  - Linux: DEB (albo RPM);
  - bez tych narzędzi: `app-image` spakowany do ZIP-a.

  Odstępstwo od 7: jpackage nie robi AppImage, więc na Linuksie bez `dpkg-deb` wychodzi ZIP z folderem aplikacji.
- Wersja aplikacji to wersja projektu zredukowana do liczb. Pierwsza liczba musi być dodatnia, bo macOS odrzuca 0.x.
- Opcje JVM paczki:
  - `-Dgulp.development=false` (build produkcyjny);
  - `--enable-native-access=ALL-UNNAMED`;
  - `--sun-misc-unsafe-memory-access=allow` (LWJGL);
  - `-XstartOnFirstThread` na macOS.
- `checkApiUsage` czyta pule stałych skompilowanych klas gry, bez biblioteki bajtkodu. Wymaga, żeby każdy typ `dev.gulp.*` spoza `dev.gulp.api` należał do samej gry (jej klas albo pakietów). Literały napisów się nie liczą. Zadanie jest częścią `check`.

### Wydajność (20.5)

- Testy alokacji (`AllocationTest`) mierzą bajty wątku głównego w dziesięciu oknach po 100 klatek, po rozgrzaniu JIT. Mediana musi być poniżej 16 B na klatkę: każdy obiekt tworzony co klatkę ma co najmniej 16 B, a szum JVM i rzadki wzrost buforów przechodzą. Sceny: sprite'y z kafelkami i HUD, fizyka (movery i bryły), eventy z zadaniami.
- Żeby to spełnić, zmieniono:
  - `Contact` jest klasą wielokrotnego użytku, z getterami liczbowymi i leniwymi wektorami (wcześniej był rekordem);
  - `Mover` trzyma prędkość i normalne jako liczby;
  - kontakty ciał mają tablicę z adresowaniem otwartym i pulę `ContactConstraint`;
  - `SpatialGrid` używa puli list komórek;
  - `TickStartEvent` i `TickEndEvent` są używane wielokrotnie przez `EventAccess` (pule eventów częstych z 20.5);
  - pętle idą po indeksach zamiast iteratorów i przechwytujących lambd.
- Rysowanie sprite'ów:
  - jedno przejście po magazynie `SpriteComponent` na klatkę i kamerę rozkłada sprite'y na warstwy (wcześniej każda z pięciu warstw przeglądała wszystkie);
  - sortowanie działa na tablicach prostych i zaczyna od kolejności poprzedniej klatki;
  - quady wyrównane do osi mają szybką ścieżkę;
  - `pop()` nie przełącza materiału bez potrzeby;
  - tick odświeża poprzednią transformację tylko przesuniętym encjom.
- `examples/bench`:
  - sceny sprite'ów (50 000), encji (20 000), cząsteczek (20 000), UI (600 widgetów) i mapy 256 × 256 z 500 encjami;
  - okno z przełączaniem 1–5;
  - tryb automatyczny: `runDesktop -Pgulp.bench` wypisuje linie `BENCH`;
  - `./gradlew :examples:bench:bench` sprawdza budżety bez okna (CPU): klatka ≤ 16,7 ms, a scena typowa tick ≤ 4 ms i render ≤ 8 ms;
  - `check` tych testów nie uruchamia, bo czasy zależą od maszyny;
  - CI uruchamia je z `-Pgulp.bench.slack=2`.
- Stan na maszynie deweloperskiej:
  - bez okna wszystkie sceny mieszczą się w budżecie (sprite'y około 14 ms);
  - w oknie z OpenGL 50 000 sprite'ów to około 23 ms na klatkę, więc cel 60 FPS dla desktopu nie jest jeszcze osiągnięty. Koszt leży po stronie wysyłki wierzchołków i sterownika. Wraca w etapie 12 (większe partie, mniej kopii).
- CI sprawdza też rozmiar `game.wasm` showcase po gzip (< 5 MB).

### Szablon

- `templates/game` to kompletna gra: akcje, komponent z `@Save`, moduł, tłumaczenia i test headless.
- Dopóki Gulp nie jest opublikowany, projekt buduje się jako build złożony z checkoutem Gulp (`gulp.home`), więc plugin i biblioteki są podstawiane ze źródeł.
- `./gradlew :gulp-tools:newGame -Pdir=... -Ppackage=... -Ptitle=...` (`NewProject`) kopiuje szablon:
  - zmienia pakiet, nazwę klasy, identyfikator i tytuł;
  - dodaje wrapper Gradle i `gulp.home`.
- CI robi projekt z szablonu i pakuje go na Linuksie, Windowsie i macOS (plus web na Linuksie).

## Konsekwencje

- Kamień milowy etapu 11 został sprawdzony lokalnie na Windowsie (paczka `app-image` uruchomiona) i dla webu. macOS i Linux sprawdza macierz CI.
- `Contact` i wektory `Mover` są współdzielone i ważne do następnego ruchu. Gra, która je przechowuje, musi skopiować wartości.
