#!/usr/bin/env python3
"""Build the GeoTag Camera website (standard library only).

    python site/build.py            build, check, zip
    python site/build.py --no-zip   build and check only

Output:
    site/dist/geotagcamera/          the site, ready for public_html/geotagcamera/
    site/dist/geotagcamera-site.zip  the same, zipped (zip root = folder contents)
    site/dist/github-pages/          tiny redirect site for konkomaji.github.io/geotagcamera/

Privacy, terms, licences and changelog pages are generated from the repository's
markdown and licence files on every build, so they never drift from the app.
"""
import datetime
import hashlib
import html
import json
import re
import shutil
import struct
import sys
import zipfile
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote, urljoin, urlsplit

SITE = Path(__file__).resolve().parent
REPO_ROOT = SITE.parent
DIST = SITE / "dist"
OUT = DIST / "geotagcamera"
PAGES_OUT = DIST / "github-pages"
ZIP_PATH = DIST / "geotagcamera-site.zip"

BASE_URL = "https://konkomaji.com/geotagcamera/"
BASE_PATH = "/geotagcamera/"
REPO_URL = "https://github.com/konkomaji/geotagcamera"
EMAIL = "konkomaji@gmail.com"
YEAR = "2026"


def app_version():
    """versionName from the app's Gradle file, so the site always shows the shipped version."""
    gradle = REPO_ROOT / "app" / "build.gradle.kts"
    m = re.search(r'versionName\s*=\s*"([^"]+)"', gradle.read_text(encoding="utf-8"))
    if not m:
        sys.exit("build.py: could not read versionName from app/build.gradle.kts")
    return m.group(1)


VERSION = app_version()
BUILD_DATE = datetime.date.today().isoformat()
TODAY = datetime.date.today().isoformat()

# --------------------------------------------------------------------------------------
# Content that appears in more than one place
# --------------------------------------------------------------------------------------

# FAQ: rendered on the home page and as FAQPage JSON-LD from this one list.
FAQ = [
    ("Is GeoTag Camera really free, with no ads or subscriptions?",
     "Yes. It's open source under the GPL, version 3 or later, and every feature works without paying. "
     "There are no ads and no trackers in the app, and there won't be."),
    ("Does it work without an internet connection?",
     "Yes. Taking, stamping, signing and verifying photos need no connection. Addresses come from your phone's "
     "built-in geocoder, which usually needs the network, so I cache them on the phone: out of signal, the stamp uses "
     "the nearest address already looked up, and the Plus Code is always worked out on the device. The map works "
     "offline for places you've already shot in, and you can save a whole area before a trip."),
    ("What data does it collect?",
     "None. Nothing is sent to me, because I didn't build a server. Location is only used while the camera screen is "
     "visible. The stamp map downloads OpenStreetMap tiles for your area from OpenFreeMap (no account, no key) and keeps "
     "them on the phone. The weather chip is off by default; if you turn it on, your position is rounded to about 1 km "
     "before it goes to Open-Meteo. Address lookup uses your phone's own geocoder, which on phones with Google services "
     "is usually Google's. App data is kept out of cloud backups and device transfers."),
    ("Do I need Google Play services?",
     "No. Since 1.4.0 the app gets your location from Android's own location services, so it runs on phones without "
     "Google, including de-Googled ones. Without a geocoder on the phone the stamp still shows coordinates and the Plus Code."),
    ("How does the tamper-evident photo feature work?",
     "Every photo's image data is hashed with SHA-256 and signed together with the capture latitude, longitude and time, "
     "using a private key created on your phone in the Android Keystore. The hash, signature and public key are embedded "
     "in the photo, so the app can later check that the pixels, including the burned-in stamp, and the signed location and "
     "time haven't changed. It doesn't prove who took the photo or that the GPS was right, and re-compressing the photo "
     "breaks the check."),
    ("Can I verify a photo that someone else sent me?",
     "Yes. The proof travels inside the photo, so anyone with the app can verify it on any phone, offline. The result shows "
     "a short fingerprint of the signing key and whether it is your own phone's key, so you can compare it with the "
     "fingerprint the sender gives you. Ask for the original file: messaging apps often recompress photos."),
    ("Which APK should I download?",
     "Most phones want the arm64-v8a APK. Older 32-bit phones need armeabi-v7a, Chromebooks and emulators x86_64. "
     "If you're not sure, the universal APK works everywhere but is bigger. The download page has a table and the SHA-256 checks."),
    ("Why won't 1.4.0 install over my older version?",
     "Versions up to 1.3.0 were signed with a development key and 1.4.0 uses the project's permanent key, so Android "
     "treats them as different apps. Uninstall the old version first, then install 1.4.0. Your photos stay in the gallery "
     "and still verify, and Gallery, Import photos re-adds them to the in-app list. Later updates install in place."),
    ("Is it on F-Droid or Google Play?",
     "Not yet. The app is submitted to F-Droid and in review, and IzzyOnDroid is next; the download page will link them "
     "once they're live. The builds are reproducible, so the F-Droid APK will have the same signature as the GitHub one "
     "and you can switch without reinstalling. Until then, install the signed APK from GitHub, or let Obtainium keep it "
     "updated from there. A Play Store release may come later."),
    ("Is the APK really built from the public source code?",
     "Yes, and you can check it. From 1.4.1 the build is reproducible: building a tagged version from the GitHub source "
     "gives a byte-identical APK. A CI job rebuilds every release on Debian with OpenJDK 17 and 21 and compares it with "
     "the published APK, and F-Droid does the same check before it publishes."),
    ("How do I add GPS location and time to photos on Android?",
     "Install GeoTag Camera, allow camera and location, wait for Location locked, and take the photo. The place, address, "
     "coordinates, date and time (and a small map) are burned into the image, and the same location and time are written "
     "to the photo's EXIF data. Photos are saved to your gallery in Pictures/GeoTagCamera."),
    ("Is GeoTag Camera an alternative to GPS Map Camera or Timestamp Camera?",
     "Yes, for the same job: a camera that puts location, address, map and time on the photo. The differences are that "
     "GeoTag Camera is free and open source with no ads, subscriptions or trackers, works without Google Play services, "
     "keeps its map available offline, and signs every photo so edits can be detected later."),
    ("Can I export photo locations to Excel, Google Earth or QGIS?",
     "Yes. In the gallery, choose a project or select photos, then Export locations: CSV opens in Excel and Google Sheets, "
     "KML in Google Earth, and GeoJSON in QGIS or ArcGIS. You can also build a PDF site report with an overview map."),
    ("Who is it built for?",
     "College students documenting fieldwork, land and infrastructure surveyors, inspectors, NGO and government field "
     "staff, and anyone who needs to show exactly where and when a photo was taken. The Field and Professional setups "
     "cover the two ends of that range."),
    ("Can I put my college or organisation's name on the stamp?",
     "Yes, with a logo, a fixed custom line such as a contract number, and a hand-drawn signature. None of it is behind a paywall."),
]

