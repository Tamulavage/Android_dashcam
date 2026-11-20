# Dashcam Android Application

An Android Dashcam app built with Kotlin, Jetpack Compose, and CameraX. It supports continuous video recording, background recording via a Foreground Service, audio toggle, multi-lens camera switching, and saving videos directly to the device's public storage (`Movies/Dashcam`).

---

## Features

- **Live Camera Preview**: Real-time camera preview built with CameraX `PreviewView` embedded in Jetpack Compose via `AndroidView`.
- **Foreground Recording Service**: Reliable background video capture using `RecordingService` with foreground service notifications (`camera` and `microphone` service types).
- **Camera Lens Switcher**: Toggle across available rear camera lenses (ultrawide, main, telephoto) seamlessly with automatic restart of active recording when switching lenses.
- **Optional Audio Recording**: In-app switch allowing users to turn audio recording ON/OFF before or during sessions, stored in shared preferences.
- **Keep Screen On**: Automatically manages screen stay-awake flags (`FLAG_KEEP_SCREEN_ON`) while the camera preview is active.
- **MediaStore Integration**: Saves recorded MP4 files under the public `Movies/Dashcam` directory using timestamped file naming (`yyyy-MM-dd-HH-mm-ss-SSS.mp4`).
- **Quick Video Gallery Viewer**: One-click launch to view saved dashcam videos in the default Android media viewer/gallery.

---

## Tech Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3 Theme (`DashcamTheme`)
- **Camera Engine**: CameraX (`1.4.1`)
  - `camera-core`
  - `camera-camera2` (including interop for focus configuration)
  - `camera-lifecycle`
  - `camera-view` (`PreviewView`)
  - `camera-video` (`Recorder`, `VideoCapture`, `Recording`)
- **Background Processing**: Android `LifecycleService` (Foreground Service)
- **State Management**: Kotlin `StateFlow` and Compose state (`remember`, `collectAsState`)
- **Minimum SDK**: Android 13 (API Level 33)
- **Target / Compile SDK**: Android 16 (API Level 36)
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`) and Version Catalog (`libs.versions.toml`)

---

## App Permissions

The application requests the following runtime permissions:
- `android.permission.CAMERA` – For video capture and live preview.
- `android.permission.RECORD_AUDIO` – For optional audio recording.
- `android.permission.POST_NOTIFICATIONS` – For foreground service notifications.
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_CAMERA`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`

---

## Project Structure

```
dashcam/
├── app/
│   ├── build.gradle.kts          # Module-level Gradle build script
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # Permissions & Service declarations
│           └── java/com/dmt/dashcam/
│               ├── MainActivity.kt # Jetpack Compose UI & Service binding
│               ├── RecordingService.kt # Foreground service handling CameraX & video recording
│               └── ui/theme/      # Compose Material 3 theme, colors, typography
├── gradle/
│   └── libs.versions.toml        # Dependency version catalog
├── build.gradle.kts              # Top-level Gradle build script
├── settings.gradle.kts           # Gradle settings
└── README.md                     # Project documentation
```

---

## Getting Started

### Prerequisites

- **Android Studio**: Android Studio Ladybug or newer recommended
- **JDK**: Java 11 or higher
- **Device / Emulator**: Physical Android device recommended for camera testing (API level 33+)

### Build & Run

1. Open the project root in Android Studio.
2. Sync the project with Gradle files.
3. Connect an Android device with camera capabilities.
4. Run the `:app` configuration on your connected device.

---

## Usage Guide

1. **Grant Permissions**: On first launch, tap "Grant Permissions" to allow Camera, Microphone, and Notification permissions.
2. **Turn On Camera**: Tap "Turn On Camera" to start the live preview.
3. **Sound Toggle**: Toggle the "Record Sound" switch ON or OFF according to your preference before starting recording.
4. **Switch Rear Lens**: If your device has multiple rear camera lenses (e.g., ultra-wide, wide, telephoto), tap the lens icon to cycle between available rear camera lenses.
5. **Start Recording**: Tap "Record Video". A persistent notification will appear indicating that recording is active in the background.
6. **Stop Recording**: Tap "Stop Recording" (or use the notification action) to complete and save the recording.
7. **View Recordings**: Tap "View Saved Videos" when the camera is off to open saved MP4 files in `Movies/Dashcam` via the system gallery.
