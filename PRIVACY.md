# Privacy Policy

**App:** GeoTag Camera (`com.geotagcamera.geotagginglocationonphoto`)
**Developer:** Konko Maji, https://github.com/konkomaji
**Last updated:** 9 November 2026

GeoTag Camera is a camera app that puts the location and time on your photos. This policy explains, in plain terms, what the app does with your data.

The short version: there's no account and no ads, and your photos and their locations stay on your phone unless you share them or turn on one of the optional online features described below. The developer only receives something when you send a problem report yourself.

There are two builds of the app, and they differ in one way:

| | GitHub and F-Droid version | Google Play version |
|---|---|---|
| Usage statistics | None. No analytics code is included at all. | Only if you agree: Google Analytics for Firebase (see [Google Play version: usage statistics](#google-play-version-usage-statistics)). Off until you choose. |
| Location provider | Android's own location services | Google Play services (fused location) |
| Everything else in this policy | Same | Same |

## What the app stores on your phone

All of this stays on your device:

- **Your photos.** Saved to your gallery in Pictures/GeoTagCamera. They're ordinary files you can view, share or delete with any gallery app.
- **A list of your captures.** For each photo: where the file is, when it was taken, the coordinates, altitude, accuracy, compass bearing, address, your organization label, and the hash and signature used for verification.
- **An address cache.** Addresses you've already looked up, so with no signal the stamp can use the nearest address already on the phone.
- **Map tiles**: a cache of up to about 150 MB, plus any areas you saved in Offline maps.
- **Your settings**, and your organization logo if you uploaded one.
- **A signing key**, created on your phone and kept in the Android Keystore. The private part can't be exported.

Android backup is turned off for this app, and the app's data is also excluded from cloud backup and device-to-device transfer, so none of this (including the location history in your capture list and address cache) goes into a Google or device-maker cloud backup or gets copied to a new phone.

## What leaves your phone

Nothing is sent to the developer unless you send a problem report. The only network traffic comes from the features below.

### Stamp map (on by default, can be turned off)

The map on the stamp is drawn on your phone from OpenStreetMap vector tiles downloaded from **OpenFreeMap** (no account, no API key). Each request names a tile by its x, y and zoom numbers. Those correspond to an area a few kilometres across around your location, so OpenFreeMap can work out roughly where you are, and it also sees your IP address. See [openfreemap.org](https://openfreemap.org/).

Tiles are cached on your phone (up to about 150 MB), so the same place isn't downloaded twice. Areas you save under Settings, Offline maps are downloaded in one go and kept until you delete them. Turn the map off in Settings, Stamp fields to stop all map requests.

### Weather (off by default)

The weather chip is off until you turn it on in Settings. If you turn it on, the app sends your latitude and longitude to **Open-Meteo** to get the current temperature and conditions. The coordinates are rounded to two decimal places before they are sent, which is roughly 1 km, so Open-Meteo learns your approximate area but not your exact spot. Open-Meteo also sees your IP address. See the [Open-Meteo terms and privacy information](https://open-meteo.com/en/terms).

### Address lookup

To turn coordinates into an address, the app uses the geocoder built into your phone's Android system. The app does not choose who provides it: on most phones this service is provided by Google Play services and needs an internet connection, which means the coordinates being looked up are sent to Google and handled under Google's privacy policy. The app caches the result so it doesn't need to ask again for the same place. If no geocoder is available or there's no connection, the app uses the nearest cached address or leaves the address off. The Plus Code on the stamp is calculated on the phone and never needs the network.

### Location

The GitHub and F-Droid version gets your location from Android's own location services (GPS, and the phone's network location or fused provider where available) and does not use Google Play services. The Google Play version uses the fused location provider in Google Play services, which Google runs under its own privacy policy. Depending on your phone's location settings, the system may use Wi-Fi and mobile network information to improve accuracy. That is controlled by your phone's settings, not by this app. GeoTag Camera only receives location updates while the camera screen is open and visible. It stops as soon as you leave the camera screen or the app, and it never uses location in the background.

### When you share a photo

When you share a photo, the file you send contains:
- the stamp burned into the image (whatever fields you chose to show),
- EXIF metadata with the exact GPS coordinates, altitude, accuracy, compass direction, time and time-zone offset,
- the verification proof (a hash, a signature and a public key).

Anyone who receives the file can read all of this. If you don't want to share your precise location, don't share the original file.

### Problem reports (only when you send one)

Report a problem (Settings, or About & legal) shows you everything a report will contain before anything is sent. Nothing is sent until you press **Send report**. You can instead send it from your own email app, or post it on GitHub (GitHub issues are public).

**What a report contains:** what you typed; if you leave the box ticked, basic device facts (app version and build, Android version, phone model, CPU type, screen size); if you add one, your email address for a reply; and only if you choose them:
- **Crash details**, from the last time the app closed or froze unexpectedly. These are kept on the phone until you send or delete them; nothing is sent on its own.
- **App log**, the app's own recent messages. The app's log never records coordinates, addresses or what you type, and lines from map libraries that could name a map area are left out.
- **Screenshots** (up to 3) and **one screen recording** (up to 20 MB) that you pick. Screenshots are resized and re-saved on your phone before sending, which removes their hidden metadata, including any GPS location. A video can't be cleaned that way: a screen recording has no location, but a video filmed with a camera may contain where it was filmed.

A report never includes your photos, your capture list or your locations.

**Where it goes:** **Send report** uploads it over an encrypted connection (HTTPS) to a small script on this app's own website (konkomaji.com, hosted by Hostinger). The script emails it to the developer and keeps a copy on the web host, outside the public website, so a report isn't lost if email fails. Stored reports and attachments are deleted automatically after 30 days. Screen recordings are not emailed; they are kept on the host for those 30 days only.

To limit abuse, the script also handles your IP address: it stores only a salted one-way hash of it (for IPv6, of its /64 network), used to limit how many reports can be sent per hour and per day, and deletes it within a day. Your IP address itself is not stored. Reports that look like spam are set aside and not emailed.

To have a report deleted sooner, email hello@konkomaji.com with its reference (shown after sending).

## Website forms

The website (konkomaji.com/geotagcamera) has two forms. Nothing is sent until you press the button.

- **Become a tester:** your email address and, if you add them, your name and phone model. They are kept on the web host, outside the public website, only to invite you to the Google Play test, and an email tells the developer you signed up. They are never shared or used for anything else. Ask at hello@konkomaji.com and your entry is deleted.
- **Send a message:** your email address, optional name and your message, emailed to hello@konkomaji.com so the developer can reply. Messages that look like spam are set aside and not emailed.

To limit abuse, both forms use the same salted, one-way IP hash as problem reports, deleted within a day. The website sets no cookies and loads nothing from other servers. Its download count is fetched by the site's own server, so your browser never contacts GitHub.

## Google Play version: usage statistics

Only the version installed from Google Play has this, and only if you agree. The first time you open the camera it asks; both options start switched off, and "No thanks" means nothing is collected. You can change your choice at any time in Settings, Usage statistics.

- **Usage statistics:** which screens you open and which features you use (for example, which stamp template, whether the map or weather is on, whether a photo was signed), with the app version, Android version, phone model, country and language. This is sent to **Google Analytics for Firebase**, run by Google, and handled under [Google's privacy policy](https://policies.google.com/privacy) and the [Firebase privacy information](https://firebase.google.com/support/privacy). Google keeps this data for up to 14 months.
- **Approximate demographics (separate choice):** if you also turn this on, Google estimates an age range, gender and interests using your phone's advertising ID, and the developer sees these only as totals across many users. You can reset or delete your advertising ID in your phone's settings (Google, Ads, or Privacy, Ads).

**Never sent to analytics:** your photos, coordinates, addresses, place names, notes, organization name, or anything you type. Analytics is never used to show you ads: the app has none, and ad personalization is always switched off.

When analytics is off, the Firebase library included in the Google Play version stays switched off and does not send usage data.

The GitHub and F-Droid versions contain no analytics code at all.

## What we don't do

- No ads.
- No analytics in the GitHub and F-Droid versions. In the Google Play version, only with your consent (above).
- No automatic crash reporting. Crash details are only sent if you choose to send them.
- No account or sign-in.
- No selling of data, and nothing is shared for advertising.
- No background location. Location is only used while the camera screen is visible.

## Permissions

- **Camera:** to take photos.
- **Location:** to stamp and tag photos with where they were taken, only while the camera screen is visible.
- **Internet and network state:** for the stamp map, offline map downloads, the optional weather feature and problem reports you send (and the Android geocoder, which may use it on its own). In the Google Play version also for usage statistics, if you agreed. Network state is used to warn you before a large download on mobile data.
- **Advertising ID (Google Play version only):** used only if you turn on approximate demographics, so Google can estimate them. Never used for ads.
- **Photo location (Android 10 and newer):** so Verify can read the GPS data inside a photo you pick and check it against the signature. Without it Android hides that data from the app.
- **Storage (Android 9 and older only):** to save photos to the gallery.

## Deleting your data

- The **Delete all data** option on the About & legal screen removes the app's capture list, address cache, map tile cache, settings and organization logo. Saved offline map areas are deleted from Settings, Offline maps.
- Uninstalling the app removes all of the above and the signing key.
- Photos already saved to your gallery are yours. They stay until you delete them yourself.

On our side: problem reports are deleted automatically after 30 days, or sooner if you ask. Usage statistics in the Google Play version can be stopped any time in Settings; to have data already collected deleted, email hello@konkomaji.com.

## Children

The app is not directed at children under 13. The GitHub and F-Droid versions don't collect personal data from anyone. The Google Play version only collects usage statistics after an explicit choice in the app, as described above.

## Changes to this policy

If the app's behaviour changes in a way that affects your privacy, this file will be updated in the same release and the change will be noted in the [changelog](CHANGELOG.md). The history of this file is public in the repository.

## Contact

Questions about this policy: email hello@konkomaji.com, or open an issue at https://github.com/konkomaji/geotagcamera/issues. For anything you'd rather not post publicly, use a [private security advisory](https://github.com/konkomaji/geotagcamera/security/advisories/new).
