# Threat model

Clipshot copies each new screenshot to the clipboard. It has no internet permission, no account and no server.

**What is protected**

- Your screenshots and settings never leave the phone through Clipshot: without the INTERNET permission, the app cannot open a connection. CI fails if the permission ever appears.
- Other apps cannot read Clipshot's private copies: the provider is not exported, and the only way in is the one-off grant the clipboard gives to the app you paste into.
- The Capture accessibility service cannot read the screen: no events, no window content, no gestures, no key filtering. A test guards the declaration.
- Gallery removal only deletes untouched files saved by a system screenshot app, one minute after the copy.
- Updates must be signed with the same key (fingerprint in SECURITY.md and in About).

**Against whom**: other apps on the phone, a network observer (there is no traffic to observe), and a modified APK (different certificate).

**What is not protected**

- The clipboard: the focused app and the keyboard can read it, as with anything you copy. Some keyboards sync their clipboard history to a cloud.
- Someone who unlocks your phone sees what you see: the gallery, the clipboard, the last copy in Clipshot.
- The copies on disk rely on Android's file encryption and app sandbox, not on encryption by Clipshot. A rooted or compromised phone can read them.
- A compromise of the developer's GitHub account or signing key. The key is kept outside the repository; moving it to hardware is planned.
- No external audit has been done. Internal reviews are in `docs/security/`.

**Cyber Resilience Act**: Clipshot is free, open source and not sold, so it is outside the commercial scope.
