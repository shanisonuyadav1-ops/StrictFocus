# Strict Focus

Strict Focus is an Android app that uses an Accessibility Service to return to the Home screen when a selected app is opened during an active lock.

## Compatibility
- Minimum Android version: Android 5.0 (API 21), as configured in `apk/app/build.gradle.kts`.
- Built with Java 17, Android Gradle Plugin 8.5.2, and Gradle 8.7.
- The GitHub Actions workflow builds a debug APK and uploads it as the `StrictFocus-APK` artifact.

## Build
Open the `apk` folder as an Android Gradle project, or run the GitHub Actions workflow from the Actions tab.

## Important limitations
Android manufacturers may restrict Accessibility Services or background activity. On some devices, users must manually enable the service and allow any relevant background/autostart settings. No APK can be guaranteed to install or behave identically on every device. Debug APKs may also trigger security warnings on some phones.

This version does not use Device Admin to prevent uninstalling, and it does not intercept Android Settings/uninstall screens. This avoids intrusive behavior but means users can still disable the service or uninstall the app.
