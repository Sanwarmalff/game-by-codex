# Native Offline Game Hub

A native Android/Kotlin app that downloads zipped HTML games from a GitHub-hosted catalog, stores them in internal storage, and plays them offline in a native WebView.

## Configure the catalog

1. Upload `games.json`, game logos, and game zip files to a GitHub repository.
2. Replace `CATALOG_URL` in `app/src/main/java/com/example/offlinegamehub/MainActivity.kt` with the raw URL of your `games.json` file.
3. Each game zip must contain an `index.html` at the root of the zip.

## Build on GitHub

Push to `main`, open the Actions tab, run the `Build Android APK` workflow, and download the `native-offline-game-hub-debug-apk` artifact to install on your phone. The repository intentionally does not commit Gradle wrapper files because some mobile PR creation tools reject binary files; the workflow generates the wrapper first, then runs `./gradlew assembleDebug`.


## Build configuration

`gradle.properties` enables AndroidX, which is required by Jetpack Compose and the AndroidX dependencies used by the app.
