# 0012. Fizyka, nawigacja i AI (etap 7): rozstrzygnięcia szczegółów

- Status: przyjęta
- Data: 2026-09-24
- Etap: 7

## Kontekst

Sekcja 15 opisuje oba poziomy fizyki, zapytania, nawigację i AI. Nie przesądza jednak:
- podziału API między komponenty a świat;
- sposobu, w jaki komponenty z `gulp-api` rozmawiają z symulacją w `gulp-core`;
- reprezentacji kształtów i kolizji z kafelkami;
- szczegółów solvera i usypiania;
- sposobu rozkładania szukania ścieżek na klatki.

Część kodu z sekcji 21 wymaga też etapów 8–10. Poniżej decyzje podjęte przy implementacji.

## Decyzje: API

- **Gdzie jest fizyka.**
  - Fizyka należy do świata: `world.physics()` daje grawitację, macierz kolizji, zapytania i złącza, a `world.navGrid()` siatkę nawigacji.
  - Zapis `physics().setCollides(...)` z sekcji 15.1 wywołuje się więc na świecie (w komponencie: `world().physics()`).
- **Warstwy kolizji.**
  - `CollisionLayer` to klasa z `of(Key)`.
  - Bity masek nadaje silnik po zamrożeniu rejestrów, w kolejności rejestracji, najwyżej 32.
  - Silnik rejestruje `gulp:default` (bit 0) i `gulp:tiles` (bit 1), a kafelki kolizyjne leżą na warstwie `gulp:tiles`.
  - `CollisionMask` to zbiór warstw z `ALL`, `NONE`, `of`, `with` i `without`.
  - Dwa kształty się stykają, gdy maska każdego zawiera warstwę drugiego i pozwala na to macierz świata. Ta sama reguła dotyczy triggerów.
- **Komponenty.**
  - `Collider` daje kształt, warstwę i maskę, także `Mover`, `Trigger` i `Body` na tej samej encji. Sam `Collider` jest statyczną przeszkodą.
  - Bez `Collider` używany jest prostokąt o rozmiarze encji, zgodnie z sekcją 15.2.
  - `Collider.oneWay(true)` robi z encji platformę jednokierunkową (na przykład windę).
- **Most do rdzenia.**
  - Komponenty w API rozmawiają z symulacją przez SPI `PhysicsAccess`, które rdzeń instaluje przy starcie, na tym samym wzorcu co `ComponentAccess`.
  - `Body` przed dodaniem do świata trzyma prędkość sam, a potem korzysta z uchwytu `BodyHandle`.
- **Mover.**
  - `moveAndSlide` dodaje grawitację świata. `jump(speed)` skacze albo zapamiętuje skok na czas bufora; dlatego sterowanie może wołać `jump` bez `canJump()`.
  - `contacts()` to widok, który zmienia się przy następnym ruchu, żeby nie alokować listy w każdym ticku.
- **Eventy.**
  - `EntityCollideEvent` i `EntityCollideEndEvent` dostaje każda z dwóch encji. Normalna wskazuje od drugiej rzeczy do encji, a dla kafelka `other()` jest `null` i ustawione jest `tile()`.
  - `NavTargetReachedEvent` i `NavPathFailedEvent` mają `destination()`, bo `target()` jest już zajęte przez eventy celowane.
- **Health i SoundEmitter.** Oba komponenty z sekcji 14.4, dotąd bez etapu w roadmapie, weszły do etapu 7.
  - `DamageType` jest klasą z `of(Key)`, a silnik rejestruje `gulp:generic`.
  - Śmierć nie usuwa encji; decyduje o tym gra w `EntityDeathEvent`.
- **Debug.**
  - `world.showDebug(DebugView, boolean)` rysuje nad światem kształty, kontakty, złącza, triggery, siatkę nawigacji, ścieżki i wektory sterowania.
  - Przełączniki F3 i komenda `/debug` z sekcji 20.1 wejdą w etapie 11.

## Decyzje: rdzeń fizyki

- **Kształty.**
  - Każdy kształt rozkłada się na wypukłe kawałki z promieniem zaokrąglenia: koło to punkt z promieniem, kapsuła to odcinek z promieniem, wielokąt ma 3–8 wierzchołków.
  - Wielokąty wklęsłe dzieli odcinanie uszu i scalanie trójkątów w części wypukłe (Hertel–Mehlhorn). Wielokąty samoprzecinające się są odrzucane.
- **Wąska faza.**
  - SAT z przycinaniem krawędzi odniesienia daje do dwóch punktów kontaktu, z osobnymi przypadkami dla koła.
  - Dla kawałków zaokrąglonych o kierunku rozstrzygają najbliższe cechy (wierzchołek–krawędź).
  - Szeroka faza to jednolita siatka 4×4 jednostki, jak siatka encji z etapu 6.
- **Kafelki.**
  - Kolizją są kafelki warstw z `isCollision()`, których `TileShape` ma wielokąt, z uwzględnieniem flag obrotu Tiled. Tylko na mapach ortogonalnych.
  - Ściany pełnego kafelka stykające się z innym pełnym kafelkiem są pomijane (krawędzie wewnętrzne), żeby ciała i postacie nie zaczepiały o styki.
