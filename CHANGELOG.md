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

### Planned for v1.0.0
- Capture, gallery and settings screens (Jetpack Compose)
- Runtime permission handling
- Field-worker signature overlay UI
