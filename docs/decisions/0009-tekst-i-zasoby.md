# 0009. Zasoby, tekst i fonty (etap 4): rozstrzygnięcia szczegółów

- Status: przyjęta
- Data: 2026-09-24
- Etap: 4

## Kontekst

Sekcja 13 opisuje fonty, tekst sformatowany, atlasy, hot reload i resource packi, ale nie przesądza formatów plików, podziału pracy między buildem a czasem działania ani zachowania przy braku zasobów. Poniżej decyzje podjęte przy implementacji.

## Decyzje

**Font domyślny.** Fira Sans Regular (Mozilla, SIL OFL 1.1) jako font MSDF: 32 px na em, zakres odległości 8 px. Zestaw znaków to łacina z rozszerzeniem A, grecki, cyrylica i typowa typografia (552 glify). Atlas i opis są wygenerowane raz (`./gradlew :gulp-tools:generateDefaultFont`) i leżą w zasobach `gulp-core` jako `assets/gulp/fonts/default.*` razem z licencją. Silnik ładuje go razem z konfiguracją, przed `onLoad`. Bez niego (testy bez zasobów) tekst jest pomijany, a do logu trafia ostrzeżenie. Rozmiar: PNG 844 KB, JSON 295 KB (głównie kerning, dobrze się kompresuje).

**MSDF w narzędziach.** `gulp-tools` generuje pola odległości przez LWJGL msdfgen, zgodnie z sekcją 5. Metryki i kerning bierze z układu tekstu JDK, bo on czyta tabelę GPOS, w której nowoczesne fonty (także Fira) trzymają kerning. FreeType czyta tylko starą tabelę `kern`. Format wyniku to własny JSON (`<nazwa>.msdf.json`) plus strony PNG w RGB. Krawędzie glifów zapisuje się w em względem pióra na linii bazowej, z osią Y w dół.

**Shader MSDF.** Mediana trzech kanałów, antyaliasing liczony z `fwidth` w pikselach ekranu, obrys i sztuczne pogrubienie przez przesunięcie progu. Szerokość obrysu jest ograniczona do połowy zakresu pola. Bez tego szersze obrysy wypełniały cały czworokąt glifu (znalezione na zrzucie ekranu). Cień to druga warstwa glifów z przesunięciem.

**Jeden typ fontu.** Sekcja 13.1 nazywa rodzaje fontów `MsdfFont`, `BitmapFont` i `DynamicFont`. W API jest jeden interfejs `Font` z `kind()` (`FontKind.MSDF`, `BITMAP`, `DYNAMIC`). Łańcuch fallbacków, `TextStyle` i `Draw.text` działają tak samo dla każdego rodzaju, a gra, która musi je rozróżnić (np. obrys tylko dla MSDF), sprawdza `kind()`. Osobne typy wymuszałyby rzutowania w kodzie gry bez żadnego zysku.

**Fonty bitmapowe i dynamiczne.** Fonty bitmapowe mają format BMFont (tekstowy `.fnt`) albo siatkę komórek (`graphics().gridFont`). Fonty dynamiczne są rasteryzowane przez FreeType na desktopie i przez Canvas2D z FontFace API na webie. Glify trafiają do stron 1024×1024 osobno dla każdego rozmiaru w pikselach ekranu, a metryki są mierzone przy 64 px. `PlatformDecoders.openFont` stało się asynchroniczne, bo przeglądarka ładuje fonty w tle. Przeglądarka nie mówi, których znaków font nie ma, więc na webie brakujące znaki dorysowuje jej własny font zapasowy. Nie ma wtedy naszego łańcucha fallbacków.

**Brakujące warianty.** Brak wariantu bold lub italic w `FontFamily` jest udawany: pogrubienie przez próg MSDF albo podwójne rysowanie, kursywa przez pochylenie czworokątów o 0,21.

**Tekst.**
- `Text` to niezmienne drzewo węzłów (treść, klucz tłumaczenia, obrazek) z formatowaniem dziedziczonym przez dzieci.
- `Markup` to parser w `gulp-api`. Nie rzuca wyjątków: zły tag zostaje widoczny i trafia do ostrzeżeń.
- `TextStyle` to rekord z metodami `with`. Komponenty nazywają się `isBold` i `isItalic`, bo `bold()` i `italic()` zwracają zmienione kopie.
- `TextAlign` ma 9 wartości: część poziomą i pionową.
- Układy tekstu są cache'owane (LRU, 512 wpisów) według tekstu, stylu, pudełka i wersji tłumaczeń, więc `draw.text("…")` w każdej klatce układa napis raz.
- Efekty (`wave`, `shake`, `rainbow`, `pulse`, `fade`) są liczone przy rysowaniu, bez alokacji.
- `TextLinkClickEvent` i `TextRevealCompleteEvent` są zdefiniowane, a wysyła je UI z etapu 9. Już teraz `TextLayout.linkAt` i `Draw.text(layout, x, y, visibleCharacters)` dają to, czego UI będzie potrzebować.

