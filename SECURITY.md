# Security policy

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting: [report a vulnerability](https://github.com/sidalidev/echo/security/advisories/new) (also in the app: About, "Report a security issue"). If you cannot use GitHub, write to contact@sidali.dev. You will get an answer within a week.

Include what an attacker can do, the steps to reproduce, and the version or commit.

## Scope

In scope: anything that lets another app or a remote party read Clipshot's copies or settings, make it copy or delete an image it should not, make it use the network, or make the Capture accessibility service do anything beyond closing the Quick Settings panel, taking a screenshot and turning itself off.

Out of scope: the clipboard itself. Android lets the focused app and the keyboard read the clipboard; that is inherent to copying anything.

What is protected and what is not: [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md). Reviews: [docs/security/](docs/security/) (internal, no external audit yet).

## Design notes

- No INTERNET permission. CI checks the merged manifests against [docs/permissions.txt](docs/permissions.txt).
- `ShotProvider` is not exported. It serves only names matching `shot-<digits>.<png|jpg|webp>` from the app's no-backup directory, read-only.
- Gallery removal (optional, needs All files access) deletes only rows owned by a system screenshot app, and re-checks the row just before deleting.
- The Capture accessibility service (full flavor only) subscribes to no event, has `canRetrieveWindowContent="false"`, no gestures and no key filtering. `CaptureConfigTest` fails if that declaration changes. The play flavor removes it from the manifest; CI checks the merged manifest.
- Backups and device-to-device transfers of app data are disabled.

## Release signing

APKs are signed with a single key. Its certificate SHA-256 is:

`b2d10abfec2170c29d4c6f957bbb353608f9cf5591d8b3cd01b4694cc8d8c2d3`

The app shows the certificate of the installed APK in About, "Signing certificate" (first 8 and last 4 characters: `b2d10abf…c2d3`), read from Android at run time. If it differs, the app was not built by us. Obtainium and Android refuse an update signed with any other key.
