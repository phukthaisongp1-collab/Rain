# RAIN Browser

RAIN is an Android browser built with Kotlin, Android SDK, Gradle, and Android WebView.

## Project configuration
- Application ID: `com.rain.browser`
- Product name: `RAIN`
- Initial version: `0.1.0` (versionCode `1`)
- Minimum Android version: Android 10 (API 29)
- Compile / target SDK: Android 16 (API 36)
- JDK: 17
- Android Gradle Plugin: 9.5.2
- Gradle Wrapper: 9.6.0
- Kotlin Gradle Plugin: 2.4.10

## Build
```sh
./gradlew clean lint testDebugUnitTest assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Security baseline
The initial WebView disables JavaScript, DOM storage, file/content access, mixed content, and multiple windows. It enables Safe Browsing where supported, accepts only HTTP(S) navigation, and cancels SSL certificate errors.

Release signing is intentionally not configured in this scaffold. It is a separate approved task and must use the project's protected Android Keystore through GitHub Actions Secrets.