# Screenshots: another process supplies site/assets/screens/N.png. Sizes are read from the
# files when present; captions describe what each one should show.
SCREENS = [
    ("1.png", "Camera", "The camera with the live stamp previewed on the viewfinder"),
    ("2.png", "Stamp", "A just-taken photo with the compact stamp, map thumbnail and Signed badge"),
    ("3.png", "Gallery", "The gallery with a verified badge on each signed photo"),
    ("4.png", "Verify", "Photo details: unaltered since signing, with the signing key fingerprint"),
    ("5.png", "Settings", "Settings with Field or Professional setup and a live stamp preview"),
    ("6.png", "Setup", "First launch: choose Field for quick capture or Professional for every detail"),
]
SCREEN_DEFAULT_SIZE = (1080, 2340)

NAV = [
    ("Features", "{{home}}#features", None),
    ("Verification", "{{home}}#verify", None),
    ("Download", "{{root}}download/", "download/"),
    ("Changelog", "{{root}}changelog/", "changelog/"),
    ("Support", "{{root}}support/", "support/"),
    ("GitHub", REPO_URL, None),
]

PAGES = [
    dict(slug="", src="home.html", name="Home",
         title="GeoTag Camera: free GPS map stamp camera for Android",
         desc="Free, open-source Android camera that stamps GPS location, address, map and time on photos and signs them. "
              "Works offline. No ads or tracking."),
    dict(slug="download/", src="download.html", name="Download",
         title=f"Download GeoTag Camera {VERSION} for Android",
         desc=f"Download the signed GeoTag Camera {VERSION} APK: which file fits your phone, how to check its SHA-256, "
              "install and update steps, and system requirements."),
    dict(slug="changelog/", md="CHANGELOG.md", name="Changelog",
         title="GeoTag Camera changelog: release notes by version",
         desc="Every release of GeoTag Camera and what changed in it, from 1.0.0 to the current version.",
         lead=f"What changed in each release. The current version is {VERSION}. Generated from "
              f"<a href=\"{REPO_URL}/blob/main/CHANGELOG.md\">CHANGELOG.md</a>."),
    dict(slug="support/", src="support.html", name="Support",
         title="GeoTag Camera support: report a problem",
         desc="Report a problem with GeoTag Camera from the app, by email or on GitHub, report a security issue "
              "privately, or help with testing, translations and code."),
    dict(slug="privacy/", md="PRIVACY.md", name="Privacy",
         title="Privacy policy | GeoTag Camera",
         desc="GeoTag Camera collects no data: no account, no server, no ads or analytics. What stays on your phone, "
              "what leaves it, and when.",
         lead="The same policy that ships inside the app, under About &amp; legal."),
    dict(slug="terms/", md="TERMS.md", name="Terms",
         title="Terms of use | GeoTag Camera for Android",
         desc="Terms of use for GeoTag Camera: GPL-3.0-or-later licence, no warranty, and what a verified photo does and doesn't prove.",
         lead="Short, because the app is free software with no account and no service behind it."),
    dict(slug="licenses/", md="THIRD_PARTY_NOTICES.md", name="Licences",
         title="Licences and third-party notices | GeoTag Camera",
         desc="GeoTag Camera's licence (GPL-3.0-or-later) and the fonts, map data and libraries it uses, with full licence texts.",
         lead="GeoTag Camera is licensed under the GNU GPL, version 3 or later. These are the works it builds on, "
              "followed by the full licence texts.",
         heading="Licences and third-party notices"),
]
PAGE_404 = dict(slug="404.html", src="404.html", name="Not found",
                title="Page not found | GeoTag Camera",
                desc="This page doesn't exist. Go to the GeoTag Camera home page, download page or support page.")

# --------------------------------------------------------------------------------------
# Small markdown converter (enough for the repo's own docs)
# --------------------------------------------------------------------------------------

LICENSE_ANCHORS = {"LICENSE": "gpl-3-0"}


def license_anchor(filename):
    stem = Path(filename).stem
    return "license-" + re.sub(r"[^a-z0-9]+", "-", stem.lower()).strip("-")


def map_link(url, slug):
    """Repo-relative links become GitHub URLs, except licence files, which live on our licences page."""
    if re.match(r"^(https?:|mailto:|#)", url):
        return url
    path, _, frag = url.partition("#")
    path = path.lstrip("./") if path.startswith("./") else path
    path = path.rstrip("/")
    target = None
    if path == "LICENSE":
        target = LICENSE_ANCHORS["LICENSE"]
    elif path.startswith("LICENSES/") and path.endswith(".txt"):
        target = license_anchor(path)
    elif path == "LICENSES":
        target = "full-texts"
    if target:
        return f"#{target}" if slug == "licenses/" else "{{root}}licenses/#" + target
    is_dir = (REPO_ROOT / path).is_dir()
    gh = f"{REPO_URL}/{'tree' if is_dir else 'blob'}/main/{path}"
    return gh + (f"#{frag}" if frag else "")


