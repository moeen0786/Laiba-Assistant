# Laiba — UI-only Android prototype

Native Android application written in **Java**. This first milestone contains only the visual shell:
- Laiba splash screen
- Gemini verification screen (visual only)
- Chat-style home screen
- Voice/call entry visual
- Bottom navigation visual
- Laiba adaptive app icon
- GitHub Actions workflow to build a debug APK

No Gemini API, sign-in, chat, microphone, speech, network, or navigation functionality is connected yet. Buttons are visual placeholders by design.

## Build on GitHub
Push this project to the repository's `main` branch, or run the workflow manually from Actions. Download the `Laiba-debug-apk` artifact after the workflow succeeds.

## Local build
Requires JDK 17, Android SDK 35, and Gradle 8.9:
```bash
gradle assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

Package: `com.laiba.assistant`
