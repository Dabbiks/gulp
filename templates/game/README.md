# My Game

Gra na silniku Gulp, utworzona z szablonu.

```bash
./gradlew runDesktop                 # gra w oknie, zasoby przeładowują się same
./gradlew runWeb --continuous        # gra w przeglądarce na localhost:8080
./gradlew test                       # testy bez okna
./gradlew packageDesktop             # instalator dla tego systemu w build/distributions/desktop
./gradlew packageWeb                 # ZIP dla itch.io w build/distributions
```

W grze: F3 pokazuje nakładkę deweloperską, `~` otwiera konsolę (`/help`).

Kod gry używa tylko `dev.gulp.api` — pilnuje tego `./gradlew check`. Zasoby leżą w
`src/main/resources/assets/mygame/` (obrazki w `sprites/`, tłumaczenia w `lang/`); klucze do nich są w
generowanej klasie `GameAssets`.

Dopóki Gulp nie jest opublikowany, projekt buduje się razem z checkoutem Gulp wskazanym w `gradle.properties`
(`gulp.home`).
