# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- A new Settings tab with a gear icon now appears in the bottom navigation bar, alongside Calendar, Snakes and Food Stock. The Settings screen is a placeholder for now — it paves the way for upcoming features like importing and exporting your data, but has no actions yet.
- The groundwork for backups is in place: the app can now gather everything it knows — your snakes, feedings, sheds, weights, and food stock — into one safe, portable backup file. A way to create that file from inside the app arrives in a coming update.
- The other half of backups is ready: given a valid backup file, the app can now restore everything it holds — your snakes, feedings, sheds, weights, and food stock — replacing what's on the phone with exactly the contents of the file and keeping each entry's original identity so links between records stay intact. A file the app can't trust (unreadable, saved by a newer version, or referencing snakes that aren't in it) is rejected whole, leaving your current data untouched. A way to pick that file from inside the app arrives in a coming update.
- Backups now start from inside the app: Settings has a new "Export backup" action. Tap it, choose where the file should go (the app suggests a name with today's date), and your complete data — snakes, feedings, sheds, weights, and food stock — is saved as one JSON backup file you can keep anywhere. A message then confirms the saved file by name, or explains why the export couldn't finish.
- Backups now come back into the app the same way: Settings has a new "Import backup" action. Choose a JSON backup file, and the app asks you to confirm first — warning plainly that the import replaces all your current data — then restores everything at once: your snakes, feedings, sheds, weights, and food stock. Afterwards a message lists what was restored, or explains exactly why the import didn't go through. After a successful import, your feeding reminders are immediately brought up to date with the restored data.

### Changed
- The app now targets Android 17 (API level 37), the newest stable Android release, so it runs under the current system rules on new phones. There is no visible change to how the app looks or works.
- The app's build tools and libraries (Kotlin symbol processing, Jetpack Compose, Navigation, Room, coroutines test) were updated to their latest stable versions. There is no visible change to how the app looks or works.

## [1.1] - versionCode 3

### Added
- Feeding reminders: when a snake comes due, the app now sends a notification at 9:00 that morning, and repeats it every morning until the feeding is logged.
- On Android 12 and newer phones where exact alarms are turned off, the app now asks once whether to allow exact alarms, so feeding reminders can arrive at the exact scheduled minute. Choosing "Not now" remembers your answer and the app won't ask again.

### Changed
- Reminders now arrive right on time on phones that allow exact alarms. Where the phone doesn't allow it, a reminder may arrive up to about a quarter of an hour later — but always on the correct day.
- Reminders reschedule instantly whenever feeding information changes or the phone restarts, so the next reminder always matches the latest schedule.
- If the phone was off at reminder time, the missed reminder is delivered as soon as the phone is back on, instead of being skipped until the next morning.

## [1.0] - versionCode 2

### Changed
- The app's underlying build configuration was reorganized, with no change to the app's features or behavior.
- The app's internal build tools and libraries were updated to their latest supported versions. There is no visible change to how the app looks or works.
- The app's upgraded build setup was verified end-to-end and automated tests were added to confirm it works. There is no visible change to how the app looks or works.
- The repository's developer tooling for agents was adjusted internally. There is no visible change to how the app looks or works.
- The app's build process now runs on Java 17, a newer version of the Java runtime. There is no visible change to how the app looks or works.
- The build now automatically downloads a compatible Java development kit if one is missing, so the project builds without extra manual setup. There is no visible change to how the app looks or works.
