# Lower Training 1.1.0

Standalone native Android lower-body workout tracker.

## Workouts
- Lower 1: Barbell Squat 4 sets, Romanian Deadlift 4 sets, 4 rounds of Leg Press -> Calf Press superset.
- Lower 2: Deadlift, Leg Extension, Leg Curl, Calf Press Machine; 4 sets each.

## Core behaviour
- Full-screen current set.
- Weight/reps entry with prior-set ghost values.
- IME Done on reps logs the set immediately and starts the next rest/transition.
- Full-screen rest timer with -15s / Skip / +15s.
- SQLite persistence.
- PR detection.
- Detailed analytics including weekly volume, exercise trends, best weight/e1RM and recent sessions.
- Configurable rest durations, sound and haptics.
- Offline-first; no account or network required at runtime.

## Build stack
Gradle 8.13, AGP 8.11.1, Kotlin 2.2.21, Compose BOM 2025.05.01, compile/target SDK 36.
