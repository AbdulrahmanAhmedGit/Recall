# Native screenshot inventory

## Preview 4 README captures — October 9, 2026

The six originals in `preview4/readme-preview4/` were freshly captured from the
current native Compose app on the API 34 Small_Phone emulator using
`ReadmeScreenshotsTest.captureCurrentNativeScreensWithDemoData`.
That workflow passed and built successfully. It navigates Library, subject resources,
English review, Cairo Arabic review, Insights and the calendar. It uses an isolated
in-memory Room library (three subjects, eighteen cards, synthetic historical events
and two notes), disables reminders, and restores the original preferences afterward.
No personal study database or files are read or published.

The README's feature panels are AI-assisted edits of these originals, not untouched
screen captures or pixel-identical composites. Each panel links to its original. Generation prompts
and provenance are in [the presentation inventory](../images/inside-recall/README.md).
Only test/documentation assets were changed; app behavior and the published APK are unchanged.

| Original | Captured feature |
| --- | --- |
| [library.png](preview4/readme-preview4/library.png) | Populated Library and native dock |
| [resources.png](preview4/readme-preview4/resources.png) | Subject notes, search and resource filters |
| [review.png](preview4/readme-preview4/review.png) | Revealed science card, intervals, ratings and Skip |
| [review-arabic.png](preview4/readme-preview4/review-arabic.png) | Cairo Arabic controls/content, RTL and isolated chemistry |
| [insights.png](preview4/readme-preview4/insights.png) | Activity at the top of Insights, synthetic completed events |
| [calendar.png](preview4/readme-preview4/calendar.png) | October 2026 next-review calendar and demo due counts |

## Earlier native captures

These images are captures of Recall's Android Compose UI, not AI-generated mockups.
All study content and populated review history are synthetic test fixtures. The
Today capture is an empty emulator installation. No personal study database,
backup, photograph or private material is published here.

| File | Feature | Capture scope |
| --- | --- | --- |
| `today-empty-light.png` | Today, calendar shortcut and dock | October 8 optimized preview smoke test; empty installation |
| `library-light.png` | Subject library | October 6 native component capture with demo subjects |
| `subject-light.png` | Optional chapter/direct-lesson hierarchy | October 6 native component capture |
| `lesson-light.png` | Tags, cards and lesson tabs | October 6 native component capture |
| `resources-light.png` | Notes, attachment entry, resource search and filters | October 6 native component capture with a synthetic note |
| `review-answer-dark.png` | Stacked reading surface, mixed text, ratings and Skip | October 6 native component capture; intervals belong to the demo card |
| `import-preview-light.png` | Editable/excludable cards and existing-lesson destination | October 6 native component capture with sample JSON |
| `schedule-light.png` | Study preferences and quiet-time editor entry | October 6 native component capture; visible list is only part of Settings |
| `activity-light.png` | Completed-review heatmap | Activity UI test with synthetic response history |
| `activity-dark-arabic.png` | Dark Arabic activity and enlarged text | Activity UI test with synthetic response history |
| `calendar-month-light.png` | Upcoming calendar month | Calendar UI test with fixture due dates |
| `calendar-day-light.png` | Selected date, types and lesson/card list | Calendar UI test with fixture due dates |
| `review-localized-arabic.png` | Arabic RTL review controls and intact chemical expression | October 8 localization UI test, dark mode, 1.3× font scale, synthetic card |
| `language-picker-arabic.png` | Arabic language sheet with five language choices | October 8 localization UI test, dark mode, 1.3× font scale |
| `settings-french.png` | Translated French settings, schedule and dock | October 8 localization UI test, light mode, small-phone emulator |

Component captures omit Android's status/navigation bars. Some show only a
component or a scrolled viewport, not a complete screen. Calendar tests use a fixed
date; labels and counts are illustrative and do not promise actual workload or
retention. Earlier captures can differ slightly from the latest revision's layout.

Static captures demonstrate presentation only. They do not prove background
notification timing, speech playback, persistence or restore behavior; those paths
have separate tests described in the feature and technical guides.
