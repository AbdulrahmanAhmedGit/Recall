# Recall motion design

Motion clarifies changes; it never changes review calculations, schedules, database
records, or interaction eligibility. Everything uses the existing Compose libraries.

## Shared primitives

`ui/design/RecallMotion.kt` contains:

- `RecallMotionProvider`: observes Android animator duration scale, unregisters its
  observer on disposal, and disables custom motion when Remove animations is enabled.
  Compose's native duration scale continues to control animation speed.
- `recallPress`: 120 ms, 1.5% press compression, drawn through a graphics layer.
  The layout/touch area stays unchanged, alongside native ripple feedback.
- `recallScreenTransition`: 280 ms directional fade/short slide. Logical navigation
  reverses in RTL; main destinations have less travel than detail screens.
- `AnimatedRecallNumber` / `AnimatedRecallCount`: 600 ms eased changes from the
  currently displayed value, including interrupted updates. Initial data is immediate;
  identical targets do not restart. Locale/units/precision belong to the formatter.
  Exact final values are restored at completion; TalkBack exposes the final value
  immediately instead of announcing intermediate numbers.
- `animatedRecallProgress`: bounded, finite progress with 240 ms easing.
- `RecallExpansion`: coordinated 240 ms fade/vertical reveal.
- `recallItemMotion`: keyed lazy-list insertion/removal/placement, disabled entirely
  under reduced motion. No staggered mass entrance or artificial loading delay.
- `recallArrival`: short, one-shot incoming review/completion surface feedback.

## Integration and limits

Today and Insights counts, observed/predicted percentages, lesson counts, and memory
progress use typed values, never parse localized strings. Navigation preserves each
main destination's saveable state/scroll position. Dock, primary actions, icon actions,
subject/lesson rows and review ratings have consistent interaction feedback. Review
answer reveal keeps the question visible; new cards never retain outgoing answer text.
Insights details/history expand, calendar selection transitions, resource/list changes
animate, and import errors reveal without blocking editing.

Material text fields, switches, checkboxes, tabs, dropdowns, dialogs, and sheets retain
their native focus/selection/opening motion rather than stacking custom animations on
top. Loading indicators only represent existing real asynchronous operations. The
activity heatmap keeps precise colors and does not animate hundreds of individual cells.

Only visible lazy-list entries animate. Press/arrival transforms read animated state
in the draw phase; numeric frame updates stay inside their leaf composables. Animation
coroutines cancel on disposal or a newer target. Controls remain usable during motion.
No perpetual decorative effects, new animation dependencies, or release/version changes.

This is a restrained pass, not a new visual identity. Device-level frame-time profiling
on low-end physical hardware remains valuable; emulator functional checks are not a
guarantee of a particular FPS.
