# Reminders and portable backups

## Reminders

Enable Review reminders in Settings. Android 13+ requests notification permission at that point, not on first launch. Background delivery shows notification/channel access and battery restrictions, with links to Android settings.

A unique 15-minute periodic WorkManager job remains registered even when there are no due cards. Relevant changes also request a coalesced immediate check. Each execution reads current Room data and preferences. Quiet times override study windows; overnight blocks, temporary pauses and timezone changes are respected. A study window is preferred within 24 hours, otherwise the next allowed daytime is used. All-day quiet periods never produce an unsafe fallback.

Notifications aggregate due cards. Normal reminders are limited to one per local day, with an optional distinct study-window-start reminder after at least four hours. A backwards device-clock adjustment cannot suppress reminders forever. Review opens the review flow; the notification's pause action pauses delivery for two hours without changing due dates.

Android controls job execution: Doze, battery optimization, OEM sleeping-app policies and force-stop can delay or prevent it. No exact alarms, misleading foreground service, or unnecessary storage permissions are used. On restrictive devices, choose unrestricted battery use and remove Recall from sleeping apps. Reopen Recall after force-stop.

Tap Scheduler information six times total (the dialog title also counts) to enable persistent debug mode. Test notification uses the real channel but deliberately ignores due counts and pauses; it does not change the normal cooldown. Debug mode also shows last execution, delivery decision and next eligible time.

## Export all data

Settings → Export all data creates an unencrypted ZIP containing versioned study data/settings and the actual attached files. Keep the archive somewhere private. The source attachments must still be accessible. A missing attachment fails the export rather than silently producing an incomplete backup.

Restore full backup validates paths, sizes, data and SHA-256 attachment checksums before merging. Existing record IDs are kept; backup settings are restored after confirmation. Imported materials are copied into private app storage and exposed through a narrowly scoped FileProvider. Duplicate restores discard unused attachment copies. The Room schema is unchanged.

Archive limits: 512 MiB per file, 2 GiB total uncompressed, 50 MiB per JSON entry, 100,000 attachments. Export is staged in app cache, so sufficient free storage is required. The older JSON-only export remains available and explicitly excludes attachment contents.

## Verification

Run unit tests, APK/test-APK builds and lint with `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`.

Device tests cover actual notification posting, deduplication, pause actions, debug unlock, full material export/restore with an unavailable original source, pronunciation links, large-font review controls and mixed-direction stacked cards. Grant the installed test app notification permission before the notification tests. These tests do not guarantee delivery behavior on every manufacturer's battery manager.
