# Gulp — instrukcje dla Claude

Źródło prawdy: [`docs/spec.md`](docs/spec.md) (oryginał z tabelami: [`docs/spec.pdf`](docs/spec.pdf)). Każdą sesję zacznij od przeczytania sekcji 1–8 oraz sekcji bieżącego zadania.

## Jak pracować

1. Otwórz sekcję 22 (Roadmapa) w `docs/spec.md` i weź pierwsze nieodhaczone zadanie z najniższego nieukończonego etapu. Nie przeskakuj etapów.
2. Przeczytaj specyfikację podsystemu (sekcje 8–20). Nazwy klas, metod i pakietów są wiążące, chyba że sekcja 23 lub ADR w `docs/decisions/` mówi inaczej.
3. Najpierw interfejsy w `gulp-api` z pełnym Javadoc, potem testy, potem implementacja w `gulp-core` lub backendzie.
4. Uruchom `./gradlew check` oraz (od etapu 3) `./gradlew :examples:showcase:buildWeb`. Zadanie nie jest skończone, dopóki oba przechodzą.
5. Zaktualizuj `CHANGELOG.md` i odhacz zadanie w roadmapie. Decyzję, której dokument nie opisywał, zapisz jako ADR `docs/decisions/NNNN-tytul.md`.
6. Jeśli specyfikacja jest sprzeczna, niejasna albo niewykonalna pod TeaVM — zatrzymaj się i zapytaj użytkownika.

## Twarde reguły

- Kod gry importuje wyłącznie `dev.gulp.api.*`.
- `gulp-api` i `gulp-core` muszą działać pod TeaVM: bez refleksji w czasie działania, `Class.forName`, dynamicznych proxy, `java.io.File`, `java.nio.file`, blokującego I/O, tworzenia wątków, `System.exit`.
- `gulp-api` zależy tylko od JSpecify; `gulp-core` nie ma zależności zewnętrznych. Biblioteki natywne tylko w backendach i narzędziach.
- Zero alokacji w gorących ścieżkach (render, dispatch eventów, fizyka).
- Nie kopiuj kodu z Bukkita, Spigota, Papera, Minecrafta, libGDX ani Godota.
- Stan świata zmienia się tylko na głównym wątku, w ticku.

## Konwencje

- Java 25; rekordy dla typów wartości, `sealed` dla zamkniętych hierarchii. Kod, nazwy i komentarze po angielsku; dokumentacja projektu po polsku.
- Każdy pakiet ma `package-info.java` z `@NullMarked`; `@Nullable` tam, gdzie wartość może być pusta (ADR 0004).
- Publiczne API w `gulp-api` i `gulp-platform` musi mieć Javadoc (wymusza to `-Xdoclint` + `-Werror`) z przykładem użycia.
- Pokrycie testami ≥ 80% linii w `gulp-api`, `gulp-core`, `gulp-backend-headless` (sprawdzane w `check`).
- Jednostki: pozycje w jednostkach świata (1 = kafelek), czas logiki w tickach lub `Duration`, czasy wizualne w sekundach (`float`), kąty w stopniach, kolory jako `Color`.

## Polecenia

```bash
./gradlew check                       # format, kompilacja z -Werror, testy, pokrycie
./gradlew spotlessApply               # formatowanie (palantir-java-format)
./gradlew :examples:showcase:run      # showcase na desktopie
./gradlew :examples:showcase:run -Pgulp.exitAfterFrames=120   # zamyka okno samo (CI)
./gradlew :gulp-core:test --tests "dev.gulp.core.GulpRuntimeTest"
```

Testy gier bez okna: `HeadlessRunner.start(game).step(n)` z `gulp-backend-headless` (do czasu `GameTestHarness` w `gulp-test`).

## Układ

| Moduł | Rola |
|---|---|
| `build-logic` | convention plugins (`gulp.java-conventions`, `gulp.api-conventions`, `gulp.coverage-conventions`) |
| `gulp-api` | publiczne API gry, fasada `Gulp` |
| `gulp-platform` | SPI platformy (sekcja 7) |
| `gulp-core` | implementacja API na SPI |
| `gulp-backend-desktop` / `-web` / `-headless` | implementacje platformy |
| `gulp-processor`, `gulp-test`, `gulp-tools`, `gulp-gradle-plugin` | procesor adnotacji, narzędzia testowe, CLI, plugin Gradle |
| `examples/showcase` | przykład każdej funkcji |
