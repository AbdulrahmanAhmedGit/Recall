# Phase 3 — reliability and manageable reviews

## Implementation and invariants

### 1. Submission-time scheduling

Previously the UI froze the scheduling instant at question presentation. A question
left open over midnight could be attributed to yesterday; an Again answer after
ten minutes could already be overdue when saved.

`ReviewScreen` refreshes four previews on resume and every minute. `submitReview`
evaluates all four ratings again at the actual tap, using the existing scheduler.
If any interval differs from the displayed intervals, **nothing is written**: the
buttons refresh and the user must choose again. Otherwise the fresh selected result
is committed unchanged. Log `reviewedAt`, state `lastReviewedAt`, exact elapsed audit
and next due all use that same submission instant. A clock earlier than the stored
last review is rejected until corrected. FSRS still uses UTC calendar-day elapsed
time; reporting uses local dates. No historical date or due date is rewritten.

Only foreground duration is accumulated, using `elapsedRealtime`, not a wall-clock
subtraction. There is no need to add presentation/reveal columns to Room.

### 2. Lightweight lifecycle safety

Index, reveal status, rating totals, skips and the in-flight save flag now live
together in the existing ViewModel. Recreation cannot restore an old UI index while
a database-save callback advances a disposed composition. Callbacks verify session
identity before advancing it. Rating/skip controls are disabled during a commit.
Backgrounding pauses duration, but does not rate or skip anything.

Room atomically compares the prior memory state, checks that the card and containers
are still active, checks the presented question/answer/type/hint/source and then
writes state plus log. Deleted, suspended, archived, content-edited and duplicate
submissions fail without writes. Editing and restoring identical content between
checks cannot be detected, but is not a different question at commit time.

Process death deliberately abandons the unfinished session and returns to Today.
Completed ratings remain in Room; no persistent session database or false claim
that unfinished answers were saved. The foreground skip-deferral list is also
ephemeral. Original history recorded at presentation time cannot be repaired
reliably from duration and is left intact.

### 3. Dates and analytics

Existing Phase 1/2A definitions are unchanged. Regression tests cover mutually
exclusive active memory counts, observed delayed-event eligibility and local
windows, UTC prediction boundaries, DST and local calendar buckets. Today and the
heatmap now exclude records later than the current instant, including later today.
Room invalidations and the shared minute/resume ticker refresh those bounded
queries with a fresh timestamp, admitting a newly completed response immediately.
The activity timeline stays chronological LTR within Arabic UI. Historical dates
are interpreted in the currently selected device timezone, not an unrecorded
original timezone. Current predictions remain read-only and are not calibration.

### 4. Backup integrity and scale

Both JSON and ZIP imports validate unique IDs, relationships, chapter ownership,
tag links, finite memory values, ratings and schedule weekdays **before mutation**.
Existing cards retain their existing states **and logs together**: incoming logs
for an ignored existing card no longer silently append a different history to its
unchanged state. Duplicate restores are idempotent. Parent/tag name collisions
under different IDs and conflicting container ownership are rejected, not silently
merged or overwritten. Resolve such conflicts by importing into a separate clean
installation or editing the conflicting library records deliberately.

The first synthetic benchmark exposed an OutOfMemoryError in the old full-history
`JSONObject`/indented-string export at 5,000 cards / 50,000 logs on a 192 MB Android
heap. Runtime JSON export/import and ZIP `data.json` now stream the review-history
array, serializing/parsing one bounded log object at a time. The same v1–v3 JSON
contract and ZIP v1 manifest/checksum contract remain supported. A small reader
isolates history from the metadata envelope; the existing codec validates values.
ZIP extraction stages `data.json` in the fresh private restore directory rather
than simultaneously retaining raw bytes, a giant string and all JSON log objects.
Checksums, traversal protection, byte limits and failed-restore cleanup remain.
JSON export stages before copying to the selected destination.

Room merge is transactional. Room and DataStore cannot be one transaction: if
preferences/reminder refresh fail after the Room commit, the existing localized
partial-restore warning is shown. Already committed records and referenced files
are retained. ZIP merge and recording committed-file ownership form a small
non-cancellable critical section, so cancellation cannot delete attachments that
Room has just committed. Cancellation is checked before entering that section.
A failed destination-provider copy can leave a partial **export file**;
it never mutates the source library. The old 50 MB metadata, 512 MB attachment and
2 GB archive safety ceilings remain; they are transport limits, not card quotas.
Metadata and Room entity lists are still materialized, so arbitrarily large
libraries/notes are not guaranteed to fit all device heaps.

### 5. Measurements and targeted performance

