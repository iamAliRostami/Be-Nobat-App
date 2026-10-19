# Be Nobat

[فارسی](README.fa.md) · **English** · [العربية](README.ar.md)

Be Nobat is an Android appointment-booking application built with Kotlin. It currently includes a
PocketBase authentication flow that accepts an email address or Iranian mobile number.

## Highlights

- Clean separation between presentation, domain, repository, and remote data layers
- Dependency injection with Koin
- Networking with Ktor and Kotlin Serialization
- Secure local token storage
- English, Persian, and Arabic resources with automatic LTR/RTL layout direction
- In-app language selector on the login screen
- Refined, keyboard-friendly Material login experience with a custom BN monogram and launcher icon
- Safe, debug-only API request logging

## Development API

The debug build connects to PocketBase at `http://10.0.2.2:8090`. The authentication path is
`/api/collections/users/auth-with-password`. Replace the placeholder release URL in
`app/build.gradle.kts` before publishing.

## Build

```bash
./gradlew test
./gradlew assembleUserDebug
```

The project requires the Gradle version declared by `gradle/wrapper/gradle-wrapper.properties` and
an Android SDK compatible with the configured compile SDK.

## Language selection

Open the login screen and tap the language button at the top. Select English, فارسی, or العربية.
The selected application locale is managed by AppCompat and remains active when activities are
recreated. Persian is selected automatically on first launch; an existing user selection is never
overwritten.

## Code documentation

The bilingual [code documentation](docs/CODE_DOCUMENTATION.md) explains every production class and
explicit function in English and Persian. It must be updated in the same commit as future code
changes. For focused network and logging notes, see [docs/README.md](docs/README.md).
