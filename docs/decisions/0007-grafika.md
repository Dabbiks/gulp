# 0007. Matematyka i grafika (etap 2): rozstrzygnięcia szczegółów

- Status: przyjęta
- Data: 2026-09-23
- Etap: 2

## Kontekst

Sekcje 10 i 11 opisują grafikę i matematykę, ale nie przesądzają kilku konwencji. Część rzeczy zakłada też podsystemy z późniejszych etapów. Poniżej decyzje podjęte przy implementacji.

## Decyzje

**Oś Y w dół.** Dotyczy świata, ekranu i `Draw`. `Vec2.UP` to `(0, -1)`, a kąty dodatnie obracają zgodnie z ruchem wskazówek zegara na ekranie. Współrzędne są takie same jak w edytorach map i obrazach, więc nie trzeba odwracać Y przy kafelkach.

**Jednostka świata = kafelek.** `GameSettings.pixelsPerUnit` (domyślnie 16) mówi, ile pikseli logicznych zajmuje jednostka przy zoomie 1. `Draw.image(region, x, y)` bez rozmiaru rysuje obraz w naturalnym rozmiarze: na warstwach świata `1/pixelsPerUnit` jednostki na teksel, na warstwach ekranu 1 piksel logiczny na teksel.

**Kolory i blending.** Tekstury i kolory wierzchołków są w premultiplied alpha. `Pixmap` trzyma kolory proste (straight), a konwersja następuje przy wysyłaniu na GPU. Dzięki temu `NORMAL` i `PREMULTIPLIED` blendują tak samo (`ONE, ONE_MINUS_SRC_ALPHA`), a filtrowanie liniowe nie daje ciemnych obwódek.

**Batcher.** Wierzchołek ma 20 bajtów (pozycja, UV, kolor ABGR), indeksy są `short`, a batch mieści do 16 384 quadów. Dwa zestawy VAO/VBO są używane na zmianę. Wierzchołki są transformowane na CPU, więc zmiana transformacji nie przerywa batcha. Batch przerywa dopiero zmiana tekstury, shadera, blendingu, projekcji lub `clip`.

**Antyaliasing kształtów.** Kształt wypukły dostaje obwódkę szerokości jednego piksela z przezroczystymi zewnętrznymi wierzchołkami. Nie ma MSAA ani shaderów SDF, więc kształty i sprite'y idą jednym batchem z białą teksturą. Wielokąty wklęsłe są triangulowane bez obwódki.

**Tryby wyświetlania.** `StretchMode` (`DISABLED`, `CANVAS`, `VIEWPORT`), `AspectMode` (`IGNORE`, `KEEP`, `KEEP_WIDTH`, `KEEP_HEIGHT`, `EXPAND`) i skalowanie całkowite liczy `DisplayLayout`. W `VIEWPORT` klatka jest rysowana w rozdzielczości logicznej do bufora pozaekranowego, a potem powiększana z filtrem `NEAREST`. Rozmiar wyświetlania jest znany już przed `onLoad`.

**Warstwy bez świata.** Do etapu 6 domyślne warstwy trzyma `Display`. Warstwy świata to `background`, `tiles`, `entities`, `foreground` i `effects` (z = 0–400). Warstwy ekranu to `ui` i `overlay` (z = 1000, 1100). Gra rysuje w `RenderLayerEvent`. `ySort` jest zapisywany, ale zadziała dopiero z encjami (etap 6).

**Szum.** Użyto klasycznego szumu Perlina i szumu simplex (1D/2D/3D, fBm, warping). Nie użyto OpenSimplex2, żeby nie wiązać się z jego licencją i kodem. `Rng` to xoshiro256**, deterministyczny na wszystkich platformach.

**Przesunięte.** Tekst w `Draw` przesunięto do etapu 4 (fonty). Gotowe materiały (outline, flash, dissolve) przesunięto do etapu 8, a podążanie i drganie kamery oraz wiele kamer do etapu 6. Dekodowanie audio i fontów na desktopie zwraca „jeszcze nie” do etapów 5 i 4.

**Testy wizualne.** `VisualTest` w `gulp-backend-desktop` renderuje sceny w prawdziwym oknie OpenGL i porównuje zrzuty z PNG w `src/test/resources/visual`. Piksel jest różny, gdy któryś kanał różni się o więcej niż 40; test przechodzi, gdy różnych pikseli jest najwyżej 1%. Tolerancja pokrywa różnice w rasteryzacji krawędzi między sterownikami (np. AMD i Mesa llvmpipe). `check` pomija te testy, bo wymagają okna. Uruchamia je osobne zadanie `visualTest`, w CI pod Xvfb. Wzorce odświeża `-Pgulp.updateReferences`, po obejrzeniu obrazów w `build/visual`.

## Konsekwencje

- Gra rysuje wyłącznie przez `Draw` z eventów renderowania. Podczas rysowania nie powstają nowe obiekty poza eventami.
- Zmiana konwencji osi lub jednostki po etapie 6 byłaby kosztowna, bo zależą od niej świat, fizyka i mapy.
