# Security

## Where to get EMITT

The only official place to download EMITT is the [Releases page](../../releases) of this repo. If I publish it somewhere else later (F-Droid, Obtainium, Google Play), I'll list it here and in the README myself.

Copies on other sites weren't uploaded by me and may be old or modified. EMITT writes NFC tags and talks to your Flipper over Bluetooth, so please only get it from a source you trust.

## Checking your copy is official

Every official release is signed with my EMITT release key. Its SHA-256 certificate fingerprint is:

```
39:8F:58:73:56:45:89:2D:8F:14:12:EE:77:F6:81:CB:C6:55:27:88:32:36:77:BB:8B:78:49:74:DD:D8:D5:52
```

You can check it two ways:

- **In the app:** Settings → About shows **Official build** with a tick. A copy signed by anyone else shows **Unofficial build** instead.
- **On a computer:** `apksigner verify --print-certs EMITT.apk` and compare the `SHA-256 digest` line with the fingerprint above.

Android also won't let a differently signed copy install over an official one, so if an "update" from somewhere else refuses to install, that's why.

## What EMITT connects to

- **GitHub's public API**, every few hours (you choose how often), to see if there's a newer release. You can turn this off in Settings → Updates. Nothing about you, your codes or your tags is sent.
- **Your Flipper Zero**, over Bluetooth, only when you connect to it.

QR scanning and decoding happen entirely on the phone. There are no analytics, ads or trackers, and EMITT doesn't use Google Play Services.

## Reporting a problem

If you find a security issue, please don't open a public issue. Use GitHub's [private vulnerability reporting](../../security/advisories/new) instead, and I'll get back to you as soon as I can.