`LargeLibraryBenchmarkTest` uses fresh private disk databases, synthetic text, ten
logs per card, Android 14 Small_Phone emulator and debug builds. Database-path
figures below are warmed medians of five iterations. Full ZIP figures are single
round trips. These are not production-device frame-time or startup claims.

| Cards / logs | Existing full due query | Bounded 20 | Lesson list | Subject list | Heatmap | Calendar | 20 FSRS previews | ZIP write / read |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 / 1,000 | 7.27 ms | 3.05 ms | 3.03 ms | 2.97 ms | 3.57 ms | 6.46 ms | 4.25 ms | 1.04 / 1.07 s |
| 1,000 / 10,000 | 46.18 ms | 3.52 ms | 3.83 ms | 4.58 ms | 6.84 ms | 5.83 ms | 3.61 ms | 4.27 / 2.64 s |
| 5,000 / 50,000 | 75.43 ms | 8.92 ms | 7.23 ms | 10.69 ms | 23.04 ms | 11.30 ms | 2.44 ms | 4.98 / 5.61 s |
| 10,000 / 100,000 | 146.71 ms | 15.76 ms | 12.10 ms | 19.94 ms | 39.62 ms | 18.91 ms | 2.14 ms | 6.34 / 9.34 s |

These queue comparisons execute the unchanged unbounded path and the new bounded
path against the same database within the same run. Separate pre-change runs
measured 117.28/498.98 ms for history at 1,000/10,000 logs; after hoisting repeated
local-midnight calculations out of the event loop, comparable runs measured
73.02/287.65 ms. This comparison includes run/JIT noise and is not a guaranteed
percentage improvement. At 100,000 logs, history query plus analysis still takes
about 2.89 s on a background dispatcher; Room backup materialization 1.14 s.
The stress heap snapshot was about 102 MB (not a peak-memory measurement).

The final full-suite repeat is preserved in [raw measurement CSV](benchmarks/phase3-emulator.csv):
10k-card queue **142.84 ms vs 16.73 ms**, ZIP **6.39 / 9.32 s**, history **2.98 s**.
Small-dataset timings varied more across emulator runs; do not interpret a single
run as a guaranteed speedup or use these measurements as release-device benchmarks.

No new chart/query infrastructure or dependency was introduced. Small sessions
bound card-content allocations to 20 instead of loading thousands. Full due,
subject and lesson reviews intentionally retain their existing unbounded options.
Analytics work remains outside Compose; unchanged history/predictions are cached.
Further incremental history aggregation and paging need measurement on actual
devices before adding complexity. Cold-start, sustained scrolling frame times,
long scientific-answer libraries and physical-device ANR profiling are pending.

### 6. Smart backlog behavior

- At **60 or more currently due active cards**, Today offers **Review up to 20
  cards**, plus the optional **Review all due cards** action. Sixty is three small
  sessions, not a daily target. New/unreviewed due cards count toward this truthful
  waiting total; suspended/archived cards do not.
- Selection uses two bounded SQL candidate sets: started cards and new cards,
  sorted by **oldest due timestamp, then card ID**. It returns at most 20 total
  and no more new cards than the existing per-session `newCardLimit`. It does not
  invent a retrievability score or another scheduling algorithm.
- Completing a rating moves that card according to FSRS, so the next oldest cards
  get a turn. Up to 20 recently skipped IDs are deferred for the next small session
  within this process; if no candidates remain, they become available again.
  Deliberately repeated skipping or refusing new cards can leave them due; this
  is user choice, not a reason to reschedule them. Process death resets deferral.
- Unselected and skipped cards receive **no** state/log changes. New-card limits
  remain independent per-session preferences, not daily quotas. Existing subject,
  lesson and all-due flows remain available. The default batch size is deliberately
  fixed at 20, with no additional settings or goal UI.
- Completion shows answered/skipped counts and a fresh real remaining-due count.
  Done returns to Today; another session is always voluntary, never automatic.
  An empty session limited by preferences is not described as clearing the backlog.
- Oldest-due selection can bias observed outcomes toward overdue information.
  Observed recall is still a self-reported event sample, not whole-library retention
  or FSRS calibration. Attention ranking and predictions do not influence selection.
- New wording is localized in English, Arabic, Spanish, French and German; existing
  accessible controls, Cairo Arabic typography and science rendering are reused.

## Significant files

- `domain/ReviewSession.kt`: session progress, monotonic timer, backlog policy,
  displayed-interval equality check.
- `RecallViewModel.kt`: guarded fresh submission, lifecycle progress, bounded batches,
  live date-bounded activity, streaming file workflows.
- `data/RecallDatabase.kt`: atomic active/content checks, bounded queue query,
  transactional conflict-safe restore, date-bounded counts.
