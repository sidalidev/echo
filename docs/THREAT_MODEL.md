# Threat model

Echo records five seconds of your voice, compresses it like a call and plays it back. It has no internet permission, no account and no server.

**What is protected**

- Your voice never leaves the phone through Echo: without the INTERNET permission, the app cannot open a connection. CI fails if the permission ever appears.
- Takes are never written to storage. They live in memory and are cleared when the app goes to the background.
- Recording starts only when you tap Test me and stops after five seconds at most. Android's microphone indicator shows it.
- The phone's audio mode is restored at the end of every test, including on error.
- Updates must be signed with the same key (fingerprint in SECURITY.md and in About).

**Against whom**: other apps on the phone, a network observer (there is no traffic to observe), and a modified APK (different certificate).

**What is not protected**

- Someone in the room hears you, and someone holding your unlocked phone can play the last take while Echo is open.
- A rooted or compromised phone can read any app's memory, Echo's included.
- A compromise of the developer's GitHub account or signing key. The key is kept outside the repository; moving it to hardware is planned.
- No external audit has been done. Internal reviews are in `docs/security/`.

**Cyber Resilience Act**: Echo is free, open source and not sold, so it is outside the commercial scope.
