/* GeoTag Camera website script. No network requests, no cookies, no tracking.
   The only thing stored is your light/dark choice, in localStorage, on your device. */

/* Store listings. Flip `live` to true once the app is published there; build.py reads this
   object too, so the static HTML and the script always agree. Keep it valid JSON. */
var STORES = {
  "github": {"live": true, "url": "https://github.com/konkomaji/geotagcamera/releases/latest", "img": "get-it-on-github.png", "alt": "Get it on GitHub", "note": "Signed APKs and SHA-256 checksums"},
  "fdroid": {"live": false, "url": "https://f-droid.org/packages/com.geotagcamera.geotagginglocationonphoto/", "img": "get-it-on-fdroid.png", "alt": "Get it on F-Droid", "note": "Built from source by F-Droid"},
  "play": {"live": false, "url": "https://play.google.com/store/apps/details?id=com.geotagcamera.geotagginglocationonphoto", "img": "get-it-on-google-play.png", "alt": "Get it on Google Play", "note": "Automatic updates through Google Play"}
};
/* END STORES */

(function () {
  var root = document.documentElement;
  root.classList.remove('no-js');
  root.classList.add('js');
  try {
    var stored = localStorage.getItem('theme');
    if (stored === 'dark' || stored === 'light') root.setAttribute('data-theme', stored);
  } catch (e) { /* storage blocked: follow the system setting */ }
})();

document.addEventListener('DOMContentLoaded', function () {
  var root = document.documentElement;

  /* theme toggle */
  var toggle = document.getElementById('themeToggle');
  function currentTheme() {
    return root.getAttribute('data-theme') ||
      (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  }
  function labelToggle() {
    if (!toggle) return;
    var dark = currentTheme() === 'dark';
    toggle.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
    toggle.setAttribute('aria-pressed', dark ? 'true' : 'false');
  }
  if (toggle) {
    labelToggle();
    toggle.addEventListener('click', function () {
      var next = currentTheme() === 'dark' ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try { localStorage.setItem('theme', next); } catch (e) {}
      labelToggle();
    });
  }

  /* mobile menu */
  var menuBtn = document.getElementById('menuToggle');
  var panel = document.getElementById('mobilePanel');
  if (menuBtn && panel) {
    var closeMenu = function () {
      panel.setAttribute('data-open', 'false');
      menuBtn.setAttribute('aria-expanded', 'false');
      menuBtn.setAttribute('aria-label', 'Open menu');
    };
    menuBtn.addEventListener('click', function () {
      var open = panel.getAttribute('data-open') === 'true';
      panel.setAttribute('data-open', open ? 'false' : 'true');
      menuBtn.setAttribute('aria-expanded', open ? 'false' : 'true');
      menuBtn.setAttribute('aria-label', open ? 'Open menu' : 'Close menu');
    });
    panel.querySelectorAll('a').forEach(function (a) { a.addEventListener('click', closeMenu); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && panel.getAttribute('data-open') === 'true') { closeMenu(); menuBtn.focus(); }
    });
    window.addEventListener('resize', function () { if (window.innerWidth > 960) closeMenu(); });
  }

  /* store badges: render from STORES so flipping `live` here is enough */
  document.querySelectorAll('[data-store]').forEach(function (el) {
    var cfg = STORES[el.getAttribute('data-store')];
    if (!cfg) return;
    var isLive = el.tagName === 'A';
    if (isLive === !!cfg.live) return;
    var base = el.getAttribute('data-badge-base') || '';
    var next = document.createElement(cfg.live ? 'a' : 'span');
    next.className = cfg.live ? 'store' : 'store is-soon';
    next.setAttribute('data-store', el.getAttribute('data-store'));
    next.setAttribute('data-badge-base', base);
    if (cfg.live) next.href = cfg.url;
    var img = document.createElement('img');
    img.src = base + cfg.img;
    img.alt = cfg.live ? cfg.alt : cfg.alt + ' (coming soon)';
    img.width = 646; img.height = 250;
    img.setAttribute('loading', 'lazy');
    var note = document.createElement('span');
    note.className = 'store-note';
    note.textContent = cfg.live ? cfg.note : 'Coming soon';
    next.appendChild(img);
    next.appendChild(note);
    el.parentNode.replaceChild(next, el);
  });

  /* open a <details> when the URL points at it or at something inside it */
  function openTarget() {
    if (!location.hash) return;
    var target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
    if (!target) return;
    var d = target.closest('details');
    if (d) { d.open = true; target.scrollIntoView(); }
  }
  openTarget();
  window.addEventListener('hashchange', openTarget);

  /* gentle reveal on scroll */
  var reveal = document.querySelectorAll('.reveal');
  var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduced || !('IntersectionObserver' in window)) {
    reveal.forEach(function (c) { c.classList.add('in-view'); });
    return;
  }
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add('in-view');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12 });
  reveal.forEach(function (c) { observer.observe(c); });
});
