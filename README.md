z# GymLevels

GymLevels is a local-first Android gym progress tracker for beginners. It stores everything on the device with no login, no server, and no external database.

## Features

- Workout routine screen with beginner-friendly exercises.
- Routine is loaded from `app/src/main/assets/config.json`, so your trainer/gym friend can edit exercises, weekly day, sets, reps, form cue, video link, and target muscle group.
- Interactive 3D-styled barbell loader: tap plates to add them to the bar, then save weight and reps for the selected exercise.
- Exercise screen includes a form-video button and a graphical target-muscle rendering based on the configured muscle group.
- Daily body weight tracking with an on-device trend chart.
- Daily progress photo capture using the native camera app and an outline overlay preview in the app.
- Photo timeline playback for visual progress over time.
- Native step counter integration through Android's `TYPE_STEP_COUNTER` sensor when the device supports it.

## Open In Android Studio

1. Open this folder in Android Studio.
2. Let Android Studio install/sync the Android Gradle plugin if prompted.
3. Run the `app` configuration on an emulator or Android phone.

This workspace shell does not currently expose Java, Gradle, or the Android SDK, so the project was scaffolded but not compiled here.

## Routine Config

Edit `app/src/main/assets/config.json` before building the app. Example:

```json
{        
  "name": "Bench Press",
  "day": "Monday",
  "sets": 3,
  "reps": "6-10",
  "cue": "Lock shoulder blades and keep a smooth bar path.",
  "videoUrl": "https://www.youtube.com/results?search_query=bench+press+proper+form",
  "muscleGroup": "chest, triceps, shoulders"
}
```

The muscle renderer currently recognizes: `chest`, `back`, `shoulders`, `biceps`, `triceps`, `core`, `quads`, `hamstrings`, and `glutes`.
