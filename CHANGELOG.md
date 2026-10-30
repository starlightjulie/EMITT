# Changelog

## 1.3.1

### Fixed
- **Be the tag on phones with a built-in NFC tag.** Some phones (like the Moto G 5G 2024 on Android 15) have an empty tag built into their NFC chip, and the chip sends readers there instead of to EMITT. Apps can't override that. EMITT now also answers on its own ID, and EMITT's reader tries it, so a phone with EMITT open always reads an EMITT phone. Tag mode explains this if the other phone sees nothing.

### New
- **Saved colour sets.** "Use my own colours" now has its own page (Appearance → Use my own colours). Edit your five colours, save them as a named set, tap a set to switch to it, and delete sets you don't want. Reset look keeps your sets.
- **Comets** background: a twinkling starfield with comets streaking across. It replaces Synth Grid.
- **Pulsar** background: stacked ridgelines that swell and drift like a pulsar's radio trace. It replaces Static.
- **Smoother navigation.** The bottom bar's indicator slides to the tab you pick, the `EMITT://` path backspaces and types the new screen's name, and screens fade and slide in the direction you're going. All of it turns off with Android's "Remove animations" setting.

If you were using Synth Grid or Static, EMITT switches you to Comets or Pulsar and keeps the rest of your look.

## 1.3.0

### New
- **23 languages.** EMITT now speaks English, 简体中文, हिन्दी, Español, Français, العربية, বাংলা, Português (Brasil), Русский, اردو, Bahasa Indonesia, Deutsch, 日本語, Naijá (Nigerian Pidgin), العربية المصرية, मराठी, తెలుగు, Türkçe, தமிழ், 粵語（香港）, Italiano, Tiếng Việt and 한국어. Pick one in Settings → Language, or follow your phone. Arabic and Urdu get a full right-to-left layout. Flipper menu paths and chip names stay in English so they match what's on your Flipper.
- **Appearance has its own page.** Tap the palette icon next to the cog for colourways, background, interface and app icon. Settings now holds Language, Updates, Vibration and About.
- **Six new colourways**: Midnight, Sakura, Forest, Ember, Coffee and Glacier (16 in total).
- **Six new app icons**: Mono, Sakura, Redline, Ultraviolet, Paper and Glacier (10 in total).

### Changed
- New installs start in the **Mono** colourway. Your current look is kept when you update.
- Links and other Latin text in input boxes read left to right even when the app is in Arabic or Urdu.

## 1.2.0

### Fixed
- **Flipper Bluetooth now connects.** EMITT finds Flippers by their Bluetooth service instead of the name "Flipper", so renamed Flippers (Momentum and other custom firmware) show up. It connects to the address the Flipper is actually advertising, not a stale pairing, and tells you when another app (like the official Flipper app) is holding the connection.
- **Flippers can read your phone in tag mode.** EMITT now accepts the ISO 7816 "select file under current application" command the Flipper uses, which it used to reject.
- The floating QR preview no longer pops up while you're typing. It only shows while the Style panel is on screen.
- Flipper file names show in their real case.

### New
- **Tag mode**: Be the tag now opens a full-screen view that keeps the screen on and EMITT in front, with a big pulse, your payload and a read counter. Some phones (like Samsung's with "Embedded tag") only let EMITT answer readers while it's on screen. Minimise it to the bottom bar any time.
- **Vibration patterns**: pick Tick, Double tap, Triple, Heartbeat, Long buzz, Rising, Falling, Purr or SOS (or Off) separately for tag writes/reads, for being read as a tag, and for failures, with a strength slider. Settings → Vibration.
- Tips in the NFC tag output for using a Flipper: emulate an NTAG to test Write to tag, and use Read Specific Card Type → Type 4 Tag to read your phone (plain Read also wakes your wallet app).

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
