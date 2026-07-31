# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-07-31

A full UI rebuild against a new design system, plus portable verification.

### Added
- Live WYSIWYG viewfinder: the stamp overlay is drawn by the exact same code that burns into the saved photo, so what you frame is what you get. Long-press and drag the stamp to snap it across nine anchor positions.
- Camera controls: pinch and rail zoom, tap-to-focus, flash auto/on/off, front/back switch, aspect ratio 4:3 / 16:9 / 1:1 (a true center crop), and a rule-of-thirds grid.
- Portable photo verification: capture now embeds the hash, signature and public key in EXIF (mirrored in XMP), so any device with the app can verify any photo, offline, even one it never took. New "Verify a photo" screen and an Android share target ("Share → Verify with GeoTag Camera").
- Post-capture review screen with Share as the primary action, an address-derived filename, and a SIGNED · SHA-256 chip.
- Optional OpenStreetMap map thumbnail (via Stadia Maps) and current-weather chip (via Open-Meteo). Both are off by default, on demand, cached, and never block a capture.
- Redesigned stamp: Card / Bar / Minimal templates, country chip, Plus Code, GMT offset, altitude / accuracy / bearing chips, org label and logo.
- Gallery rebuild with per-photo verified/tampered dots, long-press multi-select batch share, and a new Photo Detail screen that reads metadata from live EXIF.
- Restructured Settings: template and position pickers, four surfaced stamp fields with the rest behind "All 14 fields", org logo upload, and capture defaults.
- Launch screen, first-run permission primer, and an About & legal screen (privacy/data-safety copy, licenses, and a type-to-confirm delete-all-data path).
- New brand mark (lens-frame-and-aperture-dot), Poppins + Roboto Mono typography, and a new accent palette.
- Landscape support: orientation unlocked, with rotation-aware layouts for the capture and review screens.

### Changed
- The app now declares the INTERNET permission for the first time, used only by the optional map-tile and weather features. The core capture flow remains fully offline.
- Verification is no longer limited to the capturing device.

### Notes
- Building from source: the map thumbnail needs your own free Stadia Maps API key in `local.properties` as `stadiaMaps.apiKey`. Without it, the stamp reflows without the map.

## [1.0.0] - 2026-07-31

### Added
- Project scaffold: Gradle build, package structure, adaptive app icon
- Room database for captured photos and offline geocode cache
- Fresh-fix location provider on top of Fused Location Provider
- Offline-first reverse geocoding using the device Geocoder with a local cache fallback
- User-toggleable stamp fields (coordinates, address, timestamp, altitude, accuracy, bearing) with DataStore-backed preferences
- Photo stamp renderer (coordinates, address, timestamp, custom organization label)
- EXIF GPS and timestamp writer
- Tamper-evident photo signing: SHA-256 hash signed with an Android Keystore key generated on-device
- Project website with feature showcase, FAQ and SEO/AEO metadata, deployed via GitHub Pages
- Capture screen: CameraX preview, runtime permission gate, shutter-to-saved-row pipeline (fresh GPS fix, reverse geocode, stamp render, EXIF write, keystore sign)
- Captures publish to the device's real Photos/Gallery app via MediaStore (Pictures/GeoTagCamera) instead of an app-private folder
- Gallery screen: grid of captures with a detail dialog for date, coordinates, address and tamper-check verification
- Settings screen: per-field stamp toggles and organization label, backed by DataStore
- Bottom-nav shell wiring capture, gallery and settings
- Field-worker signature overlay: optional draw-to-sign pad shown after capture, burned into the photo before EXIF write and keystore signing
