# MBM - Original Project Overview for AI Agents

This project, MBM (Moment By Moment), is an Android application designed for creating daily journals through short video clips and images.

## Architecture & Core Modules
- **Journal Management:** Managed via `DashboardActivity`, which handles folder-based isolation for different journals.
- **Calendar Navigation:** `MainActivity` and `CalendarAdapter` provide a grid interface for the year 2026 (current project focus).
- **Media Pipeline:** `ExportEngine.kt` leverages `androidx.media3` (Transformer, Effect, Common) to perform:
    - **Surgical Cut:** Trimming a 1-second segment from a source video with rotation support.
    - **Smash:** Concatenating multiple segments into a single exportable movie.
- **Automated Distribution:** The `app/build.gradle.kts` contains a custom task `copyApkToYDrive` that renames the debug APK to `mbm.apk` and moves it to `Y:\apps\mbm` upon successful build.

## Key Technical Details
- **Min SDK:** 24
- **Target SDK:** 35
- **Media3 Version:** 1.2.0
- **Storage:** Uses public Downloads directory with a custom `MBM` subfolder structure.
