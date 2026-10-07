# Latest source changes

## v1.0.0-preview.3 — October 8, 2026

- Complete offline interfaces in English, Arabic, Spanish, French and German:
  navigation, review ratings/intervals, editors, resources, imports/validation,
  settings, schedules, calendar/activity, guide, backup status and notifications.
- Locale-aware dates, numbers and plural categories; appropriate Arabic RTL.
- Immediate persistent language switching, with the study/pronunciation language
  remaining independent of the interface. Backups retain all five language choices.
- Preserve Activity owners when scoping resources, keeping permission requests
  and document pickers functional; keep Android settings/file-viewer launches
  on the real Activity. Settings choices open fully and scroll for
  longer translations and larger text.
- Reserve content space for the floating dock to keep settings rows tappable;
  keep the Library quick-add action visible on small screens.
- Android version code 3, optimized non-debuggable preview, same signing certificate.
  No database migration, FSRS change, content translation or data reset.

See [localization](localization.md) and [release notes](releases/v1.0.0-preview.3.md).

## v1.0.0-preview.2 — October 8, 2026

Second public preview, including the calendar, reliability improvements and feature
documentation described below. Android version code increases to 2. The downloadable
APK uses the optimized, non-debuggable `preview` variant and the same local debug
signing certificate as the first preview. It is not a store-signed production release.

Export a full backup before updating. If Android reports a signature mismatch,
do not uninstall without first preserving your data. No Room migration or study-data
reset is required for this release. See the
[release notes](releases/v1.0.0-preview.2.md) for installation and verification details.

## October 8, 2026 — calendar, reliability and project documentation

These changes are included in `v1.0.0-preview.2` and the latest source on `main`.

### Review calendar

- Added calendar access from Today and Insights, future month navigation, daily
  counts and Q&A/Cloze/new/overdue breakdowns.
- Added lesson-grouped card listings, read-only question/answer details and lesson links.
- Collected overdue cards under Today; excluded suspended/archived content.
- Used local-calendar boundaries across midnight, timezone changes and DST.
- Bounded day-detail loading to batches of 100 cards.

### Reliability and responsiveness

- Moved JSON preview parsing and backup/file IO away from the main thread, with
  bounded import reads and visible processing states.
- Filtered subject queues in SQL and made review loading single-flight.
- Waited for atomic review persistence before advancing; rejected stale submissions.
- Reduced hidden dashboard work and simultaneous full-screen transitions, while
  keeping short navigation/dock/reveal motion and main-tab scroll restoration.
- Cached science/pronunciation annotations and fixed delayed pronunciation links.
- Opened the lesson when tapping an Insights attention row.
- Coalesced reminder refreshes after ratings and consistently excluded archived parents.
- Added an optimized, non-debuggable, locally debug-signed `preview` build.

No FSRS equation, AI-generation contract, database version or schema change was
needed for this pass. Submitted review history remains durable. Restoring an
unfinished session after process death is not implemented.

### Documentation

- Credited 100% AI implementation using OpenAI Codex under the project owner's
  direction and supervision, separately from third-party authorship/licenses.
- Added a [feature-by-feature guide](features.md) with native screenshot examples
  and explicit data, notification and statistics limitations.
- Added a [screenshot inventory](screenshots/README.md) identifying synthetic
  content and capture scope; no personal backup or study history is included.

### Verification

The implementation pass passed debug/test APK builds, optimized preview build,
59 JVM tests, 29 targeted emulator regression tests, a 9-test review/navigation/
pronunciation repeat run, and 5 persistence tests after the final archived-query
adjustment. Lint reported no errors and 33 pre-existing warnings.

The optimized preview was separately installed as an update on an API 34 emulator
and smoke-tested through Today, Library, Insights, Settings and Calendar. Device
instrumentation uses the debug build. This is not a physical-phone frame-time
benchmark or a claim that every manufacturer-specific background restriction was tested.
