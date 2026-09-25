# 0014. Przegląd silnika po etapie 8

- Status: przyjęta
- Data: 2026-09-25
- Etap: 8 → 9

## Kontekst

Przed UI przejrzeliśmy etapy 1–8 i porównaliśmy je ze specyfikacją:
- sekcje 1–17, tabele parytetu (sekcje 9 i 10) i kod z sekcji 21;
- odłożone punkty z ADR 0006–0013;
- połączenia między podsystemami.

Nowy test integracyjny `CrossWorldTest` łączy etapy 6–8:
- encje ze wszystkimi komponentami;
- przełączanie i wyładowanie światów;
- pauzę i skalę czasu;
- wyłączenie modułu;
- teleport między światami.

## Znalezione i naprawione

- **Teleport do innego świata** (`Entity.teleport(Location)`) przenosił encję bez wiedzy komponentów:
  - collider, `Body`, `Mover` i `Trigger` zostawały w fizyce starego świata;
  - światło zostawało w `lighting()` starego świata, a emiter cząsteczek w jego systemie cząsteczek.

  Teraz komponenty wychodzą ze starego świata przez `onRemove` i wchodzą do nowego przez `onSpawn`. `Body` zachowuje prędkość.
- **Tick światów kopiował listę światów co tick.** Teraz używa listy wielokrotnego użytku.
- **Odłożone w ADR 0006 i nigdy niedokończone:**
  - komendy `/spawn <typ> [x y]`, `/tp <x> <y>` i `/reload assets`;
  - argument `Arguments.entityType`;
  - `CommandContext.reply(Text)`.
  - `/tp` przenosi encję z tagiem `player`, a bez niej kamerę.
  - Komenda gry o tej samej nazwie zastępuje komendę wbudowaną, zamiast rzucać wyjątek.
- **Braki API względem sekcji 11, 14 i 9:**
  - `World.raycast(...)` (skrót do `physics()`);
  - `TileType.data()` i `Builder.data(key, type, value)`;
  - w `gulp-core/util`: `FloatList`, `IntIntMap`, `Stopwatch`, `RollingAverage`.
- **Okno.** Tabela parytetu wymaga pełnego ekranu, okna bez ramki i wyboru monitora, a gra nie mogła zmieniać okna w trakcie działania. Doszło:
  - `display().window()` (`GameWindow`): tytuł, rozmiar, pełny ekran, `borderless`, VSync, `monitors()` i `setMonitor`;
  - `GameSettings.borderless(...)` i `monitor(...)`;
  - metody SPI w `PlatformWindow`.
  - Na webie jest jeden monitor, a ramka i VSync nic nie robią.
- **Sekcja 21 w `examples/platformer`:**
  - gracz ma `Animator` z eksportu Aseprite z regułami `auto()` (`jump`, `run`, `idle`);
  - monety kręcą się (`spin`) i przy zebraniu odlatują tweenem `Tweens.parallel(by Y, to ALPHA).onComplete(remove)`, jak w specyfikacji.

## Zgodne ze specyfikacją lub świadomie odłożone

- **Dopiero w swoich etapach:**
  - `ui()` (etap 9);
  - `saves()`, `http()` i persystencja (etap 10);
  - `debug()`, `/debug`, `/profile`, F3 i rotacja logów (etap 11).
- **Nagrywanie z mikrofonu:** opcjonalne w sekcji 16.3, nie zostało wykonane (ADR 0010).
- **Kamienie milowe, które wymagają sprzętu lub CI:** Linux i macOS, Safari i Firefox, pad na obu platformach. Zostają nieodhaczone, jak dotąd.
- **Czytniki ekranu:** decyzja z sekcji 23 była potrzebna przed etapem 9 i pozostała otwarta. Stosujemy propozycję: po 1.0.

## Konsekwencje

- Encje można bezpiecznie przenosić między światami, a menu ustawień z etapu 9 może przełączać tryby okna.
- `CrossWorldTest` pilnuje połączeń między etapami 6–8 przy kolejnych zmianach.
