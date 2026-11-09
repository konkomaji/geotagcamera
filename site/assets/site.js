/* GeoTag Camera website script.
   No cookies, no tracking, no third-party requests. The only network call is
   to this site's own api/stats.php (download count and supporters, cached on
   the server). The only thing stored is your light/dark choice, in
   localStorage, on your device. Everything works without this script; it
   only adds the interactive demos and live numbers. */

/* Store listings. Flip `live` to true once the app is published there; build.py reads this
   object too, so the static HTML and the script always agree. Keep it valid JSON. */
var STORES = {
  "github": {"live": true, "url": "https://github.com/konkomaji/geotagcamera/releases/latest", "img": "get-it-on-github.png", "alt": "Get it on GitHub", "note": "Available now: signed APKs"},
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
  var $ = function (s, el) { return (el || document).querySelector(s); };
  var $$ = function (s, el) { return Array.prototype.slice.call((el || document).querySelectorAll(s)); };
  var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var siteRoot = document.body.getAttribute('data-root') || '';
  var fmt = function (n) { try { return new Intl.NumberFormat('en-IN').format(n); } catch (e) { return String(n); } };

  /* ---------- theme ---------- */
  var toggle = $('#themeToggle');
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

  /* ---------- header + mobile menu ---------- */
  var header = $('header.site');
  var onScroll = function () { if (header) header.classList.toggle('scrolled', window.scrollY > 8); };
  window.addEventListener('scroll', onScroll, { passive: true }); onScroll();
  var menuBtn = $('#menuToggle'), panel = $('#mobilePanel');
  if (menuBtn && panel) {
    var setMenu = function (open) {
      panel.setAttribute('data-open', open ? 'true' : 'false');
      menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
      menuBtn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    };
    menuBtn.addEventListener('click', function () { setMenu(panel.getAttribute('data-open') !== 'true'); });
    $$('a', panel).forEach(function (a) { a.addEventListener('click', function () { setMenu(false); }); });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && panel.getAttribute('data-open') === 'true') { setMenu(false); menuBtn.focus(); } });
    window.addEventListener('resize', function () { if (window.innerWidth > 960) setMenu(false); });
  }

  /* ---------- store badges: STORES is the single switch ---------- */
  $$('[data-store]').forEach(function (el) {
    var cfg = STORES[el.getAttribute('data-store')];
    if (!cfg || (el.tagName === 'A') === !!cfg.live) return;
    var next = document.createElement(cfg.live ? 'a' : 'span');
    next.className = cfg.live ? 'store' : 'store is-soon';
    next.setAttribute('data-store', el.getAttribute('data-store'));
    if (cfg.live) next.href = cfg.url;
    var img = el.querySelector('img').cloneNode();
    img.alt = cfg.live ? cfg.alt : cfg.alt + ' (coming soon)';
    var note = document.createElement('span');
    note.className = 'store-note';
    note.textContent = cfg.live ? cfg.note : 'Coming soon';
    next.appendChild(img); next.appendChild(note);
    el.replaceWith(next);
  });

  /* ---------- reveal on scroll ---------- */
  if ('IntersectionObserver' in window && !reduced) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
    }, { rootMargin: '0px 0px -8% 0px' });
    $$('.reveal').forEach(function (el) { io.observe(el); });
  } else {
    $$('.reveal').forEach(function (el) { el.classList.add('in'); });
  }

  /* ---------- stamp builder (home) ---------- */
  var builder = $('#builder');
  if (builder) {
    var stamp = $('.stamp', builder);
    var presets = {
      field: ['place', 'coords', 'time', 'signed', 'brand'],
      pro: ['place', 'address', 'coords', 'time', 'gmt', 'alt', 'acc', 'bearing', 'note', 'map', 'signed', 'brand']
    };
    var setField = function (name, on) {
      $$('[data-field="' + name + '"]', builder).forEach(function (el) { el.hidden = !on; });
      var chip = $('[data-toggle="' + name + '"]', builder);
      if (chip) chip.setAttribute('aria-pressed', on ? 'true' : 'false');
      var meta = $('.st-meta', builder);
      if (meta) meta.hidden = !$$('.st-meta [data-field]', builder).some(function (c) { return !c.hidden; });
    };
    $$('[data-toggle]', builder).forEach(function (chip) {
      chip.addEventListener('click', function () {
        setField(chip.getAttribute('data-toggle'), chip.getAttribute('aria-pressed') !== 'true');
        $$('[data-preset]', builder).forEach(function (b) { b.setAttribute('aria-pressed', 'false'); });
      });
    });
    var applyPreset = function (name) {
      var on = presets[name];
      $$('[data-toggle]', builder).forEach(function (chip) { var f = chip.getAttribute('data-toggle'); setField(f, on.indexOf(f) >= 0); });
      $$('[data-preset]', builder).forEach(function (b) { b.setAttribute('aria-pressed', b.getAttribute('data-preset') === name ? 'true' : 'false'); });
    };
    $$('[data-preset]', builder).forEach(function (b) { b.addEventListener('click', function () { applyPreset(b.getAttribute('data-preset')); }); });
    $$('[data-template]', builder).forEach(function (b) {
      b.addEventListener('click', function () {
        stamp.classList.toggle('minimal', b.getAttribute('data-template') === 'minimal');
        $$('[data-template]', builder).forEach(function (x) { x.setAttribute('aria-pressed', x === b ? 'true' : 'false'); });
      });
    });
    $$('[data-pos]', builder).forEach(function (b) {
      b.addEventListener('click', function () {
        stamp.classList.toggle('top', b.getAttribute('data-pos') === 'top');
        $$('[data-pos]', builder).forEach(function (x) { x.setAttribute('aria-pressed', x === b ? 'true' : 'false'); });
      });
    });
    var bearing = $('#bearing', builder), needle = $('.compass svg', builder);
    var dirs = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    var setBearing = function () {
      var v = parseInt(bearing.value, 10) || 0;
      var label = v + '° ' + dirs[Math.round(v / 45) % 8];
      $$('[data-out="bearing"]', builder).forEach(function (o) { o.textContent = label; });
      if (needle) needle.style.transform = 'rotate(' + (-v) + 'deg)';
    };
    if (bearing) { bearing.addEventListener('input', setBearing); setBearing(); }
    applyPreset('pro');
  }

  /* ---------- verify demo (home) ---------- */
  var proof = $('#proofDemo');
  if (proof) {
    var shot = $('.proof-shot', proof), status = $('.proof-status', proof);
    var title = $('[data-proof="title"]', proof), detail = $('[data-proof="detail"]', proof);
    var nowHash = $('[data-proof="now"]', proof), signedHash = $('[data-proof="signed"]', proof);
    var gps = $('[data-proof="gps"]', proof);
    // Not SHA-256: a small stand-in so the demo can show "same input, same hash; any change, different hash".
    var demoHash = function (s) {
      var out = '', h = 2166136261;
      for (var r = 0; r < 8; r++) {
        for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i) + r; h = Math.imul(h, 16777619) >>> 0; }
        out += ('00000000' + h.toString(16)).slice(-8);
      }
      return out;
    };
    var original = demoHash('pixels:original|23.221300,88.366000|2026-11-09T10:42');
    signedHash.textContent = original.slice(0, 32) + '…';
    var render = function () {
      var edits = $$('[data-edit][aria-pressed="true"]', proof).map(function (b) { return b.getAttribute('data-edit'); });
      shot.classList.toggle('bright', edits.indexOf('bright') >= 0);
      shot.classList.toggle('cropped', edits.indexOf('crop') >= 0);
      var where = edits.indexOf('gps') >= 0 ? '22.572600, 88.363900' : '23.221300, 88.366000';
      if (gps) gps.textContent = where;
      var st = $('[data-proof="stamp"]', proof); if (st) st.textContent = where + ' \u00B7 09/11/2026 10:42';
      var now = edits.length ? demoHash('pixels:' + edits.join(',') + '|x') : original;
      nowHash.textContent = now.slice(0, 32) + '…';
      nowHash.classList.toggle('diff', edits.length > 0);
      var bad = edits.length > 0;
      status.classList.toggle('bad', bad);
      title.textContent = bad ? 'Edited after signing' : 'Unaltered since signing';
      detail.textContent = bad
        ? (edits.indexOf('gps') >= 0 && edits.length === 1 ? 'The location in the file no longer matches what was signed.' : 'The image no longer matches its signature.')
        : 'Pixels, GPS location and time match the signature exactly.';
      if (!reduced) { status.classList.remove('flash'); void status.offsetWidth; status.classList.add('flash'); }
    };
    $$('[data-edit]', proof).forEach(function (b) {
      b.addEventListener('click', function () { b.setAttribute('aria-pressed', b.getAttribute('aria-pressed') === 'true' ? 'false' : 'true'); render(); });
    });
    var reset = $('[data-proof-reset]', proof);
    if (reset) reset.addEventListener('click', function () { $$('[data-edit]', proof).forEach(function (b) { b.setAttribute('aria-pressed', 'false'); }); render(); });
    render();
  }

  /* ---------- screens carousel ---------- */
  var shots = $('.shots');
  if (shots) {
    $$('[data-shots]').forEach(function (b) {
      b.addEventListener('click', function () {
        var step = shots.querySelector('li') ? shots.querySelector('li').getBoundingClientRect().width + 24 : 280;
        shots.scrollBy({ left: b.getAttribute('data-shots') === 'next' ? step : -step, behavior: reduced ? 'auto' : 'smooth' });
      });
    });
  }

  /* ---------- APK picker (download) ---------- */
  var picker = $('#apkPicker');
  if (picker) {
    var base = picker.getAttribute('data-dl'), ver = picker.getAttribute('data-version');
    var out = $('#apkResult');
    var choose = function (btn) {
      $$('[data-abi]', picker).forEach(function (b) { b.setAttribute('aria-pressed', b === btn ? 'true' : 'false'); });
      var abi = btn.getAttribute('data-abi'), file = 'geotagcamera-v' + ver + '-' + abi + '.apk';
      $('[data-apk="name"]', out).textContent = file;
      $('[data-apk="link"]', out).href = base + file;
      $('[data-apk="sum"]', out).href = base + file + '.sha256';
      $('[data-apk="why"]', out).textContent = btn.getAttribute('data-why');
      $$('[data-apk-file]').forEach(function (c) { c.textContent = file; });
    };
    $$('[data-abi]', picker).forEach(function (b) { b.addEventListener('click', function () { choose(b); }); });
    choose($('[data-abi="arm64-v8a"]', picker));
  }

  /* ---------- tabs ---------- */
  $$('.tabs').forEach(function (t) {
    var tabs = $$('[role="tab"]', t);
    var show = function (tab) {
      tabs.forEach(function (x) {
        var on = x === tab;
        x.setAttribute('aria-selected', on ? 'true' : 'false');
        x.tabIndex = on ? 0 : -1;
        var p = document.getElementById(x.getAttribute('aria-controls'));
        if (p) p.hidden = !on;
      });
    };
    tabs.forEach(function (tab, i) {
      tab.addEventListener('click', function () { show(tab); });
      tab.addEventListener('keydown', function (e) {
        var d = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
        if (d) { var n = tabs[(i + d + tabs.length) % tabs.length]; show(n); n.focus(); }
      });
    });
    var pre = tabs.filter(function (x) { return x.getAttribute('aria-selected') === 'true'; })[0] || tabs[0];
    var ua = navigator.userAgent || '';
    var guess = /Windows/.test(ua) ? 'win' : /Mac OS X/.test(ua) && !/Mobile/.test(ua) ? 'mac' : /Linux/.test(ua) && !/Android/.test(ua) ? 'linux' : null;
    if (guess) { var g = $('[data-os="' + guess + '"]', t); if (g) pre = g; }
    show(pre);
  });

  /* ---------- copy buttons ---------- */
  $$('pre').forEach(function (pre) {
    if (!navigator.clipboard || pre.closest('.no-copy')) return;
    var wrap = document.createElement('div'); wrap.className = 'copy-wrap';
    pre.parentNode.insertBefore(wrap, pre); wrap.appendChild(pre);
    var b = document.createElement('button'); b.type = 'button'; b.className = 'copy-btn'; b.textContent = 'Copy';
    b.addEventListener('click', function () {
      navigator.clipboard.writeText(pre.innerText.trim()).then(function () { b.textContent = 'Copied'; setTimeout(function () { b.textContent = 'Copy'; }, 1600); });
    });
    wrap.appendChild(b);
  });

  /* ---------- TOC highlight (generated pages) ---------- */
  var tocLinks = $$('.toc a');
  if (tocLinks.length && 'IntersectionObserver' in window) {
    var map = {};
    tocLinks.forEach(function (a) { map[a.getAttribute('href').slice(1)] = a; });
    var tio = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { tocLinks.forEach(function (a) { a.classList.remove('active'); }); var a = map[e.target.id]; if (a) a.classList.add('active'); }
      });
    }, { rootMargin: '-80px 0px -70% 0px' });
    Object.keys(map).forEach(function (id) { var h = document.getElementById(id); if (h) tio.observe(h); });
  }

  /* ---------- screens viewer: tabs + gentle autoplay ---------- */
  var sv = $('#screenViewer');
  if (sv) {
    var svTabs = $$('[role="tab"]', sv), bar = $('.sv-progress', sv), DUR = 5200, timer = null, userTook = false, idx = 0;
    var caption = document.createElement('p'); caption.className = 'sv-caption'; caption.setAttribute('aria-hidden', 'true');
    $('.sv-stage', sv).appendChild(caption);
    var showShot = function (i, focus) {
      idx = (i + svTabs.length) % svTabs.length;
      svTabs.forEach(function (t, k) {
        var on = k === idx;
        t.setAttribute('aria-selected', on ? 'true' : 'false');
        t.tabIndex = on ? 0 : -1;
        var p = document.getElementById(t.getAttribute('aria-controls'));
        if (p) { p.hidden = !on; var img = p.querySelector('img'); if (on && img && img.loading === 'lazy') img.loading = 'eager'; }
      });
      caption.textContent = $('.st-text span', svTabs[idx]).textContent;
      if (focus) svTabs[idx].focus();
      if (window.innerWidth <= 860) svTabs[idx].scrollIntoView({ block: 'nearest', inline: 'center', behavior: reduced ? 'auto' : 'smooth' });
    };
    var stop = function () { userTook = true; clearTimeout(timer); if (bar) bar.classList.remove('run'); };
    var tick = function () {
      if (userTook || reduced) return;
      if (bar) { bar.classList.remove('run'); void bar.offsetWidth; bar.style.setProperty('--sv-dur', DUR + 'ms'); bar.classList.add('run'); }
      timer = setTimeout(function () { showShot(idx + 1); tick(); }, DUR);
    };
    svTabs.forEach(function (t, k) {
      t.addEventListener('click', function () { stop(); showShot(k); });
      t.addEventListener('keydown', function (e) {
        var d = (e.key === 'ArrowDown' || e.key === 'ArrowRight') ? 1 : (e.key === 'ArrowUp' || e.key === 'ArrowLeft') ? -1 : 0;
        if (d) { e.preventDefault(); stop(); showShot(k + d, true); }
      });
    });
    sv.addEventListener('mouseenter', function () { clearTimeout(timer); if (bar) bar.classList.remove('run'); });
    sv.addEventListener('mouseleave', function () { if (!userTook) tick(); });
    showShot(0);
    if ('IntersectionObserver' in window && !reduced) {
      var svo = new IntersectionObserver(function (en) {
        en.forEach(function (e) { if (e.isIntersecting && !userTook) { tick(); svo.disconnect(); } });
      }, { threshold: .35 });
      svo.observe(sv);
    }
  }

  /* ---------- real-world problems: reveal + filter ---------- */
  var issues = $$('.issue');
  if (issues.length) {
    issues.forEach(function (card) {
      var btn = $('.issue-toggle', card), fix = $('.issue-fix', card);
      fix.hidden = true;
      btn.addEventListener('click', function () {
        var open = btn.getAttribute('aria-expanded') === 'true';
        btn.setAttribute('aria-expanded', open ? 'false' : 'true');
        btn.textContent = open ? 'How GeoTag Camera handles it' : 'Hide the answer';
        fix.hidden = open; card.classList.toggle('open', !open);
        if (!open) { fix.classList.remove('show'); void fix.offsetWidth; fix.classList.add('show'); }
      });
    });
    $$('[data-filter]').forEach(function (f) {
      f.addEventListener('click', function () {
        var cat = f.getAttribute('data-filter');
        $$('[data-filter]').forEach(function (x) { x.setAttribute('aria-pressed', x === f ? 'true' : 'false'); });
        issues.forEach(function (c) { c.hidden = cat !== 'all' && c.getAttribute('data-cat') !== cat; });
      });
    });
    var all = $('[data-reveal-all]');
    if (all) all.addEventListener('click', function () {
      var opening = all.getAttribute('data-open') !== 'true';
      all.setAttribute('data-open', opening ? 'true' : 'false');
      all.textContent = opening ? 'Hide the answers' : 'Show every answer';
      issues.forEach(function (c) {
        var btn = $('.issue-toggle', c), fix = $('.issue-fix', c);
        btn.setAttribute('aria-expanded', opening ? 'true' : 'false');
        btn.textContent = opening ? 'Hide the answer' : 'How GeoTag Camera handles it';
        fix.hidden = !opening; c.classList.toggle('open', opening);
      });
    });
  }

  /* ---------- website forms (tester signup, contact) ---------- */
  $$('form[data-form]').forEach(function (form) {
    var t = $('[data-form-time]', form); if (t) t.value = String(Math.floor(Date.now() / 1000));
    var status = $('.form-status', form), btn = $('button[type="submit"]', form);
    form.addEventListener('submit', function (e) {
      if (!window.fetch || !window.FormData) return;
      e.preventDefault();
      status.className = 'form-status'; status.textContent = 'Sending...';
      btn.disabled = true;
      fetch(form.action, { method: 'POST', body: new FormData(form), headers: { 'X-GeoTag-Client': 'web' }, credentials: 'omit' })
        .then(function (r) { return r.json().catch(function () { return { ok: false, msg: 'Something went wrong. Please email hello@konkomaji.com.' }; }); })
        .then(function (d) {
          status.className = 'form-status ' + (d.ok ? 'ok' : 'err');
          status.textContent = d.msg;
          if (d.ok) { form.reset(); if (t) t.value = String(Math.floor(Date.now() / 1000)); }
        })
        .catch(function () { status.className = 'form-status err'; status.textContent = 'No connection. Please try again, or email hello@konkomaji.com.'; })
        .then(function () { btn.disabled = false; });
    });
  });
  // Without JavaScript the server redirects back with ?sent= or ?error=; show that here too.
  (function () {
    var q = new URLSearchParams(location.search), sent = q.get('sent'), err = q.get('error');
    if (!sent && !err) return;
    var form = $(sent === 'tester' || location.hash === '#beta' ? '#beta form' : '#contact form');
    if (!form) return;
    var st = $('.form-status', form);
    st.className = 'form-status ' + (sent ? 'ok' : 'err');
    st.textContent = sent ? (sent === 'tester' ? "You're on the list. We'll email you when the test opens." : 'Thanks, your message was sent.') : err;
  })();

  /* ---------- live numbers from this site's own api/stats.php ---------- */
  if (!$('[data-stat]') && !$('#wall')) return;
  var animateTo = function (el, n) {
    if (reduced || n < 20) { el.textContent = fmt(n); return; }
    var start = null, from = 0, dur = 900;
    var stepFn = function (t) {
      if (!start) start = t;
      var p = Math.min(1, (t - start) / dur), e = 1 - Math.pow(1 - p, 3);
      el.textContent = fmt(Math.round(from + (n - from) * e));
      if (p < 1) requestAnimationFrame(stepFn);
    };
    requestAnimationFrame(stepFn);
  };
  fetch(siteRoot + 'api/stats.php', { credentials: 'omit' }).then(function (r) { return r.ok ? r.json() : null; }).then(function (d) {
    if (!d) return;
    $$('[data-stat="downloads"]').forEach(function (el) { if (typeof d.downloads === 'number') { animateTo(el, d.downloads); var box = el.closest('[data-live]'); if (box) { box.hidden = false; box.classList.add('live'); } } });
    $$('[data-stat="stars"]').forEach(function (el) { if (typeof d.stars === 'number') animateTo(el, d.stars); });
    $$('[data-stat="released"]').forEach(function (el) { if (d.released) el.textContent = d.released; });
    var s = d.supporters, wall = $('#wall');
    if (!s || !wall) return;
    if (!(s.coffees > 0)) return;
    $$('[data-thanks-empty]').forEach(function (el) { el.hidden = true; });
    $$('[data-thanks-live]').forEach(function (el) { el.hidden = false; });
    $$('[data-stat="coffees"]').forEach(function (el) { animateTo(el, s.coffees); });
    $$('[data-stat="supporters"]').forEach(function (el) { animateTo(el, s.count || 0); });
    $$('[data-stat="coffees-word"]').forEach(function (el) { el.textContent = s.coffees === 1 ? 'coffee' : 'coffees'; });
    $$('[data-stat="supporters-word"]').forEach(function (el) { el.textContent = s.count === 1 ? 'supporter' : 'supporters'; });
    var list = $('.notes', wall);
    (s.messages || []).forEach(function (m) {
      var li = document.createElement('li');
      var q = document.createElement('blockquote'); q.textContent = m.note;
      var who = document.createElement('p'); who.className = 'note-by'; who.textContent = m.name;
      li.appendChild(q); li.appendChild(who); list.appendChild(li);
    });
    list.hidden = !list.children.length;
  }).catch(function () { /* numbers stay at their static fallback */ });
});
