# Recall

Offline-first native Android study companion built around subjects, optional
chapters, and lessons. Flashcards help retain lessons over time rather than
acting as the primary organization system.

## Features

- Kotlin, Jetpack Compose, Material 3, Room, DataStore, and WorkManager.
- FSRS-6 review scheduling with rating previews and persisted review history.
- Manual cards, provider-independent AI JSON import, and science notation support.
- Subject resources for notes, PDFs, and photos; portable full-data backups.
- Quiet times, study windows, reminder pauses, and notification diagnostics.
- Review activity heatmap and Skip for now without changing card schedules.
- English/Arabic guide explaining the study workflow and scientific foundations,
  shown once at startup and available again from Settings.
- Light/dark appearance and bidirectional educational text rendering.

## Build

Use Android Studio, JDK 17, and Android SDK 36. Configure your local SDK location
in `local.properties` (not committed).

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

The debug APK is generated under `app/build/outputs/apk/debug/`. Instrumented
tests require a connected emulator or device. The application name is defined
in Android string resources.

## Learning model

Recall supports retrieval practice and spaced practice, with FSRS-6 memory-state
equations. Predictions are estimates, not guarantees of learning outcomes. This
implementation uses default FSRS weights rather than personalized parameter
training; Again includes a product-level 10-minute relearning step.

- [Retrieval practice research](https://pubmed.ncbi.nlm.nih.gov/26151629/)
- [Distributed practice research](https://pubmed.ncbi.nlm.nih.gov/16719566/)
- [FSRS algorithm documentation](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm)
- [Recall scheduler notes](docs/fsrs-scheduler.md)

## Privacy and limitations

Core study data is local and no account is required. External AI providers and
research links are optional and governed by their own policies. Android battery
restrictions and force-stop can delay notifications. Export a full backup to
preserve attached file contents; data-only JSON does not include them.

Build artifacts, machine-local configuration, validation captures, credentials,
and personal backups are excluded from this repository. The chemistry JSON in
`recall-imports/` is an educational regression fixture used by instrumented tests.
Third-party licenses and attribution are documented in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
