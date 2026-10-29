# Changelog

## 1.4.0

- Clipshot's own colors: Sapin green, the same as the new icon, in light and dark, on a warm cream or deep green page. They no longer change with the wallpaper. Every text and background pair meets WCAG AA, checked by a test.
- New icon: the four rounded corners of a capture around a lens, cream on flat Sapin, with a themed version. The Quick Settings tiles use the same drawing.
- Flat and rounded everywhere: no gradient or shadow, rounder cards, tinted capsules with a full size touch target.
- New type: Bricolage Grotesque for titles and Figtree for text, both under the SIL Open Font License, bundled in the app. They follow your phone's text size.

## 1.3.2

- One main switch: the On / Paused card at the top of the main screen stops everything in one tap. Paused, nothing runs in the background, a pending gallery removal is cancelled (that screenshot stays in your photos), and Clipshot stays paused after a restart until you turn it back on. One tap resumes it, nothing to set up again.
- The Quick Settings tile and the main screen always show the same state, and TalkBack reads it ("On" or "Paused"). The whole card is the switch.

## 1.3.1

- Permissions: a screen that lists every access Clipshot uses (photos, and in the full version all files access and the accessibility service), why, and which feature depends on it. A check shows what is on. One tap turns it on, and when it is on, the same tap opens the exact settings page to turn it off. Open it from the main screen or from About.
- When an access is turned off, Clipshot says calmly what stops working and how to turn it back on. It never asks again on its own.

## 1.3.0

- About: our principles, the installed version and what's new, the date of the last security review, the signing certificate of the installed app (compare it with SECURITY.md), and links to the source code, the privacy policy and private security reporting.
- What leaves your device: nothing. The screen says so, and says what the optional Capture service cannot do.
- Erase everything: resets Clipshot to its first launch (settings, counters, the last three copies, gallery removal turned off). Type ERASE to confirm.
- Security: threat model and a dated internal review in `docs/`. CI now checks the permissions against a fixed list.

## 1.2.0

- Capture: a Quick Settings tile and the floating accessibility button take a screenshot in one tap, straight to the clipboard. It works through an accessibility service that cannot read your screen; it only presses the screenshot button. Optional, off by default, not in the Google Play version.
- Your phone's colors: Material You, from your wallpaper, with a matching palette on phones without it. Themed icon.
- No more white flash at launch, predictive back, springy buttons and haptic feedback.
- English only.

## 1.1.1

- Google Play build (same app, without the "remove from gallery" mode, which Play does not allow). Nothing changes for GitHub, Obtainium and F-Droid users.

## 1.1.0

- Remove from gallery (optional): the screenshot is deleted from your photos one minute after the copy.
- Quick Settings tile to pause and resume.
- Only screenshots saved by the system screenshot app are copied; downloads or synced files named "Screenshot…" are ignored.
- More reliable while the screenshot is still being written.
- Android 11 and 12 supported (storage permission).
- App data excluded from backups and device transfers. Reproducible builds.

## 1.0.0

- First release: every screenshot goes straight to the clipboard. No internet permission.
