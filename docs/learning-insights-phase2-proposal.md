# Predicted recall and calibration — Phase 2 decisions

Status: **Option A implemented; Options B and C deferred**. No prediction logging,
migrations, calibration charts, model training, or scheduling changes were added.
See [current metric definitions and timestamp audit](learning-insights.md).

## Recommendation

The read-only current-prediction feature is implemented first. Defer claims about historical
calibration until model provenance and prediction timing can be recorded reliably.
The existing history supports useful observed recall; it does not automatically
prove how accurate the historical scheduler predictions were.

## Option A: current predicted recall, without migration

Implemented an independent analytics-facing predictor that uses the existing FSRS-6 curve:

`R = (1 + factor × elapsedDays / stability)^decay`

`decay = −w[20]`, `factor = 0.9^(1 / decay) − 1`.

For parity with Recall's scheduler, elapsed days must use the difference of UTC
epoch-day numbers, not local day boundaries or exact fractional elapsed time.
It reuses the existing memory-model function and parameter values; it does not introduce a
second equation, change preview results, or write review states.

It reports only active cards in `review` state with previous responses,
finite positive stability and a valid past last-review timestamp. Coverage exposes
the excluded population. It averages valid probabilities with equal card weight
and labels them “Current predicted recall · FSRS,” kept in
optional details. Missing predictions are not represented as zero or labelled
objective comprehension. No database fields or backup-format changes were necessary.
Predicted and observed recall describe different populations and times; their
difference is not calibration. The implementation and tests are in the linked guide.

## Option B: reconstruct historical predictions

Modern logs store previous stability, exact elapsed days, rating and the authoritative
review timestamp. Exact elapsed time can approximate the previous timestamp;
consecutive complete logs for a card can also supply it. Replay must then use the
UTC-day rule and the model parameters that actually applied at that time.

Limitations prevent unconditional exact reconstruction:

- `elapsedDays` is fractional, whereas the model consumes UTC-day differences.
  Floating-point reconstruction can be ambiguous near midnight.
- Older migrations and backups contain default eligibility/audit fields.
- The historical model/parameter version is not logged.
- Deletions and incomplete imported history remove preceding evidence.
- Older builds froze `reviewedAt` at question presentation. Phase 3 uses coherent
  submission-time evaluation for new records, without rewriting the old ones.
  Replaying old records with an inferred tap time would change the prediction.

Do not silently fill missing history or call reconstructed estimates recorded
predictions. If explored later, require explicit data-quality flags, only include
reliably reconstructable records and disclose coverage and the model assumption.
Recommend against presenting these results as definitive personalized calibration.

## Option C: prospective, auditable calibration

If approved separately, add nullable log fields for **pre-review predicted
retrievability**, a **model/parameter identifier**, and the **elapsed-time input
used for the prediction**. The identifier must distinguish parameter sets and UTC
elapsed-time policy. Capture prediction at the same authoritative submission-time
instant used by the committed schedule; save it atomically with that result and log.
Do not recalculate or alter the selected schedule to collect analytics.

A non-destructive Room migration would leave old rows null. Backups would preserve
the new optional fields and keep older backups readable. No retroactive predictions
would be synthesized. Original event timezone is a separate reporting question,
not required for predicting with Recall's UTC-day rule.

Compare predictions against eligible outcomes with Again = 0 and Hard/Good/Easy = 1.
Use the Phase 1 eligibility and first-eligible-event/day policy. Suggested optional
details are Brier score `mean((R − outcome)²)` and observed-versus-predicted bins,
each with sample size and coverage. A desired-retention target is not a per-event
prediction and must never be substituted for one.

Recommend an initial display gate of 200 eligible events across 30 distinct cards,
and at least 30 events in each displayed bin. These are product safeguards, not
proof of accuracy. Repeated observations of a card are dependent; any future
uncertainty estimate must account for clustering by card. Interval-stratified views
(1–6, 7–29, 30+ elapsed days) require their own sufficient samples. Changing the
active population can change results, and ratings remain self-reported.

Even if calibration is approved, it should remain optional and observational.
Do not automatically optimize FSRS weights, prescribe extra reviews, change
existing equations, or reschedule cards. A separate review should approve the
migration, backup compatibility, metric interpretation and UI before implementation.
