# Study activity

Insights includes a native Compose activity calendar. It reads completed response history
from `ReviewLogEntity.reviewedAt`; new cards, due states and scheduled intervals do not
contribute. Each log counts as one response, including multiple responses to the same
card on the same day. No activity records are persisted and Room remains at version 4.

## Data path

`RecallDao.studyActivity` → `RecallViewModel.studyActivity` → `InsightsScreen` →
`StudyActivityCard`. `studyActivityQuery` aggregates a single indexed timestamp range
inside SQLite, returning at most one row per local date. It never fetches full log
entities or queries each individual cell.

The query derives historical offsets from `ZoneId.systemDefault().rules`, including all
transitions within the past year. It adds the offset applicable at the event timestamp
before deriving the calendar-day bucket. This handles half-hour offsets and DST gaps
and repeated hours. The period uses local start-of-day boundaries, not fixed 24-hour
durations. The ViewModel observes Room invalidations and checks date/timezone changes
once a minute while subscribed; resuming the Activity also refreshes the period.

## Presentation

- A rolling year, with approximately 16 recent week columns initially visible on a phone.
  Narrow screens scroll instead of shrinking cells below 12dp visual squares. Weekday
  rows have extra height for readable labels and larger font scales.
- Locale-specific first weekday and month labels. An explicit UI language preserves the
  system locale's region. The chronological grid remains left-to-right in RTL; the
  heading and details sheet follow the app direction.
- Future days in the current week and dates preceding the range are blank and not
  interactive. Today has a subtle outline.
- Fixed levels: 0, 1–5, 6–15, 16–30, 31+ responses. Counts remain exact integers;
  intensity colors blend the existing theme primary and interactive surface.
- Any rendered date, including a zero day, opens a bottom sheet with localized full date
  and completed response count. Each cell exposes the same information to accessibility.
- English and Arabic resources are bundled together for offline language switching.
- Preview fixtures are confined to `StudyActivityPreviews.kt`; runtime uses Room only.

## Limits inherited from existing history

All timestamps are interpreted in the current device timezone. Original event timezones
were not stored, so changing timezone can move an event to another calendar day. The
calendar follows the existing retention of review logs: deleting a card, lesson or
subject currently deletes its associated logs and therefore removes that activity.
There are no streaks or extra session metrics in this feature.

## Verification

`StudyActivityTest` tests fixed levels, empty years, current partial weeks, month/year
boundaries, locale week ordering, Arabic month labels and DST day lengths.
`StudyActivityPersistenceTest` uses real Room/SQLite queries to test repeated responses,
due-only cards, local midnight, range exclusion, historical DST offsets and index usage.
`StudyActivityUiTest` checks live Insights updates from `RecallViewModel.rate`, latest-week
positioning, date details, historical scrolling, empty small-phone layout and dark Arabic
at 1.5× font scale. It captures screenshots in the test app's `activity-validation` folder.

## Changed files

- `data/RecallDatabase.kt` and `data/StudyActivityQuery.kt`: observed aggregation query.
- `domain/StudyActivity.kt`: activity projection, levels and calendar layout.
- `RecallViewModel.kt`: lifecycle-aware reactive activity state and refresh.
- `ui/DashboardScreens.kt`: Insights integration and locale selection.
- `ui/components/StudyActivityCard.kt`: themed calendar and date-details sheet.
- `ui/StudyActivityPreviews.kt`: light, dark Arabic and empty previews.
- `res/values/strings.xml`, `res/values-ar/strings.xml`: localized labels and plurals.
- `app/build.gradle.kts`: retain both bundled UI languages for offline switching.
- `StudyActivityTest.kt`, `StudyActivityPersistenceTest.kt`, `StudyActivityUiTest.kt`: tests.
- `docs/study-activity.md`: design, data flow, validation and inherited limitations.