def slugify(text, used):
    s = re.sub(r"<[^>]+>", "", text).strip().lower()
    s = re.sub(r"[^\w\- ]", "", s).replace(" ", "-")
    s = re.sub(r"-{2,}", "-", s).strip("-") or "section"
    base, n = s, 1
    while s in used:
        s = f"{base}-{n}"
        n += 1
    used.add(s)
    return s


def inline(text, slug):
    tokens = []

    def stash(value):
        tokens.append(value)
        return f"\x00{len(tokens) - 1}\x00"

    text = re.sub(r"`([^`]+)`", lambda m: stash(f"<code>{html.escape(m.group(1), quote=False)}</code>"), text)

    def link(m):
        label, url = m.group(1), m.group(2)
        href = map_link(url, slug)
        return stash(f'<a href="{html.escape(href)}">{emphasis(html.escape(label, quote=False))}</a>')

    text = re.sub(r"!?\[([^\]]+)\]\(([^)\s]+)\)", link, text)
    text = html.escape(text, quote=False)

    def autolink(m):
        url = m.group(0)
        trail = ""
        while url and url[-1] in ".,;:":
            trail = url[-1] + trail
            url = url[:-1]
        return stash(f'<a href="{url}">{url}</a>') + trail

    text = re.sub(r"https?://[^\s<>\x00]+", autolink, text)
    text = emphasis(text)
    while "\x00" in text:
        text = re.sub(r"\x00(\d+)\x00", lambda m: tokens[int(m.group(1))], text)
    return text


def emphasis(text):
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<![\*\w])\*(?!\s)(.+?)(?<!\s)\*(?!\*)", r"<em>\1</em>", text)
    return text


LIST_RE = re.compile(r"^(\s*)([-*+]|\d+\.)\s+(.*)$")
TABLE_SEP_RE = re.compile(r"^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$")


def split_row(line):
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|"):
        line = line[:-1]
    return [c.strip() for c in re.split(r"(?<!\\)\|", line)]


def md_to_html(text, slug, changelog=False):
    """Returns (title, body_html, [(level, id, text)])."""
    lines = text.replace("\r\n", "\n").split("\n")
    out, heads, used = [], [], set()
    title = None
    i, n = 0, len(lines)

    def is_block_start(line):
        return (line.startswith("#") or line.startswith("```") or LIST_RE.match(line)
                or line.startswith(">") or re.match(r"^(-{3,}|\*{3,})\s*$", line) or line.startswith("|"))

    while i < n:
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith("```"):
            buf = []
            i += 1
            while i < n and not lines[i].startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            out.append("<pre><code>" + html.escape("\n".join(buf), quote=False) + "</code></pre>")
            continue
        m = re.match(r"^(#{1,6})\s+(.*?)\s*#*$", line)
        if m:
            level, raw = len(m.group(1)), m.group(2)
            i += 1
            if level == 1 and title is None:
                title = re.sub(r"[*`]", "", raw)
                continue
            level = max(2, level)
            cm = re.match(r"^\[?(\d+\.\d+\.\d+)\]?\s*-\s*(\d{4}-\d{2}-\d{2})$", raw) if changelog else None
            if cm:
                hid = "v" + cm.group(1).replace(".", "-")
                used.add(hid)
                label = f'{cm.group(1)} <span class="date">{cm.group(2)}</span>'
                plain = cm.group(1)
            else:
                label = inline(raw, slug)
                hid = slugify(label, used)
                plain = re.sub(r"<[^>]+>", "", label)
            heads.append((level, hid, plain))
            out.append(f'<h{level} id="{hid}">{label}<a class="anchor" href="#{hid}" '
                       f'aria-label="Link to this section">#</a></h{level}>')
            continue
        if re.match(r"^(-{3,}|\*{3,})\s*$", line):
            out.append("<hr>")
            i += 1
            continue
        if line.startswith("|") and i + 1 < n and TABLE_SEP_RE.match(lines[i + 1]):
            header = split_row(line)
            i += 2
            rows = []
            while i < n and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            th = "".join(f'<th scope="col">{inline(c, slug)}</th>' for c in header)
            body = "".join("<tr>" + "".join(f"<td>{inline(c, slug)}</td>" for c in r) + "</tr>" for r in rows)
            out.append(f'<div class="table-wrap"><table><thead><tr>{th}</tr></thead><tbody>{body}</tbody></table></div>')
            continue
        if line.startswith(">"):
            buf = []
            while i < n and lines[i].startswith(">"):
                buf.append(lines[i][1:].strip())
                i += 1
            out.append(f"<blockquote><p>{inline(' '.join(buf), slug)}</p></blockquote>")
            continue
        lm = LIST_RE.match(line)
        if lm:
            ordered = lm.group(2)[-1] == "."
            items = []
            while i < n:
                cur = lines[i]
                lm = LIST_RE.match(cur)
                if lm and (lm.group(2)[-1] == ".") == ordered:
                    items.append(lm.group(3))
                    i += 1
                elif cur.strip() and items and not is_block_start(cur):
                    items[-1] += " " + cur.strip()  # continuation line
                    i += 1
                elif not cur.strip() and i + 1 < n and LIST_RE.match(lines[i + 1]):
                    i += 1  # loose list
                else:
                    break
            tag = "ol" if ordered else "ul"
            out.append(f"<{tag}>" + "".join(f"<li>{inline(it, slug)}</li>" for it in items) + f"</{tag}>")
            continue
        buf = []
        while i < n and lines[i].strip() and not is_block_start(lines[i]):
            buf.append(lines[i].strip())
            i += 1
        if not buf:  # a line that looked like a block start but wasn't one (e.g. a lone '|')
            buf.append(lines[i].strip())
            i += 1
        if len(buf) > 1 and all(b.startswith("**") for b in buf):
            out.append("<p>" + "<br>".join(inline(b, slug) for b in buf) + "</p>")
        else:
            out.append(f"<p>{inline(' '.join(buf), slug)}</p>")
    return title, "\n".join(out), heads


