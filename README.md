<p align="center"><img src="docs/images/light.png" width="260" alt="Clipshot, light"> &nbsp; <img src="docs/images/dark.png" width="260" alt="Clipshot, dark"></p>

# Clipshot

**Press the two buttons. Your screenshot is already on the clipboard.**

Clipshot copies every new screenshot to the clipboard the moment Android saves it. Take a screenshot, switch to your chat, paste. That's the whole app.

Android takes the screenshot as usual, Clipshot only hands it to the clipboard. If you would rather not press the two buttons, the optional **Capture** tile and accessibility button take the screenshot for you (see below).

## Why trust it

| | |
|---|---|
| No internet permission | The app cannot open a network connection. Check `AndroidManifest.xml`, or the permissions screen once installed. |
| No background service | Android wakes Clipshot only when an image is saved (a JobScheduler content trigger). No persistent notification, no battery drain. |
| One permission | Read images, to see new screenshots. Only system screenshots from the last 30 seconds are looked at. |
| No screen reading | The optional Capture service cannot read anything on screen. It only presses the screenshot button. |
| Nothing leaves the phone | No account, no analytics, no ads, no cloud backup, no device-to-device transfer of its data. |
| Reproducible builds | Two builds of the same commit produce byte-identical APKs. |
| Checkable | About shows the signing certificate of the installed app, to compare with [SECURITY.md](SECURITY.md). Last security review: 24 Oct 2026, internal, no external audit yet ([docs/security](docs/security/), [threat model](docs/THREAT_MODEL.md)). |

## Features

- Every new screenshot goes to the clipboard, ready to paste anywhere that accepts images.
- **Remove from gallery** (optional): one minute after the copy, the screenshot is deleted from your photos and only lives on the clipboard. It needs "All files access", requested only when you turn this on, and used for nothing else. Only files saved by the system screenshot app are ever deleted, and only if they are untouched.
- **Capture** (optional): a Quick Settings tile and the floating accessibility button take a screenshot in one tap, and it goes to the clipboard like any other. See [The Capture service](#the-capture-service).
- **Pause tile** in Quick Settings to pause and resume.
- **About**: our principles, what leaves your device (nothing), the signing certificate, and **Erase everything**, which resets Clipshot to its first launch.
- Its own colors: Sapin green, the same as the icon, in light and dark, with a themed icon. Flat, rounded, set in Bricolage Grotesque and Figtree. English.

## Install

Android 11 or newer. Built and tested for GrapheneOS, works on any Android.

- [Obtainium](https://github.com/ImranR98/Obtainium): add this repository's URL; Obtainium installs Clipshot from the releases and keeps it up to date.
- Or download the APK from [Releases](../../releases) and check its SHA-256 against the `.sha256` file published next to it.

Clipshot cannot update itself, by design: it has no internet access.

Signing certificate SHA-256: see [SECURITY.md](SECURITY.md).

## How it works

1. `ShotJob` registers a JobScheduler job with a content trigger on `MediaStore.Images`. Android runs it only when the photo library changes.
2. The job looks for an image added in the last 30 seconds that a system screenshot app saved (`OWNER_PACKAGE_NAME`, see `Rules.kt`). While the screenshot is still being written, it waits for it (up to 10 s).
3. It copies the image into the app's private storage and puts a `content://` URI on the clipboard. `ShotProvider` is not exported: the app you paste into gets one-off read access through the clipboard grant, and nothing else.
4. The last three copies are kept so the clipboard stays valid; older ones are deleted.

The whole app is about 1,400 lines of Kotlin. Read `Rules.kt`, `Shots.kt` and `ShotProvider.kt` first.

## The Capture service

Android has no screenshot permission for apps. The only way for an app to take a screenshot is an accessibility service calling `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`, the same as pressing the two buttons. So Capture is an accessibility service, and it is off until you turn it on in the accessibility settings.

Accessibility services can be very powerful, so here is exactly what this one declares ([`capture_service.xml`](app/src/main/res/xml/capture_service.xml)) and does ([`Capture.kt`](app/src/main/java/dev/sidali/clipshot/Capture.kt)):

- It subscribes to **no accessibility events**: it is never told what happens on screen, what you type or which app is open.
- `canRetrieveWindowContent="false"`: it **cannot read** any window.
- No gestures, no key filtering, no fingerprint gestures. Its only flag is `flagRequestAccessibilityButton`, for the floating button.
- It makes two calls: close the Quick Settings panel (so it is not on the screenshot), then take the screenshot.
- Clipshot still has no internet permission. A unit test (`CaptureConfigTest`) fails if the declaration is ever widened.

Android shows its standard warning when you turn on any accessibility service ("full control of your device"); the service above is what Clipshot actually asks for. On Android 13 and later, if the APK was not installed by an app store, Android may say the setting is restricted: open App info, the ⋮ menu, then *Allow restricted settings*.

Notes: the floating accessibility button is drawn by Android and appears on the screenshot it takes; the tile does not. Capture is not in the Google Play build, since Play strictly limits accessibility services.

## Build

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew :app:testFullDebugUnitTest :app:lintFullRelease :app:assembleFullRelease
```

Release signing reads `~/.echo/keystore.properties` if present; otherwise the release APK is unsigned. To check reproducibility, build the same commit twice from two clones with `-Punsigned` and compare the SHA-256 of `app-full-release-unsigned.apk`.

Two flavors share all the code: `full` (GitHub, Obtainium, F-Droid) and `play` (Google Play, without the "remove from gallery" mode, since Play reserves All files access to file managers, backup and antivirus apps, and without Capture, since Play strictly limits accessibility services).

`pastecheck/` is a tiny test app that reads the clipboard the way a messaging app would, to check pasting end to end on a device or emulator.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: [SECURITY.md](SECURITY.md). Privacy: [PRIVACY.md](PRIVACY.md).

## License

[Apache-2.0](LICENSE). Fonts and icon: see [ASSETS.md](ASSETS.md).
