# Inline pronunciation

Tap an underlined word or phrase in a review, import preview, or card editor to hear only that target. Playback is separate from reveal, ratings, timers, and memory state.

## Authoring

Use **AI prompt** in Import; subject-scoped prompts still pin the existing destination. The prompt generates Recall JSON v2 and marks only useful vocabulary in language lessons. v1 imports remain supported.

Example:

```json
{
  "version": 2,
  "subject": "German",
  "chapter": null,
  "lesson": "Greetings",
  "content_type": "language_learning",
  "learning_language": "de",
  "cards": [{
    "type": "qa",
    "front": "كيف تنطق \"Guten Morgen\" بالألمانية؟",
    "back": "Good morning",
    "pronunciation_targets": [
      {"side": "front", "text": "Guten Morgen", "occurrence": 1},
      {"side": "back", "text": "Good morning", "language": "en", "occurrence": 1}
    ]
  }]
}
```

Targets match exact, case-sensitive substrings. Occurrences are one-based, counted from the beginning of the specified side. Phrases remain a single target. Missing languages inherit the lesson language; a target without a safely determined language is skipped. Invalid, overlapping, duplicate, or unresolved targets are omitted with a preview warning, not by discarding the card.

The editor's collapsed **Pronunciation** section supports adding, editing and removing targets on any card, including academic cards. Changed text must still match its targets before saving. The AI is instructed to select roughly 0–4 targets; validation caps metadata at 32 targets per card and 160 characters per target.

## Playback and storage

- One native Android TTS engine is shared by the app ViewModel. Only installed offline voices are eligible. Explicit regions are respected; an unavailable accent does not silently use another one.
- Missing voices show an unobtrusive message with an Android voice-management action. Recall never downloads voices or calls a speech API.
- Settings → Pronunciation offers Slow, Normal and Fast. Speech stops when leaving a review/import or backgrounding the app; the engine is released with its ViewModel.
- Room v3 adds lesson metadata and an indexed, cascading target table. Migrations 1→2→3 retain existing cards and review state.
- Backup v2 includes targets, lesson language and speech rate. v1 backups remain supported; merging never overwrites existing card annotations.
- Inline links use Compose LinkAnnotation and named accessibility actions. Raw target ranges are mapped through the existing Arabic/Latin isolation layer before rendering.

## Validation

Unit coverage: exact matching, repeated occurrences, phrases, invalid metadata, language inheritance, bidi offset mapping, offline voice selection, import v1/v2 and backup round-trips.

Device coverage: migrations, target persistence/cascade, import/edit/duplicate, real text hit-testing in light/dark/large RTL, editable import preview, review-state isolation, and native engine completion or missing-voice handling.

Run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```
