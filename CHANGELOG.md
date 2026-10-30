# Changelog

## 1.1.0

- **Be the tag**: your phone can now act as an NFC tag. Pick it under Create → NFC tag (or on any library item or imported `.nfc` file), and other phones read your link, Wi-Fi, contact and so on by tapping the back of yours while your screen is on. An ongoing notification lets you stop it from anywhere.
- **Floating QR preview**: when you scroll down into Style, a small live copy of the code swoops into the corner so you can see every change. Drag it out of the way or tap it for full screen.
- **Calmer first launch**: the background animation now starts Off for new installs. Every style is still in Settings → Background.
- **Fixed**: the Text size slider no longer gets stuck while dragging. The new size applies when you let go.

## 1.0.0

First build.

- **Create** one payload (text, link, Wi-Fi, contact, email, SMS, phone, location, app) and emit it as a QR code, an NFC tag or a Flipper Zero `.nfc` file.
- **QR styling**: module shapes (square, rounded, dots, fluid, bars, diamond), eye shapes, gradients, custom eye colours, transparent background, centre badge (EMITT mark, text or your own image), error correction and quiet zone.
- **Visibility checks**: every code is test-scanned as you edit, with contrast, inversion, border and density warnings. Full-screen view boosts brightness.
- **Scanner** using the camera or an image, without Google Play Services. Scanned codes can be re-emitted as tags or Flipper files.
- **NFC tools**: write, read/inspect (UID, ATQA/SAK, memory, records, raw pages), copy, erase and lock tags.
- **Flipper Zero**: generate NTAG213/215/216 files, convert tags read by the phone, import `.nfc` files and write their data to real tags, and send files straight to the Flipper over Bluetooth.
- **Library** of saved codes, with a home-screen widget and a Quick Settings tile.
- **Look**: 10 colourways plus fully custom colours, 10 background animations with speed/density/intensity/colour controls, CRT scanlines, vignette and flicker, corner styles, fonts and four launcher icons.
- **Update notifications** when a new release is published on GitHub.
