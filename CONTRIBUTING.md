# Contributing to Recall

Thank you for helping make long-term study calmer, more reliable and accessible.
Read the [feature guide](docs/features.md) and [code of conduct](CODE_OF_CONDUCT.md)
before contributing. English and Arabic reports are welcome.

## Ask, report or propose

- Search [existing issues](https://github.com/AbdulrahmanAhmedGit/Recall/issues)
  before reporting a bug or requesting a feature. Use the issue forms so problems
  can be reproduced without guessing the device, version or review behavior.
- Use [Discussions](https://github.com/AbdulrahmanAhmedGit/Recall/discussions)
  for setup questions and ideas that are not yet a concrete change.
- Discuss broad product, schema or scheduling changes before implementing them.
- Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md),
  not through public issues, pull requests or discussions.

Never upload personal study backups, copyrighted textbook pages, private notes,
credentials, signing keys or unredacted device logs. Use small synthetic fixtures
and redact screenshots. Confirm you have permission to share contributed content.

## Local setup

1. Fork the repository and create a focused branch from `main`.
2. Open it in Android Studio with JDK 17 and Android SDK 36.
3. Set your own SDK path in the ignored `local.properties` file.
4. Build and run a debug installation on an emulator or test device.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
git diff --check
```

Device tests need a connected emulator/device; notification tests may need
notification permission granted to the test installation. Use a disposable test
environment, not your only copy of study data. `assemblePreview` builds the optimized
preview; it is debug-signed and is not a production signing setup.

## Implementation expectations

- Keep the lesson-first hierarchy: Subject → optional Chapter → Lesson → Cards.
  Preserve offline operation and avoid unnecessary libraries, accounts or services.
- Follow the existing Kotlin/Compose style and reuse design tokens/components.
  Put IO and expensive parsing outside the UI thread.
- Keep scheduler calculations independent of UI and notification policy. Preserve
  atomic review-state/history saves and exact rating previews.
- Any Room schema change requires a data-preserving migration and tests. Never
  solve a migration problem with a destructive reset.
- Preserve JSON import/backups, science markers, pronunciation targets and older
  supported formats. Reject unsafe input without losing existing user data.
- Check English, Arabic/RTL, mixed formulas, light/dark, small widths, large fonts
  and accessibility when changing UI. No real user data or permanent runtime demo data.
- Add focused regression tests for changed behavior. Update affected feature or
  technical documentation. For documentation-only changes, check links and whitespace.

The current architecture uses Room/DataStore, a ViewModel with reactive flows,
independent domain utilities, WorkManager reminders and native Compose screens.
Start with the existing locations rather than introducing a new framework or layer.

## AI-assisted contributions

AI-assisted code is welcome. State which tools were used and what you checked in
the pull request. Contributors remain responsible for correctness, permission to
contribute, data safety and dependency licenses. Review generated code and run
relevant checks; never send secrets or private study materials to an AI provider.
Recall's Codex development credit does not replace human review or verification.

## Pull requests

Keep each PR focused. Explain the problem, solution, tests actually run and any
unverified behavior. Include synthetic before/after screenshots for visual changes,
and explicit migration/compatibility notes when relevant. Do not commit build
outputs, local configuration, keys or backups. Use a short descriptive commit title.

Maintainer review is best-effort; a submitted PR is not a promise of acceptance or
a deadline. Recall is distributed under the [MIT License](LICENSE). Contributions
to Recall are provided under the same license; contribute only material you have
permission to share. Third-party components retain the terms in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and their own notices.
