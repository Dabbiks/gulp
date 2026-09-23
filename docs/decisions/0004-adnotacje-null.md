# 0004. Adnotacje null: `@NullMarked` + `@Nullable`

- Status: przyjęta (potwierdzona przez właściciela 2026-09-23)
- Data: 2026-09-23
- Etap: 0

## Kontekst

Sekcja 1 wymaga „`@Nullable` i `@NotNull` z JSpecify na każdym publicznym parametrze i wartości zwracanej w API”. JSpecify 1.0 nie ma `@NotNull`; odpowiednikiem jest `@NonNull`, a zalecanym sposobem jest oznaczenie pakietu jako `@NullMarked`, co czyni wszystkie typy domyślnie niepustymi.

## Decyzja

Każdy pakiet ma `package-info.java` z `@NullMarked`. Tam, gdzie wartość może być `null`, stoi jawne `@Nullable`. `@NonNull` nie jest pisane, bo w pakiecie `@NullMarked` niczego nie zmienia.

## Konsekwencje

- Ten sam kontrakt co w specyfikacji (każdy publiczny typ ma określoną nullowalność), bez szumu na każdym parametrze.
- Jeśli właściciel chce jawnych adnotacji na każdym parametrze, trzeba dopisać `@NonNull` hurtowo; zmiana jest mechaniczna.
