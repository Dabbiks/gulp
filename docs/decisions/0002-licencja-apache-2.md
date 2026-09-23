# 0002. Licencja Apache 2.0

- Status: przyjęta (propozycja z sekcji 23, potwierdzona przez właściciela 2026-09-23)
- Data: 2026-09-23
- Etap: 0

## Kontekst

Sekcja 23 wymienia licencję jako decyzję otwartą (Apache 2.0 / MIT / GPL) potrzebną przed etapem 0, z propozycją Apache 2.0. Do rozpoczęcia etapu 0 nie zapadła inna decyzja.

## Decyzja

Framework jest na licencji Apache 2.0 (`LICENSE` w katalogu głównym, pełny tekst z apache.org). Gry mogą być zamknięte, a licencja zawiera klauzulę patentową. Zależności muszą mieć licencje zgodne z Apache 2.0 (sekcja 4).

## Konsekwencje

- Zmiana licencji później wymaga zgody wszystkich autorów kodu, więc jeśli właściciel woli MIT, trzeba to zrobić zanim pojawią się zewnętrzni kontrybutorzy.
- `THIRD_PARTY_NOTICES.md` będzie generowany w buildzie w etapie 11.
