# Strength Tracker na Androida

Samodzielna, natywna aplikacja Kotlin/Jetpack Compose. Nie wymaga połączenia z internetem, serwera Flask ani Raspberry Pi. Treningi, ćwiczenia i pomiary są przechowywane w lokalnej bazie SQLite na telefonie.

## Funkcje

- Rejestrowanie treningu z ćwiczeniami i seriami (powtórzenia, kg, notatka)
- Przeglądanie i usuwanie treningów
- Zapisywanie, przeglądanie i usuwanie pomiarów masy ciała
- Działa offline, bez konta i konfiguracji serwera

## Uruchomienie w Android Studio

1. Otwórz folder `app_android` w Android Studio i poczekaj na synchronizację Gradle.
2. Uruchom konfigurację `app` na telefonie lub emulatorze. Aplikacja działa od razu i nie wymaga dodatkowej konfiguracji.

Do kompilacji wymagane jest Android Studio z Android SDK 35 oraz JDK 17 lub nowsze wspierane przez używaną wersję Gradle. Android Studio dostarcza własne JDK. Wybierz je w `Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK` (Embedded JDK), zamiast JDK 25 z logu.

### Gdy Gradle zgłasza `OutOfMemoryError`

Projekt ustawia limit pamięci Gradle na 2 GB i ogranicza kompilację do dwóch workerów. Zamknij Android Studio i uruchom je ponownie, aby zakończyć daemony, które mogły pozostać po błędzie. Następnie wykonaj `Sync Project with Gradle Files` i ponów kompilację. Upewnij się, że komputer ma kilka GB wolnej pamięci.

Dane aplikacji są lokalne i nie synchronizują się automatycznie z wersją webową Strength Tracker.