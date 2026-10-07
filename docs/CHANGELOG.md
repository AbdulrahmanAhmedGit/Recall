# Latest source changes

## October 8, 2026 — calendar, reliability and project documentation

These changes describe the latest source on `main`, not a newly tagged GitHub or
store release. The Android application version remains 1.0.

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
