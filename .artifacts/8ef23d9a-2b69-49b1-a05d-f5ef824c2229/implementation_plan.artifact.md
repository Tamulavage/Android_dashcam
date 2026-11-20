# Add Camera Preview and Control Button

## Problem Statement
The app currently displays a placeholder greeting in `MainActivity`. The user wants to add a button to turn on the camera and display live video using CameraX and Jetpack Compose.

## User Review Required
> [!IMPORTANT]
> - **Camera Permission**: The app will request `android.permission.CAMERA` dynamically at runtime when the user clicks the "Turn on Camera" button.
> - **CameraX Dependencies**: We will add CameraX dependencies (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) to `app/build.gradle.kts`.

## Open Questions
- None. CameraX with `PreviewView` inside an `AndroidView` is the standard, most robust approach for displaying camera video in Jetpack Compose.

## Proposed Changes

### Android Manifest
#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/tamul/projects/android/dashcam/app/src/main/AndroidManifest.xml)
- Add `<uses-permission android:name="android.permission.CAMERA" />`.
- Add `<uses-feature android:name="android.hardware.camera.any" android:required="false" />`.

### App Dependencies
#### [MODIFY] [build.gradle.kts](file:///C:/Users/tamul/projects/android/dashcam/app/build.gradle.kts)
- Add CameraX library dependencies (version 1.4.1 or compatible).

### UI and Camera Integration
#### [MODIFY] [MainActivity.kt](file:///C:/Users/tamul/projects/android/dashcam/app/src/main/java/com/dmt/dashcam/MainActivity.kt)
- Add runtime permission handling for camera access (`rememberLauncherForActivityResult`).
- Create a camera preview Composable using `AndroidView` and `PreviewView`.
- Add a toggle button ("Turn on Camera" / "Turn off Camera") in the Home destination or a dedicated camera view.
- Bind CameraX `ProcessCameraProvider` to the lifecycle when camera is active.

## Verification Plan

### Automated Tests
- Build the app using `./gradlew assembleDebug` to ensure compilation succeeds with new dependencies and code.

### Manual Verification
- Deploy to an emulator or physical device with a camera.
- Click the "Turn on Camera" button, grant permission, and verify live video feed is displayed correctly.
