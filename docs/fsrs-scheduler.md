# Recall scheduling

Recall uses the FSRS-6 memory equations and the official 21 default weights at a
default desired retention of 90%. The Kotlin model is ported from the MIT-licensed
`open-spaced-repetition/go-fsrs` implementation at commit
`61159b9b3891b1279b63206afef4086d7ef92992`.

The scheduling engine is independent from Compose. It calculates all four rating
outcomes together. Since Phase 3, the UI refreshes previews on resume and each minute,
then evaluates the selected rating at submission time. If any displayed interval
changed, it refreshes the buttons and requires a second, explicit choice. Otherwise
the fresh submission-time result is persisted unchanged, with log, last-review and
next-due timestamps all anchored to that same instant. The FSRS equations are unchanged.

Recall keeps a deliberate product-level learning policy: **Again** schedules a
10-minute learning or relearning step. Hard, Good, and Easy graduate to FSRS review
intervals. FSRS elapsed days use UTC calendar-day boundaries while the audit log
keeps the exact elapsed duration, so device timezone and daylight-saving changes
cannot silently alter an already calculated due timestamp.

Each committed review atomically stores the authoritative due timestamp and review
log, including before/after due date, state, stability, difficulty, repetitions, and
lapses. Database migration 3 to 4 preserves all existing cards and history.
