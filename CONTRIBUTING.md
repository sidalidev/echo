# Contributing to Echo

Thanks for helping. Echo does one thing; contributions that keep it small, private and reliable are the most welcome.

## Ground rules

- **No network, ever.** A pull request that adds the INTERNET permission, directly or through a dependency, will not be merged. CI fails if the merged manifest contains it.
- **No new dependency** without a strong reason. Today the app uses only AndroidX Compose and Activity.
- **Two permissions, no more by default.** Microphone and audio settings. Anything else must be optional, asked at the moment it is needed, and explained in the UI.
- **Nothing on disk.** Takes stay in memory.
- Keep it simple. If a feature needs a settings screen of its own, it probably belongs in another app.

## Getting started

1. JDK 21, Android SDK with platform 36.
2. `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug`
3. Test on a real phone with a headset: the emulator has no real microphone, so it always reports silence.

## Pull requests

- One topic per pull request, with a short description of what changes for the user.
- Add or update unit tests in `app/src/test` for any change to `Levels`.
- Echo is English only. Strings go in `values/strings.xml`; lint fails on hard-coded text.
- Colors are the sidali.dev palette, seeded on Echo's Heather (`Palette` in `Tokens.kt`). Never write a color in a screen: add a role in `Tokens.kt`. CI fails on a color literal anywhere else, and `ContrastTest` fails if a text and background pair drops below WCAG AA.
- Recording starts only from Test me and never touches storage.
- Update `CHANGELOG.md` under "Unreleased".

## Reporting bugs

Open an issue with your phone model, Android version (and ROM, e.g. GrapheneOS), and what you expected. Say which headset you used (USB-C, jack adapter, Bluetooth) and what Echo showed as the mic.
