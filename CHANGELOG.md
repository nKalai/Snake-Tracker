# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Feeding reminders: when a snake comes due, the app now sends a notification at 9:00 that morning, and repeats it every morning until the feeding is logged.
- On Android 12 and newer phones where exact alarms are turned off, the app now asks once whether to allow exact alarms, so feeding reminders can arrive at the exact scheduled minute. Choosing "Not now" remembers your answer and the app won't ask again.

### Changed
- Reminders now arrive right on time on phones that allow exact alarms. Where the phone doesn't allow it, a reminder may arrive up to about a quarter of an hour later — but always on the correct day.
- Reminders reschedule instantly whenever feeding information changes or the phone restarts, so the next reminder always matches the latest schedule.

## [1.0] - versionCode 2

### Changed
- The app's underlying build configuration was reorganized, with no change to the app's features or behavior.
- The app's internal build tools and libraries were updated to their latest supported versions. There is no visible change to how the app looks or works.
- The app's upgraded build setup was verified end-to-end and automated tests were added to confirm it works. There is no visible change to how the app looks or works.
- The repository's developer tooling for agents was adjusted internally. There is no visible change to how the app looks or works.
- The app's build process now runs on Java 17, a newer version of the Java runtime. There is no visible change to how the app looks or works.
- The build now automatically downloads a compatible Java development kit if one is missing, so the project builds without extra manual setup. There is no visible change to how the app looks or works.