# --------------------------------------------------------------------------------------
# Page shell
# --------------------------------------------------------------------------------------

MARK_SVG = ('<svg viewBox="0 0 200 200" aria-hidden="true" focusable="false">'
            '<path class="mark-frame" fill-rule="evenodd" d="M75,25 L125,25 A50,50 0 0 1 175,75 L175,125 A50,50 0 0 1 125,175 '
            'L75,175 A50,50 0 0 1 25,125 L25,75 A50,50 0 0 1 75,25 Z M75,39 L125,39 A36,36 0 0 1 161,75 L161,125 '
            'A36,36 0 0 1 125,161 L75,161 A36,36 0 0 1 39,125 L39,75 A36,36 0 0 1 75,39 Z"/>'
            '<circle class="mark-dot" cx="100" cy="100" r="18.75"/></svg>')

SUN = ('<svg class="icon theme-light-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4.5"/>'
       '<path d="M12 2v2.5M12 19.5V22M4.2 4.2l1.8 1.8M18 18l1.8 1.8M2 12h2.5M19.5 12H22M4.2 19.8l1.8-1.8M18 6l1.8-1.8"/></svg>')
MOON = ('<svg class="icon theme-dark-icon" viewBox="0 0 24 24" aria-hidden="true">'
        '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/></svg>')


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()[:10]


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def page_url(slug):
    return BASE_URL + ("" if slug == "" else slug)


def header_html(page):
    links = []
    for label, href, slug in NAV:
        cur = ' aria-current="page"' if slug is not None and slug == page["slug"] else ""
        links.append(f'<a href="{href}"{cur}>{label}</a>')
    nav = "\n      ".join(links)
    return f"""<a class="skip-link" href="#main">Skip to content</a>
<header class="site">
  <div class="navbar">
    <a class="brand" href="{{{{home}}}}">{MARK_SVG}<span>GeoTag Camera</span></a>
    <nav class="navlinks" aria-label="Main">
      {nav}
    </nav>
    <div class="navbar-actions">
      <button class="icon-btn js-only" id="themeToggle" type="button" aria-label="Switch theme">{SUN}{MOON}</button>
      <button class="icon-btn menu-btn js-only" id="menuToggle" type="button" aria-label="Open menu" aria-expanded="false" aria-controls="mobilePanel"><span></span></button>
    </div>
  </div>
  <nav class="mobile-panel" id="mobilePanel" data-open="false" aria-label="Main, mobile">
    {nav}
  </nav>
</header>"""


def footer_html():
    return f"""<footer class="site">
  <div class="wrap">
    <div class="foot-grid">
      <div class="foot-brand">
        <a class="brand" href="{{{{home}}}}">{MARK_SVG}<span>GeoTag Camera</span></a>
        <p>An Android camera for photos that need to show where and when they were taken. Free software under the
        <a href="{{{{root}}}}licenses/#gpl-3-0">GPL-3.0-or-later</a>.</p>
      </div>
      <nav aria-labelledby="foot-app">
        <h2 id="foot-app">App</h2>
        <ul>
          <li><a href="{{{{home}}}}">Home</a></li>
          <li><a href="{{{{root}}}}download/">Download</a></li>
          <li><a href="{{{{root}}}}changelog/">Changelog</a></li>
          <li><a href="{{{{root}}}}support/">Support</a></li>
        </ul>
      </nav>
      <nav aria-labelledby="foot-legal">
        <h2 id="foot-legal">Legal</h2>
        <ul>
          <li><a href="{{{{root}}}}privacy/">Privacy policy</a></li>
          <li><a href="{{{{root}}}}terms/">Terms of use</a></li>
          <li><a href="{{{{root}}}}licenses/">Licences</a></li>
        </ul>
      </nav>
      <nav aria-labelledby="foot-project">
        <h2 id="foot-project">Project</h2>
        <ul>
          <li><a href="{REPO_URL}">Source on GitHub</a></li>
          <li><a href="{REPO_URL}/releases">All releases</a></li>
          <li><a href="{REPO_URL}/issues">Issues</a></li>
          <li><a href="{{{{root}}}}support/#security">Report a security issue</a></li>
        </ul>
      </nav>
    </div>
    <div class="foot-bottom">
      <p>No ads, no tracking, no cookies.</p>
      <p class="mono">Version {VERSION}</p>
      <p>&copy; {YEAR} Konko Maji</p>
    </div>
  </div>
</footer>"""


def json_ld(obj):
    text = json.dumps(obj, ensure_ascii=False, indent=2).replace("</", "<\\/")
    return f'<script type="application/ld+json">\n{text}\n</script>'


PERSON = {
    "@type": "Person",
    "@id": "https://konkomaji.com/#person",
    "name": "Konko Maji",
    "url": "https://konkomaji.com/",
    "email": "mailto:konkomaji@gmail.com",
    "sameAs": ["https://github.com/konkomaji"],
}
APP_ID = BASE_URL + "#app"

FEATURES = [
    "GPS location, address, map, date and time stamped on photos",
    "Live stamp preview in the viewfinder",
    "Each photo signed on the device (SHA-256, Android Keystore) so edits are detectable",
    "Verify any GeoTag Camera photo offline",
    "On-device OpenStreetMap map, offline areas",
    "Projects, notes and custom text on the stamp",
    "Export locations as CSV, KML or GeoJSON",
    "PDF site report with overview map",
    "Location and time written to EXIF",
    "No ads, no tracking, no Google Play services",
]


