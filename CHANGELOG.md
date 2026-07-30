# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
