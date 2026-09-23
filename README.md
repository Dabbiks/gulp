# Gulp

Framework gier 2D w Javie 25 dla desktopu (Windows, macOS, Linux) i przeglądarki. Pisanie gry ma przypominać pisanie pluginu serwerowego: klasa główna z cyklem życia, moduły, listenery eventów, zadania w schedulerze i rejestry z kluczami. Sceny i UI buduje się wyłącznie kodem.

> Status: **etap 0 z 12** — szkielet buildu, SPI platformy, okno desktopowe i backend testowy. API nie jest jeszcze stabilne. Plan: [`docs/spec.md`](docs/spec.md), sekcja 22.

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

## Budowanie

Wymagany JDK 25 (Gradle znajdzie go przez `JAVA_HOME` lub w standardowych lokalizacjach).

```bash
./gradlew check
```

```bash
./gradlew :examples:showcase:run
```

## Moduły

| Moduł | Rola |
|---|---|
| `gulp-api` | jedyne pakiety, które importuje kod gry (`dev.gulp.api.*`) |
| `gulp-platform` | interfejsy platformy: grafika, okno, input, audio, pliki, czas, wątki |
| `gulp-core` | implementacja API, działa pod TeaVM |
| `gulp-backend-desktop` | LWJGL 3: GLFW, OpenGL 3.3 core |
| `gulp-backend-web` | TeaVM: WebGL2, WebAudio (etap 3) |
| `gulp-backend-headless` | bez okna i dźwięku, do testów i CI |
| `gulp-processor` | procesor adnotacji (etap 1) |
| `gulp-test` | narzędzia testowe dla gier |
| `gulp-tools` | packer atlasów, generator fontów, importery |
| `gulp-gradle-plugin` | plugin `dev.gulp.game` dla projektów gier |

## Licencja

[Apache 2.0](LICENSE). Nazwa „Gulp” nie ma związku z narzędziem JavaScript gulp.js.
