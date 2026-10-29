# Security policy

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting: [report a vulnerability](https://github.com/sidalidev/echo/security/advisories/new) (also in the app: About, "Report a security issue"). If you cannot use GitHub, write to contact@sidali.dev. You will get an answer within a week.

Include what an attacker can do, the steps to reproduce, and the version or commit.

## Scope

In scope: anything that lets another app or a remote party hear or read a take, make Echo record outside a test the user started, keep a take after the app is left, use the network, or leave the phone's audio mode changed after a test.

Out of scope: what Android itself shows while the microphone is in use (the privacy indicator), and other apps that hold the microphone at the same time.

What is protected and what is not: [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md). Reviews: [docs/security/](docs/security/) (internal, no external audit yet).

## Design notes

- No INTERNET permission. CI checks the merged manifest against [docs/permissions.txt](docs/permissions.txt).
- Recording starts only from the Test me button, lasts five seconds at most and stays in memory. Nothing is written to files.
- The take is cleared when the app goes to the background.
- The audio mode is restored when a test ends, including on error (`CallSession` is `AutoCloseable`).
- Backups and device-to-device transfers of app data are disabled.

## Release signing

APKs are signed with a single key. Its certificate SHA-256 is:

`1b9b6d07abbcd14d8b58681a769c2599f28422a4661232abc1b02e8b6dc20d02`

The app shows the certificate of the installed APK in About, "Signing certificate" (first 8 and last 4 characters: `1b9b6d07…0d02`), read from Android at run time. If it differs, the app was not built by us. Obtainium and Android refuse an update signed with any other key.
