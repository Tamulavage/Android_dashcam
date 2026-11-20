# Walkthrough - Add Camera Preview and Control Button

I have successfully implemented the camera preview feature with a control button in the Dashcam app.

## Changes

### Android Manifest
#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/tamul/projects/android/dashcam/app/src/main/AndroidManifest.xml)
- Added `<uses-permission android:name="android.permission.CAMERA" />`
- Added `<uses-feature android:name="android.hardware.camera.any" android:required="false" />`

### App Dependencies
#### [MODIFY] [build.gradle.kts](file:///C:/Users/tamul/projects/android/dashcam/app/build.gradle.kts)
- Added CameraX dependencies (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` version `1.4.1`).

### UI & Camera Implementation
#### [MODIFY] [MainActivity.kt](file:///C:/Users/tamul/projects/android/dashcam/app/src/main/java/com/dmt/dashcam/MainActivity.kt)
- Implemented `CameraScreen` with dynamic runtime permission handling (`rememberLauncherForActivityResult`).
- Added a toggle button ("Turn On Camera" / "Turn Off Camera" / "Grant Camera Permission").
- Integrated CameraX `ProcessCameraProvider` and `PreviewView` inside an `AndroidView` to display the live camera feed when active.

## Verification Results

### Automated Tests
- Ran Gradle build (`./gradlew app:assembleDebug`), which completed successfully with exit status `BUILD SUCCESSFUL`.
