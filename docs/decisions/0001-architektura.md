# 0001. Architektura repozytorium i warstwy platformy

- Status: przyjęta
- Data: 2026-09-23
- Etap: 0

## Kontekst

Specyfikacja (sekcje 3, 6, 7) opisuje podział na 11 modułów, zależności `api ← core ← backend-*` i interfejsy SPI platformy. Kilka szczegółów trzeba było ustalić przy zakładaniu repozytorium, bo dokument ich nie przesądza.

## Decyzja

**Moduły.** Jeden build Gradle; `build-logic` jest buildem dołączonym (`includeBuild`) z trzema convention pluginami:

| Plugin | Co robi | Gdzie |
|---|---|---|
| `gulp.java-conventions` | toolchain Java 25, `-Xlint:all -Werror`, Spotless (palantir), JUnit + AssertJ, JaCoCo | wszystkie moduły |
| `gulp.api-conventions` | dodatkowo `-Xdoclint:all/protected`: brak Javadoc na publicznym elemencie psuje build | `gulp-api`, `gulp-platform` |
| `gulp.coverage-conventions` | `jacocoTestCoverageVerification` z progiem 80% linii, podpięte pod `check` | `gulp-api`, `gulp-core`, `gulp-backend-headless` |

Pokrycie nie jest wymagane w `gulp-backend-desktop` (kod wymaga okna i GPU; testowane są tylko czyste funkcje) ani w `gulp-platform` (same interfejsy).

**Demon Gradle na JDK 25.** `gradle/gradle-daemon-jvm.properties` przypina demona do Javy 25, bo `gulp-gradle-plugin` jest kompilowany do bajtkodu 25 i musi się dać załadować w buildach gier.

**SPI platformy bez `Promise`.** `gulp-platform` nie zależy od niczego, a `Promise<T>` ma według sekcji 7 żyć w `gulp-core`. Operacje asynchroniczne w SPI przyjmują więc `PlatformCallback<T>` (`success` / `failure`, zawsze na głównym wątku między klatkami); `gulp-core` opakuje je w `Promise` w etapie 1.

**Zero alokacji w SPI.** `Gl` używa uchwytów `int` i buforów NIO; `InputListener` dostaje wyłącznie argumenty prymitywne.

**Headless.** Callbacki plików, dekoderów i sieci są dostarczane na początku następnej klatki (tak jak na prawdziwych platformach), a `PlatformExecutor` wykonuje zadania synchronicznie, zgodnie z tabelą w sekcji 7. Dekodery to stuby (obraz 1×1, jedna ramka ciszy, font z kwadratowymi glifami).

**Desktop w etapie 0.** Zaimplementowane są pętla, okno, `Gl`, executor (wątki wirtualne) i `PlatformInfo`. Pozostałe usługi (`input`, `audio`, `files`, `decoders`, `net`, `modules`) rzucają `UnsupportedOperationException` z numerem etapu, w którym powstaną.

**Minimalne `Game`, `GameSettings` i `Color` już w etapie 0.** Launcher musi coś uruchomić, a kamień milowy wymaga koloru tła. `Game` ma tylko cykl życia, `GameSettings` tylko ustawienia okna i TPS, `Color` jest pełnym rekordem wartości. Etap 1 rozszerza `Game` (`engine()`, `logger()`, `config()`, `key()`) i `GameSettings` (`modules`, rozdzielczość bazowa i reszta z 8.2). Tymczasowy `GulpRuntime` w `gulp-core` zostanie zastąpiony przez `Engine`.

**`System.exit` tylko w backendzie desktop.** Restart JVM z `-XstartOnFirstThread` na macOS kończy proces rodzica kodem wyjścia dziecka; to dozwolone, bo zakaz z sekcji 1 dotyczy `gulp-api` i `gulp-core`.

## Konsekwencje

- Kod gry w `examples/*` ma na classpath kompilacji tylko `gulp-api`; backend jest zależnością `runtimeOnly`, więc import z `core` lub backendu nie skompiluje się jeszcze przed `checkApiUsage` (etap 11).
- Moduły `gulp-backend-web`, `gulp-processor`, `gulp-test`, `gulp-tools` są pustymi szkieletami z `package-info.java`.
