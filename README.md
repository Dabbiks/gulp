# Gulp

Framework gier 2D w Javie 25 dla desktopu (Windows, macOS, Linux) i przeglądarki. Pisanie gry ma przypominać pisanie pluginu serwerowego: klasa główna z cyklem życia, moduły, listenery eventów, zadania w schedulerze i rejestry z kluczami. Sceny i UI buduje się wyłącznie kodem.

> Status: **etap 4 z 12** (kamień milowy etapu 3 czeka na potwierdzenie w Firefoksie) — rdzeń (pętla o stałym kroku, moduły, eventy, scheduler, rejestry, usługi, konfiguracja YAML, komendy), matematyka i grafika 2D (batcher, `Draw`, kamera, tryby wyświetlania), web (TeaVM Wasm GC + JS, plugin Gradle) oraz zasoby i tekst (atlasy, fonty MSDF, bitmapowe i dynamiczne, markup z efektami, tłumaczenia, hot reload, resource packi). API nie jest jeszcze stabilne. Plan: [`docs/spec.md`](docs/spec.md), sekcja 22.

## Minimalna gra

```java
public final class MyGame extends Game {
    public static void main(String[] args) {
        Gulp.launch(new MyGame());
    }

    @Override public String id() { return "mygame"; }

    @Override public void configure(GameSettings settings) {
        settings.title("My Game").windowSize(960, 540).clearColor(Color.rgb(0x1d2b53));
    }

    @Override public void onStart() {}
}
```

Część gry jako moduł (wymaga `gulp-processor` jako procesora adnotacji):

```java
@ModuleInfo(id = "economy")
final class EconomyModule extends GameModule {
    int coins;

    @Override public void onEnable() {
        coins = config().getInt("start", 0);            // assets/mygame/config/economy.yml
        every(20, () -> coins += 1);                     // co 20 ticków, anulowane przy wyłączeniu
        command("coins", ctx -> ctx.reply("Coins: " + coins));
    }
}
```

## Budowanie

Wymagany JDK 25 (Gradle znajdzie go przez `JAVA_HOME` lub w standardowych lokalizacjach).

```bash
./gradlew check
```

```bash
./gradlew :examples:showcase:runDesktop          # okno na desktopie
./gradlew :examples:showcase:runWeb --continuous  # http://localhost:8080, przeładowanie po zmianach
./gradlew :examples:showcase:buildWeb            # statyczna strona w build/web
```

## Moduły

| Moduł | Rola |
|---|---|
| `gulp-api` | jedyne pakiety, które importuje kod gry (`dev.gulp.api.*`) |
| `gulp-platform` | interfejsy platformy: grafika, okno, input, audio, pliki, czas, wątki |
| `gulp-core` | implementacja API, działa pod TeaVM |
| `gulp-backend-desktop` | LWJGL 3: GLFW, OpenGL 3.3 core |
| `gulp-backend-web` | TeaVM (Wasm GC + JS): WebGL2, DOM, IndexedDB; WebAudio w etapie 5 |
| `gulp-backend-headless` | bez okna i dźwięku, do testów i CI |
| `gulp-processor` | procesor adnotacji (etap 1) |
| `gulp-test` | narzędzia testowe dla gier |
| `gulp-tools` | packer atlasów, generator fontów, importery |
| `gulp-gradle-plugin` | plugin `dev.gulp.game` dla projektów gier (included build): `runDesktop`, `buildWeb`, `runWeb`, `packageWeb` |

## Licencja

[Apache 2.0](LICENSE). Nazwa „Gulp” nie ma związku z narzędziem JavaScript gulp.js.
