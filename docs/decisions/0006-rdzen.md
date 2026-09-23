# 0006. Rdzeń (etap 1): rozstrzygnięcia szczegółów

- Status: przyjęta
- Data: 2026-09-23
- Etap: 1

## Kontekst

Sekcja 8 opisuje rdzeń, ale kilku rzeczy nie przesądza albo zakłada podsystemy z późniejszych etapów. Poniżej decyzje podjęte przy implementacji.

## Decyzje

**API rośnie z etapami.** `Engine` ma w etapie 1 tylko usługi, które już istnieją (`modules`, `events`, `scheduler`, `registries`, `services`, `commands`, `platform`, `logger`). `worlds()`, `input()`, `audio()`, `ui()`, `assets()`, `display()`, `saves()`, `preferences()`, `translations()`, `http()`, `debug()` dochodzą w etapach, które je implementują. Podobnie: `DataType.VEC2` (etap 2, razem z `Vec2`), `CommandContext.reply(Text)` (etap 4, na razie `reply(String)`), argument komendy `entityType` (etap 6), komendy `/spawn`, `/tp`, `/debug`, `/profile` i `/reload assets` (etapy 6, 11 i 3). Ustawienia wyświetlania z 8.2 (`baseResolution`, `stretchMode`, `aspectMode`, `pixelPerfect`, `icon`) dochodzą w etapie 2 razem z `Display`.

**`Owner`.** Wspólny interfejs `Game` i `GameModule` (spec mówi „zadania należą do właściciela (Game lub GameModule)”). Jego metody domyślne to skróty z 8.3 (`listen`, `on`, `run`, `later`, `every`, `command`, `key`, dostęp do usług). Skróty modułu tworzą klucze w przestrzeni nazw gry.

**Scheduler z widokami.** Metody mają nazwy ze specyfikacji (`run`, `later`, `every`), a właściciela i zegar wybiera się widokiem: `scheduler().owner(this).realtime().every(...)`. Bez `owner(...)` właścicielem jest gra. `every(period)` uruchamia się pierwszy raz po jednym okresie. `Sequence.wait(long)` koliduje z `Object.wait(long)`, więc oczekiwanie w tickach to `waitTicks(long)`. Planowanie zadań jest bezpieczne wątkowo (`@ThreadSafe`), co pozwala wrócić z kodu asynchronicznego na główny wątek.

**`Promise` w `gulp-api`.** Sekcja 7 umieszcza `Promise` w `gulp-core`, ale gra musi widzieć ten typ (`async().thenSync()`, `Config.save()`). Interfejs jest w `dev.gulp.api.scheduler`, implementacja w `gulp-core`.

**JSON i YAML w `gulp-core`, fasady w API.** `Json.parse` i `DataContainer.create()` są statyczne w API i delegują przez SPI `ApiSupport` (ładowane `ServiceLoader`) do `gulp-core`, zgodnie z sekcją 5 („własny parser JSON i podzbioru YAML w gulp-core”). `JsonValue`, `Codec` i `DataType` są typami wartości w API.

**Kod generowany i jego rejestr.** `gulp-processor` generuje `<Listener>$Handlers`, `<Rekord>$Codec` i deskryptory modułów oraz jeden publiczny `GulpIndex_<hash>` na pakiet, wpisany do `META-INF/services/dev.gulp.api.spi.GeneratedIndex`. Indeks per pakiet ma dostęp do klas pakietowo-prywatnych. Silnik czyta metadane `@ModuleInfo` z deskryptorów, więc moduł bez przetworzenia przez procesor nie wystartuje (czytelny błąd). Klasy anonimowe i lokalne nie mogą być listenerami: javac nie pokazuje ich procesorom, a silnik zgłasza brak dyspozytora przy `listen`.

**Dziedziczenie eventów przez `Class.getSuperclass()`.** Tablice handlerów per klasa eventu buduje się, idąc w górę hierarchii klas. TeaVM utrzymuje metadane hierarchii (potrzebne do `instanceof`), więc to nie jest refleksja. Do potwierdzenia buildem web w etapie 3.

**Subskrypcje przypięte do obiektu.** Event „o obiekcie” implementuje `TargetedEvent`; `events().on(typ, cel, ...)` dostarcza tylko eventy tego celu, a `events().release(cel)` kończy wszystkie jego subskrypcje (encje wywołają to przy usunięciu w etapie 6).

**Pętla.** Stały krok z akumulatorem; osobne liczniki ticków gry (pauza, `timeScale`) i ticków czasu rzeczywistego (zadania `realtime()`). Maksymalnie 5 ticków nadrabiania na klatkę; nadwyżka jest odrzucana, a klatka dłuższa niż 250 ms jest przycinana. `TickStartEvent` i `TickEndEvent` powstają tylko, gdy ktoś ich słucha.

**Start asynchroniczny.** Konfiguracje gry i modułów są czytane asynchronicznie (na webie nie da się czekać blokująco), a `onLoad` rusza w pierwszej klatce po ich wczytaniu. Wartości domyślne pochodzą z `assets/<gra>/config/<nazwa>.yml`, nadpisania z `config/<nazwa>.yml` w danych użytkownika. `save()` zapisuje scalone wartości jako YAML bez komentarzy.

**Moduły.** Brak wymaganej zależności lub nieudane `onLoad` zależności daje stan `DISABLED` (moduł nie wstaje, gra działa). `enable(id)` najpierw włącza wyłączone zależności. Moduł w stanie `FAILED` nie jest ponawiany tylko dlatego, że zależy od niego inny moduł; ponowienie wymaga jawnego `enable(id)` tego modułu.

**Sprzątanie po właścicielu.** Kolejność: usługi (ich eventy wyrejestrowania widzą jeszcze wszyscy słuchacze), komendy, zadania, listenery.

**SPI platformy: `PlatformLog` i `PlatformConsole`.** Log trafia na desktopie do terminala i `logs/latest.log` w katalogu danych (poprzedni plik zostaje jako `previous.log`; pełna rotacja w etapie 11). Konsola terminala czyta linie na wątku demona i dostarcza je przed klatką. `PlatformFiles` na desktopie powstało już w etapie 1, bo potrzebuje go `Config`: zasoby z `-Dgulp.assetsDir`, `./assets` albo z classpath `assets/`.

**Konsola deweloperska.** `GameSettings.developerConsole(...)` ma trzy stany: domyślnie dostępna tylko w buildzie deweloperskim, `true` włącza ją też w produkcji, `false` wyłącza.

**Sprawdzanie głównego wątku** (20.3) w buildzie deweloperskim: eventy, rejestry, usługi, komendy, pauza i wznowienie rzucają wyjątek z nazwą metody i wątku. Logger i planowanie zadań są bezpieczne wątkowo.

**`-Xlint:processing` wyłączone** w buildzie: przy obecnym procesorze javac ostrzega o każdej adnotacji, której żaden procesor nie przejmuje (np. `@Test` z JUnit), co przy `-Werror` psuje kompilację.

## Konsekwencje

- Gra korzystająca z modułów lub listenerów musi mieć `gulp-processor` jako procesor adnotacji. W repozytorium dodaje go ręcznie build przykładu; plugin Gradle zrobi to od etapu 3.
- Każde rozszerzenie `Engine` w kolejnych etapach to dodanie metody do interfejsu API. Przed 1.0 jest to dozwolone.
