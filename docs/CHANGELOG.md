# Latest source changes

## v1.0.0-preview.5.1 — October 11, 2026

- Pause subjects and chapters as well as lessons. Parent rules dynamically cover
  new lessons; overlapping rules remain independent. Compatible preference and
  backup handling without a Room migration or FSRS changes.
- Dynamic wallpaper colors now apply to shared selected/interactive/elevated
  surfaces on Android 12+. Long lesson tags wrap into rows instead of narrow
  vertical columns, retaining edit/remove actions and accessible touch targets.

- Shared Compose motion tokens, eased numeric updates and progress interpolation.
- RTL-aware detail/tab navigation; consistent press feedback for shared actions,
  library rows and review ratings; smooth answer and Insights-detail reveals.
- Keyed list placement/insertion/removal in Today, Library, lesson cards,
  calendar cards, resources and import preview; no decorative heatmap animation.
- Live Android Remove animations support, exact accessible numeric targets,
  no dependencies or scheduling/data-contract changes from the motion pass.
- New release/README banner based on native demo captures. See
  [release notes](releases/v1.0.0-preview.5.1.md) and
  [motion design and implementation](motion-design.md). Android version code is 6.

## v1.0.0-preview.5 — October 10, 2026

- Pause individual lessons for today, seven days, or an inclusive custom date
  range. Mixed Today queues, backlog batches and reminders omit active pauses;
  due dates, card memory state and review history are unchanged.
- Focus on a subject, optional chapter or lesson. Due cards use normal FSRS;
  practice includes future cards without writing schedules or review logs.
  Sessions are randomized and bounded to 200 cards; due mode keeps new-card limits.
- Saved pauses persist offline, expire automatically on subsequent checks and
  round-trip in backups. Legacy backups remain compatible; no Room migration.
- Added all five interface translations and enlarged-text Arabic/RTL checks.
- Inter and Cairo typography now ship together in this versioned release.
- New AI-assisted release banner uses synthetic native app captures.

See [release notes](releases/v1.0.0-preview.5.md) and the
[pause/focus guide](review-focus.md). Android version code is 5.

## v1.0.0-preview.4 — October 9, 2026

Includes the Learning Insights, Cairo typography and Phase 3 changes below.
Android version code is 4; optimized non-debuggable preview with unchanged signing.
See [release notes](releases/v1.0.0-preview.4.md).

## Unreleased — Phase 3 reliability and manageable sessions

- Evaluate new ratings coherently at submission time; refresh changed intervals
  before confirmation and use monotonic foreground duration across interruptions.
- Retain session progress/in-flight guards in the ViewModel across recreation;
  reject inactive, edited, deleted and duplicate rating submissions atomically.
- Exclude future responses from Today/activity without delaying new completed logs.
- Validate backup relationships/conflicts before mutation, protect existing state
  and history together, and stream history in JSON/ZIP to avoid a demonstrated
  large-export memory failure. Backup contracts and Room version remain unchanged.
- At 60 due cards, offer optional oldest-due sessions of up to 20, preserve new-card
  limits and every untouched due date, and show real remaining counts.
- Added synthetic 100–10,000 card / 1,000–100,000 history benchmarks and reliability
  regressions. See [Phase 3 results and limitations](phase3-reliability.md).

## Unreleased — Insights placement and Arabic typography

- Moved Study Activity directly below the Insights header, above today's metrics.
- Bundled Cairo Regular/Medium/SemiBold/Bold for the Arabic interface; retained
  other languages' typography and the existing separate science-expression font.
- Review context headers can grow for enlarged text instead of clipping at a
  fixed height.
- Use the optimized `preview` APK for everyday testing. Debug builds deliberately
  include unshrunk code and Compose tooling and are substantially larger; APK size
  is not itself a measurement of runtime performance.

## Unreleased — Learning Insights Phase 1 and Phase 2A

- Replaced reviewed-once “Mature” statistics with mutually exclusive active-card
  categories and a documented 21-day FSRS stability threshold, labelled as an estimate.
- Added event-based, self-reported observed recall for 30 days, explicit eligibility,
  counts, small-sample safeguards and descriptive previous-period comparison.
- Added at most three optional attention lessons based on repeated delayed failures,
  with evidence, recovery handling and lesson links. Scheduling remains unchanged.
- Added all five UI translations, calculation/Room/Compose tests and historical
  limitations.
- Added optional expandable current predicted recall using the existing FSRS-6
  curve, UTC-day elapsed time, equal card weighting and explicit active-card coverage.
  Empty and small groups remain clearly explained in all five interface languages.
- Verified Phase 1 and documented frozen presentation-time review timestamps,
  including midnight effects. Historical reconstruction and calibration remain deferred.
- No database schema, stored memory-state, scheduler, or due-date changes.

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
