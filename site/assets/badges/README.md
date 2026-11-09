# Store badges

Downloaded 2026-10-14 with curl from the official sources and checked to be real PNG files.
This README is not copied into the built site (build.py skips it).

| File | Source | Size | Licence / usage terms |
|---|---|---|---|
| `get-it-on-fdroid.png` | https://f-droid.org/badge/get-it-on.png (linked from https://f-droid.org/en/badges/) | 646x250 | F-Droid artwork, made by the F-Droid project for apps listed on F-Droid to link to their listing. The artwork repository (gitlab.com/fdroid/artwork) is understood to be CC-BY-SA 3.0; not re-verified. Only link it once the app is actually on F-Droid. |
| `get-it-on-google-play.png` | https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png | 646x250 | Google's official badge; use per Google Play badge guidelines |
| `get-it-on-github.png` | https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png | 646x250 | From Kunzisoft/Github-badge. The repository declares no licence (GitHub API returns none), so usage rights are not explicit; it is widely used for this purpose. The GitHub logo is a trademark of GitHub, Inc. Replace it with a plain button if that matters. |

Which badges are active is controlled by the `STORES` object at the top of `../site.js`.
