# 0003. Wybór backendu przez `ServiceLoader`

- Status: przyjęta
- Data: 2026-09-23
- Etap: 0

## Kontekst

Etap 0 wymaga `Gulp.launch(new MyGame())` dla desktopu i headless. `Gulp` żyje w `gulp-api`, które nie może zależeć od `core` ani backendów, a sekcja 6 mówi, że „backend wybiera launcher”. Potrzebny jest sposób, by `gulp-api` znalazło backend bez refleksji w czasie działania.

## Decyzja

- `gulp-api` definiuje `dev.gulp.api.spi.GameLauncher` (`name()`, `priority()`, `launch(Game)`).
- Każdy backend rejestruje implementację w `META-INF/services/dev.gulp.api.spi.GameLauncher`: desktop (`priority` 100), headless (`priority` 0).
- `Gulp.launch` ładuje je przez `java.util.ServiceLoader` i wybiera najwyższy priorytet; `-Dgulp.backend=<name>` wymusza konkretny backend.

`ServiceLoader` jest wspierany przez TeaVM (usługi są rozwiązywane w czasie kompilacji z `META-INF/services`), więc nie łamie zakazu refleksji. Na webie punkt wejścia i tak generuje plugin Gradle (sekcja 7), który może wołać launcher web bezpośrednio.

## Konsekwencje

- Pakiet `dev.gulp.api.spi` jest częścią `gulp-api`, ale kod gry go nie używa; `checkApiUsage` (etap 11) powinno go wykluczyć.
- Działanie `ServiceLoader` pod TeaVM trzeba potwierdzić w etapie 3; jeśli zawiedzie, launcher web zostanie wywołany wprost z wygenerowanego `main`.
