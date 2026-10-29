# Echo

**Hear yourself the way a call does, before the call.**

Plug in your headset, tap Test me, say a few words. Echo records five seconds through the same path Meet, Teams or a phone call use, squeezes it the way a call does, and plays it back. You hear what the other side will hear, and Echo tells you if you are too quiet, clipping, or not coming through at all.

## Why trust it

| | |
|---|---|
| No internet permission | The app cannot open a network connection. Check `AndroidManifest.xml`, or the permissions screen once installed. |
| Nothing stored | Each take lives in memory and is gone when you leave the app. Nothing is written to storage, so there is nothing to erase. |
| Two permissions | Microphone, asked on the first test. Audio settings, to put the phone in call mode on your headset for the length of a test. |
| No account, no tracking | No analytics, no ads, no cloud backup, no device-to-device transfer. |
| Checkable | About shows the signing certificate of the installed app, to compare with [SECURITY.md](SECURITY.md). Last security review: 29 Oct 2026, internal, no external audit yet ([docs/security](docs/security/), [threat model](docs/THREAT_MODEL.md)). |

## Features

- **Call path, not the voice recorder.** The phone switches to communication mode, picks the mic a call would pick (USB headset first, then wired, Bluetooth, then the phone itself) and records with the voice communication source, so echo cancellation and noise suppression are the ones your calls get.
- **Call quality.** The take goes through the Opus codec at 32 kbps, the codec WebRTC calls use, then plays back on the call output.
- **A verdict.** Silent, quiet, good or clipping, from the loudest part of what you said.
- **Hear the raw mic** next to the call version, to tell a bad mic from a bad call.
- Follows the headset you plug in or pull out while the app is open.
- Its own color: Heather, the same as the icon, in light and dark, with a themed icon. Flat, rounded, set in Bricolage Grotesque and Figtree. English.

## Install

Android 12 or newer. Built and tested for GrapheneOS, works on any Android.

- [Obtainium](https://github.com/ImranR98/Obtainium): add this repository's URL; Obtainium installs Echo from the releases and keeps it up to date.
- Or download the APK from [Releases](../../releases) and check its SHA-256 against the `.sha256` file published next to it.

Echo cannot update itself, by design: it has no internet access.

## How it works

1. `Route` finds the input a call would use and sets it with `setCommunicationDevice`, in `MODE_IN_COMMUNICATION`.
2. `CallSession` records five seconds at 48 kHz from `VOICE_COMMUNICATION` into memory, then restores the audio mode.
3. `Opus` encodes and decodes the take with the platform codec (`MediaCodec`, 20 ms frames, 32 kbps).
4. `Levels` measures the loudest 30 % of 20 ms frames and the peak, and gives the verdict.
5. Playback uses `AudioTrack` with `USAGE_VOICE_COMMUNICATION`, so you hear it where a call would sound.

The whole app is about 1,350 lines of Kotlin. Read `Engine.kt` first.

## Build

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Release signing reads `~/.echo/keystore.properties` if present; otherwise the release APK is unsigned (`-Punsigned` forces it).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: [SECURITY.md](SECURITY.md). Privacy: [PRIVACY.md](PRIVACY.md).

## License

[Apache-2.0](LICENSE). Fonts and icon: see [ASSETS.md](ASSETS.md).