def structured_data(page, screens):
    if page["slug"] == "download/":
        howto = {
            "@context": "https://schema.org",
            "@type": "HowTo",
            "name": "How to install GeoTag Camera on Android",
            "description": "Install the signed GeoTag Camera APK from GitHub on an Android 8.0 or newer phone.",
            "totalTime": "PT3M",
            "tool": [{"@type": "HowToTool", "name": "Android phone with Android 8.0 or newer"}],
            "step": [
                {"@type": "HowToStep", "position": 1, "name": "Pick the APK",
                 "text": "Open the latest release on GitHub and download geotagcamera-v" + VERSION +
                         "-arm64-v8a.apk (most phones), armeabi-v7a (older 32-bit phones) or universal (any phone).",
                 "url": f"{REPO_URL}/releases/latest"},
                {"@type": "HowToStep", "position": 2, "name": "Check the download (optional)",
                 "text": "Compare the file's SHA-256 with the matching .sha256 file on the release page."},
                {"@type": "HowToStep", "position": 3, "name": "Install",
                 "text": "Open the APK on your phone and allow installs from your browser or file manager when Android asks."},
                {"@type": "HowToStep", "position": 4, "name": "Start",
                 "text": "Open GeoTag Camera, choose Field or Professional, and allow camera and location."},
            ],
        }
        crumbs = {
            "@context": "https://schema.org",
            "@type": "BreadcrumbList",
            "itemListElement": [
                {"@type": "ListItem", "position": 1, "name": "GeoTag Camera", "item": BASE_URL},
                {"@type": "ListItem", "position": 2, "name": page["name"], "item": page_url(page["slug"])},
            ],
        }
        return "\n".join(json_ld(x) for x in (howto, crumbs))
    if page["slug"] == "":
        app = {
            "@context": "https://schema.org",
            "@type": ["SoftwareApplication", "MobileApplication"],
            "@id": APP_ID,
            "alternateName": ["GeoTag Camera for Android", "GPS map stamp camera"],
            "keywords": "GPS camera, geotag photos, GPS map camera, timestamp camera, location stamp, field survey photos, "
                        "tamper-evident photos, open source, Android",
            "featureList": FEATURES,
            "fileSize": "15MB",
            "inLanguage": "en",
            "datePublished": "2026-07-30",
            "dateModified": BUILD_DATE,
            "publisher": {"@id": PERSON["@id"]},
            "codeRepository": REPO_URL,
            "name": "GeoTag Camera",
            "operatingSystem": "Android 8.0 or later",
            "applicationCategory": "MultimediaApplication",
            "applicationSubCategory": "Camera",
            "description": PAGES[0]["desc"],
            "softwareVersion": VERSION,
            "url": BASE_URL,
            "downloadUrl": f"{REPO_URL}/releases/latest",
            "installUrl": BASE_URL + "download/",
            "releaseNotes": BASE_URL + "changelog/",
            "image": BASE_URL + "assets/og-image.png",
            "license": "https://spdx.org/licenses/GPL-3.0-or-later.html",
            "isAccessibleForFree": True,
            "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
            "author": PERSON,
            "sameAs": [REPO_URL],
        }
        if screens:
            app["screenshot"] = [BASE_URL + "assets/screens/" + s for s in screens]
        faq = {
            "@context": "https://schema.org",
            "@type": "FAQPage",
            "mainEntity": [{"@type": "Question", "name": q,
                            "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in FAQ],
        }
        site = {"@context": "https://schema.org", "@type": "WebSite", "@id": BASE_URL + "#website",
                "name": "GeoTag Camera", "url": BASE_URL, "inLanguage": "en",
                "description": PAGES[0]["desc"], "publisher": {"@id": PERSON["@id"]},
                "about": {"@id": APP_ID}}
        return "\n".join(json_ld(x) for x in (app, faq, site))
    if page["slug"] == "404.html":
        return ""
    crumbs = {
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        "itemListElement": [
            {"@type": "ListItem", "position": 1, "name": "GeoTag Camera", "item": BASE_URL},
            {"@type": "ListItem", "position": 2, "name": page["name"], "item": page_url(page["slug"])},
        ],
    }
    return json_ld(crumbs)


def shell(page, body, screens, asset_v):
    is404 = page["slug"] == "404.html"
    canonical = page_url(page["slug"])
    base = f'<base href="{BASE_PATH}">\n' if is404 else ""
    robots = "noindex, follow" if is404 else "index, follow"
    t = html.escape(page["title"])
    d = html.escape(page["desc"])
    return f"""<!doctype html>
<html lang="en" class="no-js">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
{base}<title>{t}</title>
<meta name="description" content="{d}">
<meta name="author" content="Konko Maji">
<meta name="robots" content="{robots}">
{"" if is404 else f'<link rel="canonical" href="{canonical}">'}
<meta property="og:type" content="website">
<meta property="og:site_name" content="GeoTag Camera">
<meta property="og:url" content="{canonical}">
<meta property="og:title" content="{t}">
<meta property="og:description" content="{d}">
<meta property="og:image" content="{BASE_URL}assets/og-image.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="GeoTag Camera: every photo, exactly where and when it happened.">
<meta property="og:locale" content="en_IN">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#F5F7F8" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#121417" media="(prefers-color-scheme: dark)">
<meta name="color-scheme" content="light dark">
<link rel="icon" type="image/png" sizes="32x32" href="{{{{root}}}}assets/icons/favicon-32.png">
<link rel="icon" type="image/png" sizes="192x192" href="{{{{root}}}}assets/icons/icon-192.png">
<link rel="apple-touch-icon" href="{{{{root}}}}assets/icons/apple-touch-icon.png">
<link rel="manifest" href="{{{{root}}}}site.webmanifest">
<link rel="stylesheet" href="{{{{root}}}}assets/site.css?v={asset_v['css']}">
<script src="{{{{root}}}}assets/site.js?v={asset_v['js']}"></script>
{structured_data(page, screens)}
</head>
<body>
{header_html(page)}
<main id="main" tabindex="-1">
{body}
</main>
{footer_html()}
</body>
</html>
"""


# --------------------------------------------------------------------------------------
# Page bodies
# --------------------------------------------------------------------------------------

def read_stores():
    js = (SITE / "assets" / "site.js").read_text(encoding="utf-8")
    m = re.search(r"var STORES = (\{.*?\});\s*/\* END STORES \*/", js, re.S)
    if not m:
        sys.exit("build.py: STORES object not found in assets/site.js")
    return json.loads(m.group(1))


def stores_html(stores):
    order = ["github", "fdroid", "izzy", "obtainium"]
    parts = []
    for key in order:
        c = stores[key]
        img_path = SITE / "assets" / "badges" / c["img"]
        w, h = png_size(img_path) or (646, 250)
        if c["live"]:
            parts.append(
                f'<a class="store" data-store="{key}" data-badge-base="{{{{root}}}}assets/badges/" href="{html.escape(c["url"])}">'
                f'<img src="{{{{root}}}}assets/badges/{c["img"]}" alt="{html.escape(c["alt"])}" width="{w}" height="{h}" loading="lazy">'
                f'<span class="store-note">{html.escape(c["note"])}</span></a>')
        else:
            parts.append(
                f'<span class="store is-soon" data-store="{key}" data-badge-base="{{{{root}}}}assets/badges/">'
                f'<img src="{{{{root}}}}assets/badges/{c["img"]}" alt="{html.escape(c["alt"])} (coming soon)" width="{w}" height="{h}" loading="lazy">'
                f'<span class="store-note">Coming soon</span></span>')
    return '<div class="stores">\n  ' + "\n  ".join(parts) + "\n</div>"


def screens_html():
    present = []
    items = []
    for fname, label, alt in SCREENS:
        path = SITE / "assets" / "screens" / fname
        size = png_size(path) if path.exists() else None
        if size:
            present.append(fname)
        w, h = size or SCREEN_DEFAULT_SIZE
        items.append(
            f'<li><figure><img src="{{{{root}}}}assets/screens/{fname}" alt="{html.escape(alt)}" width="{w}" height="{h}" '
            f'loading="lazy" decoding="async"><figcaption>{html.escape(label)}</figcaption></figure></li>')
    block = ('<ul class="shots" aria-label="Screenshots">\n  ' + "\n  ".join(items) + "\n</ul>")
    return block, present


def faq_html():
    out = []
    for idx, (q, a) in enumerate(FAQ):
        opened = " open" if idx == 0 else ""
        out.append(f"      <details{opened}>\n        <summary>{html.escape(q)}</summary>\n"
                   f"        <p>{html.escape(a)}</p>\n      </details>")
    return "\n".join(out)


def mailto():
    subject = "[GeoTag Camera] Problem: "
    body = (f"What happened:\n\n\nWhat I expected:\n\n\nSteps to make it happen:\n1. \n2. \n\n"
            f"App version: {VERSION}\nPhone model and Android version: \n")
    return f"mailto:{EMAIL}?subject={quote(subject)}&body={quote(body)}"


def toc_html(heads):
    h2 = [(hid, text) for level, hid, text in heads if level == 2]
    if len(h2) < 4:
        return ""
    items = "".join(f'<li><a href="#{hid}">{html.escape(text)}</a></li>' for hid, text in h2)
    return f'<nav class="toc" aria-labelledby="toc-title"><p class="eyebrow" id="toc-title">On this page</p><ol>{items}</ol></nav>'


def license_texts_html():
    blocks = [("gpl-3-0", "GNU General Public License v3.0 (GeoTag Camera)", REPO_ROOT / "LICENSE", "LICENSE")]
    for p in sorted((REPO_ROOT / "LICENSES").glob("*.txt")):
        names = {"Apache-2.0": "Apache License 2.0", "OFL-1.1": "SIL Open Font License 1.1",
                 "MapLibre-BSD-2-Clause": "BSD 2-Clause License (MapLibre Native)"}
        blocks.append((license_anchor(p.name), names.get(p.stem, p.stem), p, f"LICENSES/{p.name}"))
    out = ['<h2 id="full-texts">Full licence texts<a class="anchor" href="#full-texts" aria-label="Link to this section">#</a></h2>',
           "<p>Each text below is copied unchanged from the repository at build time.</p>"]
    for anchor, name, path, rel in blocks:
        text = path.read_text(encoding="utf-8").strip("\n")
        out.append(f'<details class="license-block" id="{anchor}"><summary>{html.escape(name)}</summary>'
                   f'<p><a href="{REPO_URL}/blob/main/{rel}">{rel} on GitHub</a></p>'
                   f'<pre class="license-text"><code>{html.escape(text, quote=False)}</code></pre></details>')
    return "\n".join(out)


WEBSITE_NOTICES = """<h2 id="this-website">This website<a class="anchor" href="#this-website" aria-label="Link to this section">#</a></h2>
<p>The website uses the same Poppins and Roboto Mono font files as the app, served from this site under the
<a href="#license-ofl-1-1">SIL Open Font License 1.1</a>. It loads nothing from other servers.</p>
<p>The store badges belong to their projects and are used only to link to those listings: the F-Droid badge is
F-Droid artwork, the IzzyOnDroid badge comes from the IzzyOnDroid repository, the Obtainium badge from the Obtainium
project, and the "Get it on GitHub" badge from Kunzisoft's Github-badge project. GitHub is a trademark of GitHub, Inc.</p>"""


def generated_body(page):
    src = REPO_ROOT / page["md"]
    title, body, heads = md_to_html(src.read_text(encoding="utf-8"), page["slug"],
                                    changelog=page["md"] == "CHANGELOG.md")
    if page["slug"] == "licenses/":
        body += "\n" + WEBSITE_NOTICES + "\n" + license_texts_html()
        heads += [(2, "this-website", "This website"), (2, "full-texts", "Full licence texts")]
    heading = page.get("heading") or title or page["name"]
    lead = page.get("lead", "")
    lead_html = f"<p>{lead}</p>" if lead else ""
    return f"""<div class="wrap page-head">
  <p class="eyebrow">{html.escape(page['name'])}</p>
  <h1>{html.escape(heading)}</h1>
  {lead_html}
</div>
<div class="wrap page-body">
  {toc_html(heads)}
  <div class="prose">
{body}
  </div>
  <p class="source-note">Generated from <a href="{REPO_URL}/blob/main/{page['md']}">{page['md']}</a> in the source repository. Built {TODAY}.</p>
</div>"""


def render(template, root, extra):
    values = {"root": root, "home": root or "./", "version": VERSION,
              "dl": f"{REPO_URL}/releases/download/v{VERSION}/", **extra}
    for _ in range(2):  # values may themselves contain {{root}}
        for k, v in values.items():
            template = template.replace("{{" + k + "}}", v)
    left = re.findall(r"\{\{\w+\}\}", template)
    if left:
        sys.exit(f"build.py: unreplaced placeholders {sorted(set(left))}")
    return template


# --------------------------------------------------------------------------------------
# GitHub Pages redirect site
# --------------------------------------------------------------------------------------

REDIRECT = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>GeoTag Camera has moved to konkomaji.com/geotagcamera</title>
<script>
(function () {
  var p = location.pathname.replace(/^\\/geotagcamera(\\/|$)/, '').replace(/(^|\\/)index\\.html$/, '$1');
  location.replace('https://konkomaji.com/geotagcamera/' + p + location.search + location.hash);
})();
</script>
<meta http-equiv="refresh" content="0; url=https://konkomaji.com/geotagcamera/">
<link rel="canonical" href="https://konkomaji.com/geotagcamera/">
<meta name="viewport" content="width=device-width, initial-scale=1">
</head>
<body>
<p>The GeoTag Camera website has moved to <a href="https://konkomaji.com/geotagcamera/">konkomaji.com/geotagcamera</a>.</p>
</body>
</html>
"""


def build_pages_redirect():
    if PAGES_OUT.exists():
        shutil.rmtree(PAGES_OUT)
    PAGES_OUT.mkdir(parents=True)
    for name in ("index.html", "404.html"):
        (PAGES_OUT / name).write_text(REDIRECT, encoding="utf-8")
    (PAGES_OUT / ".nojekyll").write_text("", encoding="utf-8")


# --------------------------------------------------------------------------------------
# Checks
# --------------------------------------------------------------------------------------

class Scan(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids, self.refs, self.ld, self.errors = set(), [], [], []
        self.title = self.desc = self.canonical = self.base = None
        self._in_title = self._in_ld = self._in_script = False
        self._buf = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            if a["id"] in self.ids:
                self.errors.append(f"duplicate id '{a['id']}'")
            self.ids.add(a["id"])
        if "style" in a:
            self.errors.append(f"inline style attribute on <{tag}> (blocked by CSP)")
        for ev in a:
            if ev.startswith("on"):
                self.errors.append(f"inline event handler {ev} on <{tag}> (blocked by CSP)")
        if tag == "base":
            self.base = a.get("href")
        if tag == "title":
            self._in_title = True
        if tag == "meta" and a.get("name") == "description":
            self.desc = a.get("content")
        if tag == "link" and a.get("rel") == "canonical":
            self.canonical = a.get("href")
        if tag == "img" and not a.get("alt") and a.get("alt") != "":
            self.errors.append(f"<img src={a.get('src')}> without alt")
        if tag == "img" and not (a.get("width") and a.get("height")):
            self.errors.append(f"<img src={a.get('src')}> without width/height")
        if tag == "script":
            if a.get("type") == "application/ld+json":
                self._in_ld, self._buf = True, []
            elif "src" not in a:
                self._in_script = True
        for attr in ("href", "src"):
            if attr in a and tag in ("a", "link", "script", "img", "source"):
                self.refs.append((tag, attr, a[attr], a.get("rel")))

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False
        if tag == "script" and self._in_ld:
            self.ld.append("".join(self._buf))
            self._in_ld = False
        if tag == "script" and self._in_script:
            self.errors.append("inline <script> (blocked by CSP)")
            self._in_script = False

    def handle_data(self, data):
        if self._in_title:
            self.title = (self.title or "") + data
        if self._in_ld:
            self._buf.append(data)


EMOJI_RE = re.compile("[\U0001F000-\U0001FAFF☀-➿⬀-⯿️‍]")


def target_file(path):
    """Map a URL path under /geotagcamera/ to a file in OUT."""
    rel = path[len(BASE_PATH):]
    f = OUT / rel
    if rel == "" or rel.endswith("/"):
        f = f / "index.html"
    return f


def check_site():
    errors, warnings = [], []
    scans = {}
    for f in sorted(OUT.rglob("*.html")):
        rel = f.relative_to(OUT).as_posix()
        s = Scan()
        s.feed(f.read_text(encoding="utf-8"))
        scans[rel] = s
        for e in s.errors:
            errors.append(f"{rel}: {e}")
        for what, val in (("title", s.title), ("meta description", s.desc), ("canonical", s.canonical)):
            if what == "canonical" and rel == "404.html":
                continue  # noindex page: deliberately no canonical
            if not val or not val.strip():
                errors.append(f"{rel}: missing {what}")
        if s.canonical and not s.canonical.startswith(BASE_URL):
            errors.append(f"{rel}: canonical {s.canonical} is not under {BASE_URL}")
        for block in s.ld:
            try:
                json.loads(block)
            except ValueError as e:
                errors.append(f"{rel}: invalid JSON-LD: {e}")
    for rel, s in scans.items():
        page_path = BASE_PATH + rel.replace("index.html", "")
        base = "https://check.invalid" + (s.base or page_path)
        for tag, attr, val, relattr in s.refs:
            if re.match(r"^(mailto:|obtainium:|data:)", val):
                continue
            if val.startswith("http://"):
                errors.append(f"{rel}: insecure URL {val}")
            if tag in ("img", "script", "source") or (tag == "link" and relattr != "canonical"):
                if re.match(r"^https?://", val):
                    errors.append(f"{rel}: external resource {val} (the site must not load anything from other servers)")
                    continue
            url = urljoin(base, val)
            parts = urlsplit(url)
            if parts.netloc == "konkomaji.com" and parts.scheme == "https":
                parts = urlsplit("https://check.invalid" + parts.path + ("#" + parts.fragment if parts.fragment else ""))
            if parts.netloc != "check.invalid":
                continue
            if not parts.path.startswith(BASE_PATH):
                errors.append(f"{rel}: link {val} leaves {BASE_PATH}")
                continue
            tf = target_file(parts.path)
            if not tf.exists():
                if "/assets/screens/" in parts.path:
                    warnings.append(f"{rel}: screenshot not there yet: {val}")
                else:
                    errors.append(f"{rel}: broken link {val} -> {tf.relative_to(OUT) if tf.is_relative_to(OUT) else tf}")
                continue
            if parts.fragment and tf.suffix == ".html":
                trel = tf.relative_to(OUT).as_posix()
                if parts.fragment not in scans[trel].ids:
                    errors.append(f"{rel}: missing anchor #{parts.fragment} in {trel}")
    css = (OUT / "assets" / "site.css").read_text(encoding="utf-8")
    for u in re.findall(r"url\(\"?([^\")]+)\"?\)", css):
        if not u.startswith("data:") and not (OUT / "assets" / u).exists():
            errors.append(f"site.css: missing {u}")
    for f in OUT.rglob("*"):
        if f.suffix in (".html", ".css", ".js", ".txt", ".xml", ".webmanifest") or f.name == ".htaccess":
            text = f.read_text(encoding="utf-8")
            if EMOJI_RE.search(text):
                errors.append(f"{f.relative_to(OUT)}: contains emoji or symbol characters")
            if re.search(r"https?://(fonts\.googleapis|fonts\.gstatic|cdn\.|www\.googletagmanager)", text):
                errors.append(f"{f.relative_to(OUT)}: references a third-party CDN or tracker")
    # sitemap and robots point at real pages
    sm = (OUT / "sitemap.xml").read_text(encoding="utf-8")
    for loc in re.findall(r"<loc>(.*?)</loc>", sm):
        if not target_file(urlsplit(loc).path).exists():
            errors.append(f"sitemap.xml: {loc} has no page")
    return errors, warnings


# --------------------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------------------

def copy_assets():
    dst = OUT / "assets"
    shutil.copytree(SITE / "assets", dst, ignore=shutil.ignore_patterns("README.md", "*.psd", "Thumbs.db", ".DS_Store"))
    for f in (SITE / "static").iterdir():
        if f.suffix == ".txt":
            (OUT / f.name).write_text(render(f.read_text(encoding="utf-8"), "", {}), encoding="utf-8")
        else:
            shutil.copy2(f, OUT / f.name)
    # In-app report endpoint (PHP on the Hostinger site, see site/api/report.php).
    shutil.copytree(SITE / "api", OUT / "api")


def write(rel, text):
    path = OUT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def sitemap():
    urls = []
    for p in PAGES:
        prio = "1.0" if p["slug"] == "" else ("0.8" if p["slug"] in ("download/",) else "0.5")
        urls.append(f"  <url>\n    <loc>{page_url(p['slug'])}</loc>\n    <lastmod>{TODAY}</lastmod>\n"
                    f"    <priority>{prio}</priority>\n  </url>")
    return ('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
            + "\n".join(urls) + "\n</urlset>\n")


def make_zip():
    if ZIP_PATH.exists():
        ZIP_PATH.unlink()
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(OUT.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(OUT).as_posix())


def build(do_zip=True):
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    copy_assets()
    asset_v = {"css": file_hash(OUT / "assets/site.css"), "js": file_hash(OUT / "assets/site.js")}
    stores = stores_html(read_stores())
    screens_block, screens_present = screens_html()
    extra = {"stores": stores, "screens": screens_block, "faq": faq_html(), "mailto": html.escape(mailto())}

    for page in PAGES + [PAGE_404]:
        if "md" in page:
            body = generated_body(page)
        else:
            body = (SITE / "pages" / page["src"]).read_text(encoding="utf-8")
        depth = page["slug"].count("/")
        root = "../" * depth
        full = render(shell(page, body, screens_present, asset_v), root, extra)
        out_rel = page["slug"] + "index.html" if page["slug"].endswith("/") or page["slug"] == "" else page["slug"]
        write(out_rel, full)

    write("sitemap.xml", sitemap())
    build_pages_redirect()

    errors, warnings = check_site()
    for w in warnings:
        print("warning:", w)
    if errors:
        for e in errors:
            print("error:", e)
        sys.exit(f"build.py: {len(errors)} problem(s), no zip written")
    if do_zip:
        make_zip()
    files = sum(1 for f in OUT.rglob("*") if f.is_file())
    print(f"Built GeoTag Camera site {VERSION}: {files} files in {OUT.relative_to(REPO_ROOT)}")
    print(f"Screenshots present: {len(screens_present)}/{len(SCREENS)}")
    if do_zip:
        print(f"Zip: {ZIP_PATH.relative_to(REPO_ROOT)} ({ZIP_PATH.stat().st_size // 1024} KB)")
    print(f"GitHub Pages redirect: {PAGES_OUT.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    build(do_zip="--no-zip" not in sys.argv)
