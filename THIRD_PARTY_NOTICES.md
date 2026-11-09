# Third-party notices

GeoTag Camera itself is licensed under the [GNU General Public License v3.0 or later](LICENSE). It bundles, links to, or uses the third-party works listed below, each under its own license. Full license texts are in the [`LICENSES/`](LICENSES) folder or at the linked URL.

## Fonts bundled in the app

These files are in `app/src/main/res/font/`.

| Font | Files | Copyright | License |
|---|---|---|---|
| [Poppins](https://github.com/itfoundry/Poppins) 4.004 | `poppins_light`, `poppins_regular`, `poppins_medium`, `poppins_semibold`, `poppins_bold` | Copyright 2020 The Poppins Project Authors | [SIL Open Font License 1.1](LICENSES/OFL-1.1.txt) |
| [Roboto Mono](https://github.com/googlefonts/robotomono) 3.001 | `roboto_mono_regular`, `roboto_mono_medium`, `roboto_mono_bold` | Copyright 2015 The Roboto Mono Project Authors | [SIL Open Font License 1.1](LICENSES/OFL-1.1.txt) |

Roboto Mono was released under the Apache License 2.0 before version 3. The version 3.001 files bundled here name the SIL Open Font License in their metadata, so that is the license that applies to them.

The fonts are not sold on their own, and their names are used unchanged. Under the OFL they can be bundled with software under any license.

## Map data and tiles (stamp map)

The stamp map is drawn on the phone from vector tiles downloaded at run time. No map data is bundled in the app.

- **Map data:** © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), available under the [Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/). Every map on a stamp carries a "© OpenStreetMap" credit.
- **Tiles:** [OpenFreeMap](https://openfreemap.org/), © [OpenMapTiles](https://openmaptiles.org/).
- **Style:** OpenFreeMap "Liberty", based on [OSM Liberty](https://github.com/maputnik/osm-liberty).

## Weather data (optional weather chip)

- **Weather data** by [Open-Meteo.com](https://open-meteo.com/), licensed under [Creative Commons Attribution 4.0 International (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/). Open-Meteo combines data from national weather services; see [their attribution list](https://open-meteo.com/en/license).

## Libraries included in the app

| Library | Copyright | License |
|---|---|---|
| [Kotlin standard library](https://github.com/JetBrains/kotlin) | JetBrains s.r.o. and Kotlin Programming Language contributors | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | JetBrains s.r.o. and contributors | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| AndroidX Core, Annotation, Activity, Lifecycle, Navigation | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [Jetpack Compose](https://developer.android.com/jetpack/androidx/releases/compose) (UI, Graphics, Material 3, Tooling) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [Compose Material Icons](https://developer.android.com/jetpack/androidx/releases/compose-material) (`androidx.compose.material:material-icons-extended`) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [CameraX](https://developer.android.com/jetpack/androidx/releases/camera) (core, camera2, lifecycle, view) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [CameraX Extensions](https://developer.android.com/jetpack/androidx/releases/camera) (`androidx.camera:camera-extensions`) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [Room](https://developer.android.com/jetpack/androidx/releases/room) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) (Preferences) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [Material Components for Android](https://github.com/material-components/material-components-android) | The Android Open Source Project | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [Coil](https://github.com/coil-kt/coil) | Coil Contributors | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [MapLibre Native for Android](https://github.com/maplibre/maplibre-native) (`org.maplibre.gl:android-sdk-opengl`) | MapLibre contributors, MapTiler.com, Mapbox | [BSD 2-Clause](LICENSES/MapLibre-BSD-2-Clause.txt). Its own dependencies (GeoJSON/Turf, gestures, Timber) are under Apache 2.0, MIT or BSD |
| [OkHttp](https://github.com/square/okhttp) and [Okio](https://github.com/square/okio) (pulled in by Coil) | Square, Inc. | [Apache 2.0](LICENSES/Apache-2.0.txt) |
| [Google Play services Location](https://developers.google.com/android/guides/setup) (`play-services-location`) | Google LLC | [Android Software Development Kit License](https://developer.android.com/studio/terms) and Google APIs Terms of Service. Not open source |
| [Google Analytics for Firebase](https://firebase.google.com/docs/analytics) (`firebase-analytics`) | Google LLC | [Firebase terms](https://firebase.google.com/terms) and Google APIs Terms of Service. Not open source |

Google Play services Location and Google Analytics for Firebase are proprietary and are only included in the `play` build intended for Google Play, where analytics stays off unless the user agrees. The APKs published on GitHub and F-Droid are the `foss` build, which contains no Google Play services, no analytics and no other non-free component.

CameraX Extensions is only the open-source interface. The Night, HDR, Portrait and other modes themselves are provided by the phone maker's own software already on the device, when the phone has them. Nothing from the phone maker is bundled in the app.

Some of these libraries pull in further AndroidX and Kotlin artifacts, all under Apache 2.0. To list everything that ends up in the APK, run:

```
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

## Build and test tools (not shipped in the app)

| Tool | License |
|---|---|
| Gradle and the Gradle wrapper | Apache 2.0 |
| Android Gradle Plugin, Room compiler, KSP | Apache 2.0 |
| JUnit 4 | Eclipse Public License 1.0 |
| kotlinx-coroutines-test | Apache 2.0 |
| JSON-java (`org.json`) | Public domain |
| AndroidX Test, Espresso, Compose UI Test | Apache 2.0 |
