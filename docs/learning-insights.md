# Learning Insights — Phase 1 and Phase 2A

Recall reports memory estimates, self-reported review success, and a few optional
attention suggestions. It does not measure comprehension, examination performance,
or the fraction of the entire library that a learner can remember.

These calculations read existing Room records. They never write review states,
create review obligations, change due dates, or change FSRS. Room remains version 4.

## Memory categories

The population is currently active cards: `suspended = false`, lesson not archived,
and subject not archived. The following categories are mutually exclusive:

| Category | Rule |
| --- | --- |
| New | `reps <= 0`; a missing review-state row also counts conservatively as New |
| Mature — estimated | `reps > 0`, state `review`, finite stability ≥ 21 days |
| Learning | Every other card with `reps > 0`, including developing memory and relearning |

New + Learning + Mature equals the active-card count. Non-finite stability does not
qualify as mature. A first Again response puts a card in Learning, never Mature.

“Longer-lasting memory” shows Mature / active cards. This is an estimated memory
horizon, not a mastery score. FSRS defines stability as the time until predicted
recall reaches 90%. The 21-day threshold is an operational reporting convention,
not a scientifically established boundary of comprehension. Stability rather than
scheduled interval avoids changing the category just because desired retention
changes. Cards currently relearning are excluded from Mature even if their stored
stability is high. See the [FSRS specification](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm)
and [the documented interval-based 21-day convention](https://github.com/ankitects/anki-manual/blob/main/src/stats.md).

The lesson History tab's former “Learned cards” label is now “Reviewed at least
once.” That existing count describes submitted responses, not mastery; the lesson
overview still represents the lesson's entire card collection, including suspended
cards. Active-only filtering applies to the new quality and memory insights.

## Observed recall

The main label is “Observed recall — Last 30 days — event-based, self-reported
success.” The population is eligible **events**, not cards or library knowledge.

An event qualifies only if all of the following hold:

- Its card, lesson, and subject currently exist and are active.
- Rating is 1, 2, 3, or 4.
- The previous state was `review`, and post-response repetitions are at least 2.
- The previous due date is positive and the review timestamp is at or after it.
- The previous scheduled interval was at least one day.
- Exact `elapsedDays` is finite and at least 1.0 (24 hours).
- The timestamp is in the observation window and is not in the future.

Only the earliest eligible event per card per local calendar day counts. Equal
timestamps are resolved by log ID. A card can count again on another day. Again is
unsuccessful recall; Hard, Good and Easy are self-reported successful recall.

`observed recall = successful eligible events / all eligible events`

First learning, relearning, short retries and early practice are excluded. A
successful ten-minute retry never cancels the original delayed failure in this
metric. Skipping or merely opening a card creates no response and adds no evidence.

The current window begins at local midnight 29 days before today and ends at the
current time. The previous window is the preceding 30 local days. Use the current
device timezone and historical DST rules for calendar dates; eligibility still
requires a full 24 elapsed hours, even on a 23-hour DST day. Original event timezones
are not stored, so travel/timezone changes can shift the reporting date of an event.

Always show successful events, total eligible events and distinct cards. Display a
percentage only with at least **30 eligible events across 10 distinct cards**. These
are conservative display safeguards, not a statistical guarantee. Smaller or empty
samples show counts and a lightweight explanation; there is no quota, warning, or
extra study task. A percentage does not mean that proportion of the library is
remembered. Ratings are subjective and multiple observations of one card can be
correlated.

Show the percentage-point difference from the previous window only if **both**
windows pass both safeguards. The comparison is descriptive and is not labelled a
statistically significant improvement, deterioration, or comprehension change.

## Optional attention

Look at each active card's last five eligible, day-deduplicated events within the
last 90 local days. Flag repeated recall difficulty only with at least three events
and at least two Again responses. Two successful most-recent eligible events clear
the flag. A new card or a card with only short-term failures is not penalized.

Rank flagged cards by failed-event proportion, then failure count, then most recent
failure, with card ID as a stable final tie-break. Group by lesson in that order and
show at most three lessons. Each lesson shows its flagged-card count, the strongest
example question, and the specific evidence. Open the existing lesson when tapped.

Expandable History gives current difficulty, lifetime lapses, stability, scheduled
interval and due status. These fields provide context; they are not added into an
arbitrary score. Due/overdue status does not rank recall difficulty. Difficulty
alone is insufficient evidence. Suggestions do not create tasks or change schedules.

## Current predicted recall — Phase 2A

An optional, initially collapsed **Current predicted recall · FSRS** section appears
below memory maturity and its explanation. It is not a primary dashboard tile.
Expanding it shows an estimated percentage, coverage, and a brief scope explanation.

Use the same active-card population as memory categories for the coverage denominator.
A card is eligible for prediction only with state exactly `review`, `reps > 0`,
finite positive stability, and a non-null last-review timestamp greater than zero
and no later than the current clock. New, learning, relearning, unknown or missing
states and invalid/future timestamps are excluded. Suspended cards and archived
lessons or subjects are excluded from both numerator and denominator. Missing or
invalid memory is never treated as zero recall and is never filled in from history.

Here “learning” refers to the FSRS state, not the broader Phase 1 maturity category.
A valid `review`-state card below 21-day stability is eligible for prediction even
though its maturity category is Learning. The 21-day cutoff is not a prediction gate.

For each eligible card, reuse `Fsrs6MemoryModel.forgettingCurve` directly:

```
elapsedDays = floor(nowMillis / 86400000) − floor(lastReviewedAt / 86400000)
decay = −w[20] = −0.1542
factor = 0.9^(1 / decay) − 1
R = (1 + factor × elapsedDays / stability)^decay
current predicted recall = sum(R) / eligibleCards
coverage = eligibleCards / activeCards
```

This is the scheduler's UTC epoch-day convention, not local calendar days or a
fractional elapsed duration. The existing model bounds stability to 0.1–36,500 days
inside its curve; analytics uses those exact bounds too. Eligible cards are weighted
equally, not by review count, lesson size or difficulty. The display rounds the mean
to a localized whole percentage; calculations retain full floating-point precision.
The curve returns 1 for zero elapsed UTC days, which is a model output, not a promise
of perfect human recall. It uses existing default FSRS-6 weights, not trained weights.

With no eligible cards, show no percentage, a helpful explanation and `0 / active`
coverage. A valid one-card estimate can be displayed: this is a model calculation,
not an empirical sample, so the observed-recall 30-event/10-card gate does not apply.
For 1–9 eligible cards, explicitly mention that it covers a small group. Ten is a
lightweight explanatory cutoff, not a confidence guarantee or study target. Coverage
is always shown, even with larger groups; the mean never describes missing cards.

Predicted recall describes **current eligible cards**; observed recall describes
**eligible past events over 30 local days**. Neither is comprehension or mastery.
Never subtract the two percentages, call the difference prediction error, or use
it as calibration. The estimate does not prescribe extra study or affect scheduling.

`RecallDao.insightsPredictionCards()` fetches one small active-state projection,
including missing states for coverage, not card content or review history.
`CurrentRecallPredictor` caches results for unchanged rows within the same UTC day;
clock rollback, relevant Room invalidations and future timestamps becoming valid
invalidate it. A shared background flow in `RecallViewModel` uses the existing
minute ticker and resume refresh. UTC transitions are reflected on the next tick
(up to roughly a minute while visible). Changing device timezone refreshes local
history periods but cannot change the UTC-based prediction for the same instant.
No per-card prediction work happens in Compose, and no new persistent data is stored.

## Review timestamps: Phase 3 correction and historical limitation

Older builds froze `reviewedAt` at question presentation. Phase 3 now evaluates the
rating at submission time, refreshing on resume/minute ticks and requiring renewed
confirmation if displayed intervals changed. History, memory state and due date
share that fresh timestamp. Foreground duration uses a monotonic clock, excluding
background time. Existing records are not rewritten; no migration was introduced.

In older builds, for a question presented at 23:59 and rated at 00:01, the response was written only
after rating, but its history date is the preceding local day. This affects activity
dates, first-eligible-event/day deduplication and observed-recall window membership.
At a window boundary, such an event leaves the current window one day earlier than
a submission-time event would. Crossing UTC midnight also leaves the preview's
elapsed UTC-day input unchanged. A due date or 24-hour eligibility threshold crossed
while the question is open does not retroactively make that frozen event eligible.
Current predicted recall correctly uses the stored last-review time under the same
UTC convention, including this presentation-time behavior.

A sufficiently long idle question could also make the selected Again ten-minute due
time already past when submitted. This is fixed in Phase 3 by coherent submission-time
scheduling, not by changing analytics or moving the log timestamp alone. Existing history remains
as recorded; `durationMillis` is not a trustworthy replacement tap timestamp across
recreation and must not be used to silently rewrite historical dates.

## Historical limitations and data flow

Migration 3→4 and old backup imports default missing fields such as previous due
date, previous state, repetitions and elapsed days. These defaults are not real
historical measurements. Records without trustworthy eligibility fields are
excluded, not inferred from current states. Expandable explanations include the
excluded-response count and the subset missing reliable historical fields.

Deleting a card, lesson or subject currently deletes its logs. Orphaned logs are
not joined into current knowledge. Suspending or archiving content removes it from
these quality metrics, so samples can change when the active population changes.
The activity heatmap and today's response count intentionally retain their
separate meaning: all retained completed events, including retries and history for
currently suspended or archived content.

`RecallDao` provides active memory aggregates, one timestamp-indexed 90-day range
query of small log projections, and a batched metadata query for at most three
example cards. `analyzeRecallHistory` performs calculations independently of the
scheduler, off the main thread. `RecallViewModel.learningInsights` exposes a shared,
lifecycle-aware StateFlow. Unchanged history is cached across clock ticks; Room
invalidations refresh real changes, and date/timezone changes refresh the period.
No history aggregation occurs inside Compose. Minute ticks update due context and
admit previously future-dated records only once their timestamp is reached.

English, Arabic, Spanish, French and German resources cover all labels, counts,
explanations and empty states. Arabic uses existing bidi-aware text and RTL layout.

## Verification

- Unit tests: category boundaries, eligibility, safeguards, same-card/day behavior,
  period comparisons, midnight/DST/year boundaries, future records, suggestions and recovery;
  prediction parity, UTC boundaries, timezone changes, stability bounds, invalid
  states/timestamps, equal weighting, coverage, cache invalidation and unchanged previews.
- Room tests: active filters, real log projections, legacy defaults, orphaned/deleted
  history, query index use, prediction projections and unchanged stored memory states;
  real answer/suspension StateFlow updates and legacy lower-level frozen-result
  persistence. Phase 3 adds coherent submission-time UI scheduling tests; it does
  not reinterpret those older committed records.
- Compose tests/previews: available counts, hidden insufficient percentages,
  explanations, lesson action, collapsed predictions, empty/small coverage, all five
  languages, light/dark and large RTL fonts.
- Run unit tests, debug/app-test builds, lint and targeted device tests. Check the
  tracked schema and scheduler diff remain unchanged.

### Verification run — October 8, 2026

- Before Phase 2A edits: Phase 1 unit suite passed; six targeted Phase 1
  persistence/UI and scheduler emulator tests passed.
- Final debug build and all 79 unit tests passed, including seven prediction tests
  and ten Phase 1 analytics tests.
- All 17 targeted Android 14 emulator tests passed: Learning Insights persistence
  and UI, scheduler persistence, activity persistence/UI and localization UI.
- Lint passed with zero errors and 33 existing unrelated warnings. `git diff --check`
  passed; scheduler, tracked Room schemas and dependency configuration have no diff.
- Expanded light and dark Arabic prediction screenshots were visually inspected;
  Compose tests exercise 320dp width, Arabic RTL at 1.5 font scale, all five languages,
  empty/small populations, live coverage changes, and expand/collapse interactions.
  These are test-fixture screenshots, not fabricated production history.

Reproduce the main verification with:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication4.LearningInsightsPersistenceTest,com.example.myapplication4.LearningInsightsUiTest,com.example.myapplication4.SchedulerPersistenceTest,com.example.myapplication4.StudyActivityPersistenceTest,com.example.myapplication4.StudyActivityUiTest,com.example.myapplication4.LocalizationUiTest \
  --offline
```

Only current predicted recall (Option A) is implemented. Historical prediction
reconstruction, prospective calibration logging, training and calibration dashboards
remain deferred. See the [Phase 2 proposal](learning-insights-phase2-proposal.md).

## Implementation files

### Phase 2A changes specifically

- `app/src/main/java/com/example/myapplication4/domain/LearningInsights.kt`:
  current prediction projection/result and cached, read-only predictor.
- `app/src/main/java/com/example/myapplication4/data/RecallDatabase.kt`:
  active-card prediction query only; no entities or migration changes.
- `app/src/main/java/com/example/myapplication4/RecallViewModel.kt`:
  shared prediction flow and integration into the existing Insights StateFlow.
- In `app/src/main/java/com/example/myapplication4/ui/`: `DashboardScreens.kt`,
  `components/LearningInsightsComponents.kt`, `LearningInsightsPreviews.kt`.
- `app/src/main/res/values{,-ar,-es,-fr,-de}/insights.xml`:
  five matching sets of prediction labels, coverage and scope explanations.
- In `app/src/test/java/com/example/myapplication4/`: `PredictedRecallTest.kt`,
  `LearningInsightsTest.kt` (midnight regression).
- In `app/src/androidTest/java/com/example/myapplication4/`:
  `LearningInsightsPersistenceTest.kt`, `LearningInsightsUiTest.kt`.
- `README.md`, `docs/features.md`, `docs/CHANGELOG.md`,
  `docs/learning-insights.md`, `docs/learning-insights-phase2-proposal.md`.

### Combined Phase 1 / Phase 2A implementation

Paths below are relative to the repository root.

- Calculations and projections: `app/src/main/java/com/example/myapplication4/domain/LearningInsights.kt`;
  Room queries in `data/RecallDatabase.kt`; shared flows in `RecallViewModel.kt`
  under the same main package.
- UI under that main package: `ui/DashboardScreens.kt`,
  `ui/components/LearningInsightsComponents.kt`, `ui/LearningInsightsPreviews.kt`,
  `ui/RecallRoot.kt` and `ui/DetailScreens.kt`.
- Resources: new `insights.xml` and removal of obsolete Insights labels from
  `ui.xml` in each of `app/src/main/res/values`, `values-ar`, `values-es`,
  `values-fr` and `values-de`.
- Tests under `app/src/test/java/com/example/myapplication4`:
  `LearningInsightsTest.kt`, `PredictedRecallTest.kt`. Under `app/src/androidTest/java/com/example/myapplication4`:
  `LearningInsightsPersistenceTest.kt`, `LearningInsightsUiTest.kt`,
  `StudyActivityUiTest.kt` and `LocalizationUiTest.kt`.
- Documentation: `README.md`, `docs/features.md`, `docs/CHANGELOG.md`,
  `docs/learning-insights.md` and `docs/learning-insights-phase2-proposal.md`.