- **Ruch kinematyczny.**
  - Zgodnie z sekcją 15.2: podkroki nie dłuższe niż mniej więcej połowa postaci, potem osobno oś X i oś Y z wypychaniem SAT.
  - Powierzchnia w granicach `maxFloorAngle` wypycha pionowo, więc po zboczu idzie się z pełną prędkością poziomą. Ściany wypychają w bok.
  - Rogi, gdzie zbocze spotyka płaski teren, i niskie progi są traktowane jak podłoga.
  - Dochodzą do tego stopnie (`stepHeight`), przyciąganie do podłogi, unoszenie przez ruchomą encję pod spodem i platformy jednokierunkowe (tylko przy spadaniu, gdy spód był nad platformą).
- **Bryły sztywne.**
  - Impulsy sekwencyjne z miękkimi więzami i podkrokami, na podstawie opublikowanych opisów metody „soft step”.
  - Każdy podkrok: całkowanie prędkości, rozgrzanie impulsami z poprzedniego ticka, rozwiązanie z korekcją położenia, całkowanie położeń i relaksacja bez korekcji. Na końcu sprężystość.
  - Kontakty: 30 Hz przy tłumieniu 10, kontakty spekulatywne do 4 × `SLOP`. Złącza: 60 Hz przy tłumieniu 5.
  - Kontakty dwupunktowe rozwiązuje solver blokowy (LCP 2×2). Bez niego twarde, płaskie lądowanie przewracało skrzynię, bo pierwszy róg przejmował całe uderzenie.
- **Usypianie.**
  - Wyspy łączą bryły dynamiczne stykające się ze sobą lub połączone złączami. Wyspa zasypia, gdy wszystkie jej bryły są spokojne przez 0,5 s.
  - Ruchome bryły kinematyczne i przesuwane przez grę kollidery budzą śpiące bryły w zasięgu.
- **CCD.**
  - Bryła, która w ticku przesunęła się o więcej niż połowę swojego rozmiaru, zatrzymuje się na pierwszej przeszkodzie statycznej wzdłuż ruchu (konserwatywne przybliżanie). Z `bullet(true)` dzieje się tak zawsze.
  - Pociski nie są sprawdzane przeciwko innym bryłom dynamicznym.
- **Złącza.**
  - Kotwice zapisane są w układzie bryły względem środka masy.
  - Silniki (i złącze silnikowe) działają w obu przebiegach solvera. Bez tego relaksacja zerowała ich prędkość i bryła zasypiała przed celem.
- **Alokacje.** Wąska faza, solver i ruch kinematyczny nie alokują na parę ani na punkt. Nowe obiekty powstają tylko przy nowej parze kontaktu i przy eventach.

## Decyzje: nawigacja i AI

- **Siatka nawigacji.**
  - `NavGrid` czyta mapę na bieżąco, bez budowania osobnej struktury, więc działa też w nieskończonych światach z chunków.
  - Komórkę blokuje kafelek warstwy kolizyjnej z `isPassable() == false` (domyślnie: kształt kolizyjny) albo blokada `block(rect, owner)`. Koszt to największy `navCost` kafelków w komórce.
- **Algorytmy.**
  - A* obsługuje 4 lub 8 kierunków bez ścinania rogów.
  - Jump Point Search traktuje każdą przejezdną komórkę jako koszt 1 i szuka w prostokącie wokół startu i celu z marginesem 64 komórek. Bez takiej granicy skany na otwartej płaszczyźnie nie miałyby końca.
  - `AUTO` wybiera JPS dopiero powyżej 48 komórek odległości.
  - Wygładzanie usuwa punkty, między którymi jest linia widoczności.
- **Rozkładanie na klatki.**
  - `requestPath` wykonuje się przyrostowo ze wspólnym budżetem 4000 komórek na tick, na każdej platformie, więc wynik jest powtarzalny.
  - `findPath` liczy od razu.
- **Pola przepływu** to Dijkstra w kwadracie o podanym promieniu. Kierunek wskazuje najtańszą sąsiednią komórkę.
- **AI.**
  - `StateMachine`, `BehaviorTree` i `Steering` są komponentami zaimplementowanymi w całości w API; to czysta logika, bez zależności od rdzenia.
  - Węzły złożone drzewa zachowań pamiętają działające dziecko: kolejne ticki wracają do niego, a nie sprawdzają od początku.
  - Siła sterująca próbuje osiągnąć pożądaną prędkość w jednym ticku, ograniczona przez `maxForce`.

## Odłożone i ograniczenia

- **Sekcja 21.** Z kodu przykładu działa część fizyczna i modułowa. `Animator` i `Tweens` przyjdą w etapie 8, `ui()` i `State` w etapie 9, `GameSaveEvent` w etapie 10. `examples/platformer` pokazuje etap 7 w tej samej strukturze modułów.
- **Wartość `smoothing(6)` z sekcji 21:** `Camera.smoothing` przyjmuje sekundy (etap 6), więc przykład używa 0,15 s.
- **Kafelki na mapach izometrycznych i heksagonalnych** nie kolidują.
- **Movery nie popychają brył dynamicznych.** Traktują je jak przeszkody, a bryły traktują movery jak ciała kinematyczne o ich prędkości.

## Konsekwencje

- Gra dostaje oba poziomy fizyki, nawigację i AI bez zależności i bez refleksji, więc wszystko działa pod TeaVM.
- Stos 10 skrzyń i piramida stoją i zasypiają, łańcuchy na złączach się nie rozrywają (testy w `BodyTest` i przykład `examples/sandbox`), a wrogowie w `examples/topdown` szukają drogi do gracza.
