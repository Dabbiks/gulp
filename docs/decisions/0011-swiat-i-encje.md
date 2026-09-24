# 0011. Świat i encje (etap 6): rozstrzygnięcia szczegółów

- Status: przyjęta
- Data: 2026-09-24
- Etap: 6

## Kontekst

Sekcje 11–13 opisują świat, encje, mapy kafelków i kamerę. Nie przesądzają jednak układu danych komponentów, dostępu silnika do chronionych metod `Component`, formatu komórek mapy, zasad strumieniowania chunków, obsługi formatów Tiled i LDtk ani kolejności rysowania świata względem ekranu. Poniżej decyzje podjęte przy implementacji.

## Decyzje: encje

**Przechowywanie.**
- Komponenty jednej klasy leżą w gęstym `ComponentStore`: tablica komponentów i równoległa tablica właścicieli. Usunięcie przenosi ostatni element w lukę.
- Ticki komponentów idą magazynami w kolejności `tickOrder`, a w obrębie magazynu według kolejności dodania. Nowy komponent tickuje od następnego ticka.
- `EntityQuery` nie używa refleksji. Z filtrem `with` przegląda najmniejszy pasujący magazyn zamiast wszystkich encji.

**Cykl życia.**
- `onAttach`, `onTick`, `onDetach` i pozostałe metody `Component` są chronione. Silnik woła je przez SPI `ComponentAccess` (`dev.gulp.api.spi`), które `gulp-core` instaluje przy starcie.
- Usunięcie encji jest odroczone do końca ticka. `isRemoved()` zwraca `true` od razu, a encja znika z zapytań w tej samej chwili.
- Każda encja ma własnego właściciela (`ScopedOwner`). Handlery i zadania zarejestrowane przez jej komponenty znikają razem z nią.

**Pozycje i eventy.**
- Interpolacja: przed tickiem encja zapamiętuje `prevX/prevY`, a rysowanie miesza pozycje z `alpha()` pętli. `teleport` zeruje interpolację.
- Siatka przestrzenna ma komórki 4×4 jednostki (klucz `long` z dwóch `int`, własna `LongObjectMap` bez boxingu). Encja trafia do komórek swojego prostokąta.
- Eventy celowane (`EventBus.on(type, target, …)`) trzymają handler w mapie po celu, więc event encji nie przegląda wszystkich handlerów.
- Klik i hover liczą się raz na tick, na najwyższej encji z `Interactable` pod kursorem. Domyślnie obszar to prostokąt encji, a `Interactable.area` może go zmienić.
- `EntityScreenEnter/ExitEvent` liczą się raz na tick, dla każdej encji, której prostokąt przecina widok dowolnej kamery świata. Przejście bez alokacji korzysta z siatki przestrzennej.

## Decyzje: mapa kafelków

**Komórki i chunki.**
- Chunk ma 32×32 komórki w tablicy `int[]` na warstwę.
- Komórka to indeks w palecie `TileType` świata oraz flagi obrotu w stylu Tiled w najwyższych bitach: H `0x80000000`, V `0x40000000`, D `0x20000000`. Zero oznacza pustą komórkę.
- Kafelki z `ticking` trafiają na listę chunka. Tick iteruje po kopii, więc zmiany w trakcie ticka nie gubią wpisów.
- Render: każdy chunk trzyma `Mesh2D` na teksturę, przebudowywany tylko po zmianie. Kafelki animowane rysuje się osobno w każdej klatce.

**Strumieniowanie.**
- `ChunkGenerator` działa na `PlatformExecutor` i dostaje tylko własne `ChunkData`. Wynik wraca na główny wątek przez kolejkę i jest nakładany po najwyżej 4 chunki na klatkę.
- Chunk poza zasięgiem kamer i biletów `keepLoaded` wyładowuje się po 120 klatkach.
- Chunk zmieniony po wygenerowaniu zapamiętuje się w pamięci świata, więc po powrocie wraca w zmienionej postaci. Zapis na dysk przyjdzie z persystencją w etapie 10.

**Terrain.**
- `SIXTEEN` bierze maskę krawędzi (N 1, E 2, S 4, W 8).
- `BLOB` bierze pełną maskę 8 sąsiadów i normalizuje narożniki bez obu przyległych krawędzi. Daje to 47 masek, a lista kafelków idzie w kolejności ich posortowanych wartości.

