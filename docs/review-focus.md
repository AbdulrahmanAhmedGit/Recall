# Lesson pauses and focused study

## Temporarily leave a lesson out of mixed review

Open a lesson and choose **Pause lesson**. Choose today, the next seven calendar
days (including today), or a custom date range. Both selected dates are included.
Future date ranges are supported. **Resume / cancel pause** removes the range.
Today also lists currently paused lessons so they are easy to resume.

A pause excludes the lesson from Today’s mixed-review counts, due/upcoming lists,
the full mixed queue, small backlog batches, and reminder counts. It does not
suspend its cards, change due dates, erase history, or change memory analytics.
The review calendar still shows the underlying schedule. Explicitly opening a
lesson or choosing focused study can include paused lessons: the pause controls
mixed review, not access to the content.

Dates become local start-of-day instants when saved, with an exclusive end at
the start of the following day. This handles daylight-saving days correctly.
Changing timezone later does not reinterpret an already saved interval. Expiry
is checked on the UI’s minute clock/resume and each background reminder check;
it does not need an exact alarm. Existing reminder quiet times and deduplication
still apply.

## Focus on a subject, chapter, or lesson

Choose **Focused study** on Today, a subject, or a lesson. A chapter’s action menu
also offers it. Select a subject and optionally narrow it to a chapter or lesson.

- **Due cards:** only eligible due cards; submitted ratings use the existing FSRS
  scheduling and review-history path. The existing new-card limit applies.
- **Practice all cards:** includes due and future cards in the scope; ratings only
  advance the practice session. They do not update memory state, due dates, review
  logs, activity heatmaps, or observed recall statistics.

Suspended cards and archived lessons/subjects are excluded in both modes. Cards
are randomized. Each focused session is bounded to 200 candidates to keep loading
and session state manageable; larger scopes can be studied in another session.
The due-mode new-card limit may reduce the resulting session further. Empty
scopes produce an inline explanation, not an empty review screen. Practice
controls show **No schedule change** instead of predicted rating intervals, and
completion is labeled **Practice complete**.

## Persistence and boundaries

One optional pause interval per lesson is kept in DataStore. Saving another
replaces the previous interval. Backup settings include these intervals; older
backups without the field still load. Invalid intervals, duplicate lesson IDs,
and references to missing lessons are rejected by backup validation. Deleting a
lesson or subject removes its associated pause preferences.

There is no Room migration, FSRS equation change, or release/version change.
Already-open review sessions are snapshots; changes to pauses affect subsequently
loaded mixed queues. Process death retains saved pauses and committed normal
reviews, but does not persist an unfinished practice session.
