# Snake Tracker

A fully local, offline Android app (Kotlin + Jetpack Compose + Room) for tracking your snakes:
feeding, refusals, sheds, weight, and food stock, with local reminder notifications.

**All data stays on your device.** The app has no internet permission, no analytics, no
cloud/account sign-in, and no sync code of any kind. Everything is stored in a private
SQLite database (via Room) inside the app's own data folder.

## Features

- **Multiple snakes**: name, species, morph, enclosure, notes, custom feeding interval.
- **Feeding log**: date, food type/size, accepted or refused, assist-fed flag, notes.
- **Shed log**: date, complete/incomplete, notes (stuck caps, retained eye caps, etc).
- **Weight log**: date + grams, with notes, per snake.
- **Food stock**: track inventory of frozen feeders with quantity and low-stock warning.
- **Reminders**: a daily local background check (WorkManager) looks at each snake's last
  feeding date + its feeding interval, and posts a local notification if a feeding is due.
  Reminders survive device reboot (`BootReceiver` reschedules the check).

## How to open and run it

1. Install [Android Studio](https://developer.android.com/studio) (Koala or newer recommended).
2. Choose **Open** and select this `SnakeTracker` folder (the one containing `settings.gradle.kts`).
3. Let Android Studio sync Gradle (it will download the Gradle wrapper/distribution and
   dependencies the first time — you'll need internet access for this one-time setup, the
   same as any Android project).
4. Connect an Android device (or start an emulator), then click **Run ▶**.
5. On first launch, allow the notification permission when Android asks, so feeding
   reminders can be shown.

Minimum supported Android version: Android 8.0 (API 26).

## Project structure

```
app/src/main/java/com/snaketracker/app/
├── data/                  Room entities, DAOs, database, repository (local storage only)
├── reminders/             WorkManager worker + scheduler + notification helper + boot receiver
├── navigation/            Jetpack Navigation Compose graph
└── ui/
    ├── screens/           Compose screens: snake list, snake detail, add/edit, dialogs, food stock
    ├── viewmodel/         SnakeViewModel exposing Flow-based state to the UI
    └── theme/             Compose Material3 theme
```

## Customizing

- **Change the default feeding interval**: edit `feedingIntervalDays` default in
  `data/entities/Snake.kt`, or just set it per-snake in the Add/Edit Snake screen.
- **Change reminder check frequency**: `reminders/ReminderScheduler.kt` (currently once a day).
- **Add photos per snake**: not included yet, but the `Snake` entity can be extended with an
  image file path (stored locally, e.g. in the app's internal files directory) and displayed
  with Coil or `BitmapFactory`.
- **Export/backup your data**: since everything lives in the local Room database file
  (`snake_tracker.db` inside the app's private storage), you could add an "Export to CSV/JSON"
  button that writes to the device's Downloads folder using the Storage Access Framework —
  this keeps you in full control of if/when any data ever leaves the device.

## Why no cloud sync

Per your request, this app is intentionally offline-only: no network permission is declared
in `AndroidManifest.xml`, and there is no networking code anywhere in the project. Your snake
records never leave your phone unless you manually export/back them up yourself.