**Importy.**
- Tiled: oba formaty zapisu, XML (`.tmx`, `.tsx`) i JSON (`.tmj`, `.tsj`), także mieszane (mapa `.tmx` z zestawem `.tsj` i odwrotnie). Sekcja 13.6 wymienia tylko `.tmj`, ale domyślnym formatem Tiled jest `.tmx`, więc obsługujemy oba.
  - XML czyta `TiledXml`: mały parser (elementy, atrybuty, tekst, komentarze, CDATA, encje znakowe) zamienia dokument na to samo drzewo co JSON, więc dalej działa jeden loader. Format jest rozpoznawany po treści, nie po rozszerzeniu.
  - Warstwy kafelków we wszystkich kodowaniach Tiled: CSV, elementy `<tile>`, Base64 bez kompresji oraz z kompresją zlib, gzip i zstd. Nieskończone mapy w chunkach, warstwy obiektów i grup z przesunięciami, flagi obrotu.
  - Dekompresję robią własne dekodery w `gulp-core` (`dev.gulp.core.util.Inflate` dla DEFLATE, `Zstd` według RFC 8878), bo `gulp-core` nie ma zależności, a TeaVM nie dostarcza `java.util.zip`. Sumy kontrolne nie są sprawdzane. Słowniki zstd nie są obsługiwane, bo Tiled ich nie używa.
- LDtk: `.ldtk` z poziomami w pliku albo w `.ldtkl`. Warstwy `Tiles`, `AutoLayer` i `IntGrid` z `autoLayerTiles`, encje z polami.
  - Wartości IntGrid mapują się na `TileType` po identyfikatorze wartości przez `WorldSource.tile(...)` i trafiają do niewidocznej warstwy `<nazwa>.grid`, z której kod gry czyta kolizje.
  - Poziomy leżą w świecie według `worldX/worldY`. Warstwę tworzy i konfiguruje pierwszy poziom, a kolejne tylko dopisują kafelki.
- Nazwy plików map muszą być pisane małymi literami, jak wszystkie klucze zasobów (ADR 0009).

## Decyzje: kamera i rysowanie

- Klatka rysuje się w kolejności:
  1. warstwy aktywnego świata osobno w widoku każdej kamery;
  2. nakładka świata (`WorldText`);
  3. warstwy ekranu;
  4. przejście.
- `display().camera()` zwraca główną kamerę aktywnego świata, a bez świata kamerę ekranu.
- Wstrząs używa `trauma²`, zanika liniowo przez podany czas i przesuwa widok płynną funkcją pseudolosową, nie losowaniem na klatkę.
- Paralaksa: początek warstwy to `offset + pozycja kamery × (1 − factor)`, gdzie pozycja kamery to środek widoku.
- Przejścia z `needsFrame()` dostają obraz sceny z FBO, a pozostałe rysują na wierzchu. Zmiana świata zachodzi przy pełnym zakryciu i czeka na załadowanie świata docelowego.
- `circleWipe` rysuje jeden `Mesh2D`, bo wygładzane krawędzie osobnych trójkątów zostawiały szwy.
- `PauseMode` przeniesiono do `dev.gulp.api`, bo używają go i audio, i encje.

## Odłożone

- Persystencja encji i chunków (`@ComponentInfo`, `WorldSaveEvent`, zapis świata) przychodzi w etapie 10. Rejestr `ComponentType` istnieje, ale nie jest jeszcze używany.
- Kolizje: `TileShape` opisuje kształty, ale fizyka i `Mover` to etap 7. Przykład platformówki ma do tego czasu kilkanaście linii kolizji z siatką.
- Światło, cząsteczki i animowane sprite'y w encjach to etap 8.
- `Health` i `SoundEmitter` z listy komponentów wbudowanych (sekcja 14.4) nie były przypisane w roadmapie do żadnego etapu; weszły do etapu 7 (ADR 0012).

## Konsekwencje

- Gra dostaje świat z encjami i mapami bez refleksji i bez alokacji w ticku encji i w rysowaniu chunków, poza przebudową siatki po zmianie.
- Import map przyjmuje pliki Tiled i LDtk zapisane z domyślnymi ustawieniami edytorów, bez ręcznego eksportu.
