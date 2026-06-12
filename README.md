# CleanShare

An Android app that helps you rename files and remove metadata before sharing them. Protect your privacy by cleaning sensitive information from your files before distributing them.

## Features

- **Rename Files**: Easily rename files with custom names before sharing
- **Remove Metadata**: Strip metadata from files to protect privacy
- **Simple Interface**: User-friendly design for quick file management
- **File Sharing Ready**: Clean and prepare files for safe sharing

## Prerequisites

- [Android Studio](https://developer.android.com/studio)
- Android SDK 21+

## Installation & Setup

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. If needed, update gradle dependencies
5. Run the app on an emulator or physical device

## Building

The project is built entirely in **Kotlin** and uses Gradle for dependency management.

```bash
./gradlew build
```

To run the app:

```bash
./gradlew installDebug
```

## Automated APK Releases (GitHub Actions)

This repository includes a workflow at `.github/workflows/android-apk-release.yml` that:

- Builds both debug and release APKs
- Uploads APKs as GitHub Actions artifacts
- Creates a GitHub release with the generated APKs attached

### Triggers

- Pushes to `main`, `master`, and `build-apk-release`
- Manual runs from **Actions** using **workflow_dispatch**

### Optional release signing

If you add these repository secrets, release APKs are signed:

- `RELEASE_KEYSTORE_BASE64` (base64-encoded `.jks`)
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_PASSWORD`
- `RELEASE_KEY_ALIAS` (optional, defaults to `upload`)

If secrets are not configured, the workflow still builds a release APK (unsigned).

### Install from release

1. Open the latest GitHub release from the repository **Releases** page.
2. Download the release APK asset.
3. On Android, allow installs from unknown sources for your installer.
4. Open the APK and complete installation.

## How to Use

1. Launch the CleanShare app
2. Select a file from your device
3. Rename the file if desired
4. Remove metadata to clean sensitive information
5. Share the cleaned file securely

## Project Structure

- `app/` - Main application module
- `src/main/kotlin/` - Kotlin source code
- `src/main/res/` - Android resources (layouts, drawables, strings)

## Technologies Used

- **Language**: Kotlin
- **Platform**: Android
- **Build System**: Gradle

## Contributing

Feel free to fork this project and submit pull requests for any improvements.

## Sources Used

- [Android Developer Documentation](https://developer.android.com/)
- [Kotlin Official Documentation](https://kotlinlang.org/)
- [Google AI Studio](https://ai.studio/)
