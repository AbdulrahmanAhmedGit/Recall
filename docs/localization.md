# Offline interface languages

Recall includes English, Arabic, Spanish, French and German. Select one in
Settings → Appearance & language → Language, or choose System. Unsupported system
languages fall back to English. Translations are ordinary Android resources,
bundled together in every APK; no download, cloud service or new database is needed.

`util/RecallLocale.kt` scopes the UI context/configuration and formats local dates,
times, weekdays, numbers, rating labels and intervals. `MainActivity.kt` applies it
without resetting navigation or study state. Locale changes update Compose resources
and appropriate RTL layout together. Device region is retained for date/week rules.
The background reminder worker reads the same persisted language before posting.

`res/values*/ui.xml`, `counts.xml` and `system.xml` cover the application UI and
diagnostics. Existing calendar, activity, pronunciation and guide resources are
also translated. Arabic plurals include zero, one, two, few, many and other forms.
`util/SystemMessages.kt` maps legacy English import/policy diagnostics for display,
without changing parser validation, stored cards or scheduling equations.

## Content boundaries

- Study text, tags, resource filenames and notes keep their exact original language.
- Chemical/math markers and their left-to-right expressions are unchanged.
- Pronunciation language comes from lesson/target metadata, independently of UI.
- AI prompts, JSON field names and format identifiers remain stable protocol text.
- Android's system settings, file picker and installed voice controls belong to
  the OS and use its language; Recall does not translate these external screens.
- Local date formatting does not change due timestamps, timezone or FSRS behavior.
- Selecting a language is an in-app preference; the app does not add a separate
  Android per-app-language setting with a conflicting source of truth.

## Validation

`LocalizationCatalogTest` checks complete resource/type/format coverage in all
five languages, Arabic plural categories, regional/system locale resolution and
backup language round-trips. `LocalizationUiTest` exercises the actual scoped UI,
switching in Settings, external settings launch, navigation, mixed-text review, editing and notification
copy and singular/plural Today summaries, with synthetic in-memory study data. Captures include Arabic RTL dark mode
at 1.3× font scale and each other language. Regression tests retain the existing
review, reminder, onboarding and navigation behavior.

To add another language, translate **every** resource (including plurals, arrays,
guide text and diagnostics), add its code/autonym to `RecallLocale.languages` and
the language picker, extend backup validation, and run the coverage/device tests.
Do not translate persisted IDs, user-authored content or AI JSON field names.
