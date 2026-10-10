# Review navigation

Review sessions keep a read-only history of earlier questions, including skipped
and rated cards. Previous card opens the most recently passed card; repeat it to
move farther back. Next card walks forward through history, then returns to the
unfinished question (or completion screen). The completion screen also offers
Previous card. Show answer remains available in history; ratings do not.

English, Spanish, French and German: swipe right to skip/next and left to go back.
Arabic mirrors this: swipe left to skip/next and right to go back. Vertical drags
continue scrolling long card text; short or canceled drags do not navigate.
Buttons provide the same actions without requiring gestures.

A localized hint appears on the first review card after installing this feature.
Dismissal is persisted locally in DataStore, including across restarts. It is a
device-local onboarding preference, not study data in exported backups.

History never submits ratings or modifies FSRS, due dates, skip counts, or review
logs. Returning to the current question preserves its answer-reveal state. Time
spent reading history is excluded from that question’s foreground review timer.
Navigation is disabled while a rating is being saved.

History survives Activity recreation through the existing session ViewModel; it
ends when the session is closed. It is not a permanent cross-session browser and
does not survive process termination. Completed reviews remain stored normally.