- `data/StudyActivityQuery.kt`: future-event upper bound.
- `domain/LearningInsights.kt`: constant reporting boundaries hoisted from event loop.
- `domain/BackupValidation.kt`, `BackupEnvelopeReader.kt`, `RecallBackup.kt`,
  `FullBackupArchive.kt`: validated graph and streaming compatible history.
- `ui/DashboardScreens.kt`, `ui/ReviewImportSettings.kt`: optional short-session
  actions, lifecycle preview refresh, accurate completion, streaming backup UI.
- `res/values*/phase3.xml`: all five language catalogs.
- `ReviewReliabilityTest`, `BackupValidationTest`, `Phase3PersistenceTest`,
  `BacklogUiTest`, `LargeLibraryBenchmarkTest`, `StudyActivityPersistenceTest`:
  focused regression/measurement coverage. Existing tests were preserved.

## Automated verification — October 8, 2026

- 87 JVM unit tests passed, including unchanged FSRS outputs, interval confirmation,
  duration/background boundaries, backup graph/streaming compatibility, existing
  insights, timezone/calendar, science and all-five-language catalogs.
- Final full Android suite: 55/55 passed on the Android 14 Small_Phone emulator. This
  includes Room migration/restart, ZIP bytes/checksums, review/skip/recreation,
  activity/calendar, pronunciation, imports, notification and localization tests.
  The added JSON file-picker workflow round trip preserves state, history and notes
  and remains idempotent on a second restore.
- Subsequent UI/date follow-up: 18/18 passed. Final active-card/backup-conflict
  follow-up: 5/5 passed, including new log-ID conflict rollback and bounded queries
  at 0/1/10/20/40 candidates. Test samples are synthetic, never personal data.
  After the cancellation/attachment ownership safeguard, all six targeted Room,
  ZIP and JSON file-workflow tests passed again. The exact cancellation race and
  forced OS process death still require the manual checks, not a claim of device
  validation from these ordinary round trips.
- Optimized preview build and debug compilation passed; final lint reported
  **0 errors / 33 warnings** (existing SDK/dependency/style recommendations).
  Gradle also reports the existing experimental Kotlin source-set option. An
  earlier lint quick-fix reporter emitted a source-offset exception while files
  were being edited; a stable-source final rerun completed without that exception.
- The initial run had two date-fixture failures (a Cairo example did not actually
  cross midnight, and a future-dated DST fixture needed an explicit simulated
  clock) and the genuine old backup OOM described above. These were corrected;
  later runs passed. No failed test is being counted as passing.
- Physical device, forced OS process-death, real rotation/background/lock sequences
  and low-end-device startup/frame profiling remain on the manual checklist.
- No previews or mock heatmap values were substituted for persisted review logs.

### Manual device checklist (not yet performed)

1. Export a ZIP and JSON from a synthetic library with a PDF, photo, notes, tags,
   pronunciation, schedules and completed reviews. Restore to a separate test
   installation; compare every record, state/log, settings and attachment bytes.
2. Present before midnight, submit after midnight; inspect the completion day and
   next due. Leave a review idle, background/lock/resume and change timezone/clock;
   verify fresh intervals, correct duration and explicit reconfirmation if changed.
3. Rotate before reveal, after reveal and during a save. Force-stop/reopen after
   one completion: completed history must remain, unfinished session must be gone.
4. At 250 due, start 20, skip, finish or exit, then start another session. Check
   untouched due dates, real remaining counts, optional all-due/lesson review and
   no automatic second session. Repeat at new-card preference zero.
5. Repeat in Arabic/RTL, light/dark, 320 dp width and large font. Profile startup,
   scrolling and rating on a low-memory phone with 10k cards/100k logs.

Never clear, uninstall or corrupt a personal library for these tests. Use synthetic
test installations and retain independent backups.

## Built artifact

Optimized `app/build/outputs/apk/preview/app-preview.apk`: **3,433,585 bytes**
(about 3.4 MB), version `1.0.0-preview.3`, same application ID/signing configuration.
SHA-256: `d6d3305db71746b177329694bd91dc2aeb07e76061e44602c4b4bc83fd5fbdc0`.
This is the small non-debuggable preview variant, not the unshrunk debug APK.

## Compatibility guarantees

Room remains version **4**; no migration. FSRS equations/default weights and
rating policies are unchanged. The scheduling **anchor for newly submitted UI
ratings** is intentionally corrected from presentation to submission. Existing
states/logs/due dates are unchanged. Backup schemas are unchanged; runtime JSON is
compact rather than indented, and validation now rejects malformed/conflicting
graphs conservatively. No new dependencies, cloud, calibration, training or
analytics-driven scheduling were added.