**Atlasy.**
- `packAssets` pakuje każdy folder `assets/<ns>/sprites/` do `sprites.atlas.json` plus strony. Obrazy są przycinane z przezroczystości, a krawędzie powielane o 1 px.
- Format to wielostronicowy JSON w stylu TexturePackera. Czytane są też warianty hash i array.
- Region ma klucz ścieżki obrazka (`AssetKey.region("coins:sprites/player/idle_0")`), a jego atlas ładuje się razem z nim jako zależność.
- W trybie deweloperskim na desktopie atlas jest pakowany z folderu przy ładowaniu. Ten sam kod (`AtlasBuilder`) służy narzędziu, więc wynik jest taki sam, a edycja sprite'a przeładowuje atlas.

**`generateAssetKeys`.** Klasa `GameAssets` powstaje w pakiecie klasy głównej. Każdy folder zasobów daje w niej klasę zagnieżdżoną:
- `textures/` → tekstury;
- `sprites/` → regiony;
- `fonts/` → fonty;
- `data/`, `shaders/`, `lang/` → tekst;
- pozostałe foldery → bajty.

Przestrzeń `gulp` jest pomijana.

**Manifest i klucze bez rozszerzenia.**
- Manifest obejmuje też zasoby z jarów frameworka (np. font domyślny), a build web je kopiuje.
- Bez manifestu (np. uruchomienie z IDE) silnik sprawdza rozszerzenia po kolei.
- Na desktopie w trybie deweloperskim manifest jest składany na żywo z folderu źródeł, więc nowe pliki działają bez przebudowy.
- `runDesktop` ustawia `gulp.assetsDir` na `src/main/resources/assets`.

**Hot reload.**
- `PlatformFiles.watchAssets` na desktopie używa `WatchService`, sprawdzanego raz na klatkę. Web ignoruje to wywołanie, bo tam stronę przeładowuje `runWeb`.
- Tekstury podmieniają piksele w tym samym obiekcie GL (`TextureImpl.replace`), a atlasy podmieniają zawartość w tym samym obiekcie.
- Tekst, bajty, obrazy i fonty dostają nowe wartości. Gra odświeża się na `AssetReloadEvent`.
- Pliki `lang/` przeładowują tłumaczenia, a pliki konfiguracji swój `Config`.

**Resource packi.**
- Na desktopie to foldery albo ZIP-y w `resourcepacks/` katalogu danych gry, z plikami w `assets/` i opisem w `pack.txt`.
- Na webie `bundleResourcePacks` kopiuje `resourcepacks/` projektu do strony razem z `index.json`.
- Włączone paczki nadpisują pliki po ścieżce: pierwsza na liście wygrywa.
- Zmiana listy przeładowuje wszystkie załadowane zasoby i wysyła `ResourcePackChangeEvent`.

**Tłumaczenia (podstawa etapu 10).**
- Pliki to `assets/<ns>/lang/<locale>.json`, płaskie albo zagnieżdżone. Gra ma pierwszeństwo przed przestrzenią `gulp`.
- Kolejność szukania: locale, potem jego język, potem `GameSettings.defaultLocale` (domyślnie `en_us`), a na końcu sam klucz z ostrzeżeniem.
- Argumenty to tylko pozycyjne `{0}`. Liczba mnoga i formaty przyjdą w etapie 10.
- Język startowy to `GameSettings.locale` albo język systemu.

## Konsekwencje

- Gra spoza repozytorium dostaje `gulp-tools` w wersji pluginu przez konfigurację `gulpTools`, tak jak backendy.
- Font domyślny zwiększa paczkę web o około 0,9 MB po kompresji. Mniejszy zestaw znaków byłby niezgodny z sekcją 13.1.
- Zadania TeaVM mają jawnie cały classpath jako wejście i są wyłączone z build cache. W jednym przebiegu stary `game.js` przetrwał zmianę silnika, a test dymny złapał to w wersji JS.
