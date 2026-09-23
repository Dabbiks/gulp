# 0005. Pokrycie liczone ze wszystkich zestawów testów

- Status: przyjęta
- Data: 2026-09-23
- Etap: 1

## Kontekst

Definicja ukończenia wymaga pokrycia logiki co najmniej 80%. JaCoCo domyślnie liczy pokrycie modułu tylko z jego własnych testów. Zachowanie `gulp-api` (skróty `Owner`, `GameModule.require`, `Game.config`, `Codec.of`) da się sensownie sprawdzić wyłącznie z działającym silnikiem, czyli w testach `gulp-core` na backendzie headless. Powielanie tych samych scenariuszy w `gulp-api` byłoby sztuczne.

## Decyzja

`gulp.coverage-conventions` weryfikuje próg 80% linii na podstawie danych wykonania z testów `gulp-api`, `gulp-core` i `gulp-backend-headless` razem. Raport `jacocoTestReport` (HTML i CSV) używa tych samych danych.

## Konsekwencje

- Pokrycie modułu oznacza „ile jego kodu wykonuje zestaw testów frameworka”, a nie „ile pokrywają testy leżące w tym module”.
- `check` w module z pokryciem zależy od testów pozostałych dwóch modułów.
- Stan po etapie 1: `gulp-api` 90%, `gulp-core` 97%, `gulp-backend-headless` 99,8%.
