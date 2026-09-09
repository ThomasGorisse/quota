(() => {
  'use strict';

  const $ = (selector) => document.querySelector(selector);
  const storageKey = 'quota.token';
  const demo = new URLSearchParams(location.search).get('demo') === '1';
  const providerNames = { claude: 'Claude', codex: 'Codex' };
  const windows = { claude: ['session', 'weekly'], codex: ['session', 'weekly', 'reserve'] };
  const labels = { session: '5 h', weekly: 'Week', reserve: 'Reserve' };
  let token = '';
  let snapshot = null;
  let receivedAt = 0;
  let request = null;
  let failure = '';
  let authFailed = false;
  let demoSnapshot;
  const cards = new Map();

  function readStoredToken() {
    try { return localStorage.getItem(storageKey) || ''; } catch { return ''; }
  }

  function currentToken() {
    const fragment = new URLSearchParams(location.hash.slice(1));
    const value = fragment.has('t') ? fragment.get('t').trim() : readStoredToken();
    if (value) {
      try { localStorage.setItem(storageKey, value); } catch { /* Pairing still works for this visit. */ }
    }
    return value;
  }

  // Same GET /v1/status shape as README.md. Relative dates keep the demo useful
  // tomorrow, too; the sample values and history are deterministic, not random.
  function makeDemo() {
    const now = Date.now();
    const iso = (seconds) => new Date(now + seconds * 1000).toISOString();
    const series = [2, 3, 3, 6, 9, 13, 13, 19, 24, 28, 28, 32, 39, 41, 47, 53, 53, 57, 62, 69, 75, 3, 7, 11];
    return {
      collected_at: iso(-42), age_seconds: 42, stale: false,
      providers: {
        claude: { plan: 'max', windows: [
          { id: 'session', label: '5 h', percent: 11, resets_at: iso(9000) },
          { id: 'weekly', label: 'Week', percent: 60, resets_at: iso(240000) }
        ], extra: { extra_usage_enabled: false } },
        codex: { plan: 'plus', windows: [
          { id: 'session', label: '5 h', percent: 12, resets_at: iso(8227) },
          { id: 'weekly', label: 'Week', percent: 6, resets_at: iso(505388) },
          { id: 'reserve', label: 'Reserve', percent: 10, resets_at: iso(339414) }
        ], extra: { astra_available: true, limit_reached: false } }
      },
      severity: 'warn',
      history: series.map((value, i) => ({
        t: iso(-42 - (23 - i) * 3600),
        'claude.session': value,
        'claude.weekly': Math.round(31 + i * 29 / 23),
        'codex.session': Math.round(value * 12 / 11),
        'codex.weekly': Math.round(i * 6 / 23)
      }))
    };
  }

  function severity(used) { return used > 85 ? 'critical' : used >= 60 ? 'warn' : 'ok'; }
  function validPercent(value) { return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 100; }
  function ageSeconds() {
    const collectorAge = (Date.now() - Date.parse(snapshot.collected_at)) / 1000;
    const serverAge = Number.isFinite(snapshot.age_seconds) ? snapshot.age_seconds + (Date.now() - receivedAt) / 1000 : 0;
    return Math.max(0, collectorAge, serverAge);
  }
  function resetText(at) {
    const time = Date.parse(at);
    if (!Number.isFinite(time)) return 'Reset time unavailable';
    if (time <= Date.now()) return 'Reset due · awaiting update';
    const minutes = Math.ceil((time - Date.now()) / 60000);
    if (minutes < 60) return `resets in ${minutes} min`;
    if (minutes < 1440) return `resets in ${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
    return `resets in ${Math.floor(minutes / 1440)} d ${Math.floor(minutes % 1440 / 60)} h`;
  }
  function elapsedText(age) {
    if (age < 60) return `updated ${Math.floor(age)} s ago`;
    if (age < 3600) return `updated ${Math.floor(age / 60)} min ago`;
    if (age < 86400) return `updated ${Math.floor(age / 3600)} h ago`;
    return `updated ${Math.floor(age / 86400)} d ago`;
  }

  function createCard(id) {
    const card = document.createElement('article');
    card.className = 'provider';
    card.dataset.provider = id;
    card.setAttribute('aria-labelledby', `${id}-title`);
    const icon = id === 'claude'
      ? '<path d="M16 3v26M3 16h26M7 7l18 18M7 25 25 7M4 11l24 10M11 28 21 4" fill="none" stroke="currentColor" stroke-width="2.5"/>'
      : '<path d="m11 8-8 8 8 8m10-16 8 8-8 8m-3-20-4 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>';
    // Only fixed, local markup is inserted as HTML. API strings use textContent.
    card.innerHTML = `<header class="provider-header"><div class="provider-identity"><span class="provider-icon" aria-hidden="true"><svg viewBox="0 0 32 32">${icon}</svg></span><div><h2 id="${id}-title">${providerNames[id]}</h2><div class="provider-subtitle">${id === 'codex' ? 'ChatGPT' : 'Anthropic'}</div></div></div><span class="plan"></span></header><div class="gauges"></div><p class="provider-empty" hidden>No limits reported yet</p><div class="history" hidden><div class="history-heading"><span>Last 24 hours</span><span class="history-legend"><span><i class="history-key"></i>5 h</span><span><i class="history-key week"></i>Week</span></span></div><svg viewBox="0 0 600 60" preserveAspectRatio="none" role="img" aria-label="${providerNames[id]} remaining capacity over the last 24 hours"><line class="history-baseline" x1="0" y1="59" x2="600" y2="59"/><polyline class="session-line"/><polyline class="weekly-line"/></svg></div>`;
    $('#providers').append(card);
    const entry = { element: card, gauges: new Map() };
    cards.set(id, entry);
    return entry;
  }

  function createGauge(card, provider, id) {
    const gauge = document.createElement('section');
    gauge.className = `gauge ${id === 'reserve' ? 'reserve' : ''}`;
    gauge.innerHTML = `<h3 class="gauge-label">${labels[id]}</h3><div class="meter" role="meter" aria-label="${providerNames[provider]} ${labels[id]} remaining" aria-valuemin="0" aria-valuemax="100"><svg class="ring" viewBox="0 0 200 200" aria-hidden="true"><circle class="ring-track" cx="100" cy="100" r="88"/><circle class="ring-fill" cx="100" cy="100" r="88" pathLength="100" stroke-dasharray="100 100"/></svg><div class="readout" aria-hidden="true"><div class="value"><span class="number">—</span><span class="percent">%</span></div><span class="remaining">left</span></div></div><p class="reset" id="${provider}-${id}-reset"></p>`;
    gauge.querySelector('.meter').setAttribute('aria-describedby', `${provider}-${id}-reset`);
    card.element.querySelector('.gauges').append(gauge);
    card.gauges.set(id, gauge);
    return gauge;
  }

  function renderHistory(card, id) {
    const cutoff = Date.now() - 86400000;
    const points = (Array.isArray(snapshot.history) ? snapshot.history : [])
      .filter((p) => p && Number.isFinite(Date.parse(p.t)) && Date.parse(p.t) >= cutoff && Date.parse(p.t) <= Date.now())
      .sort((a, b) => Date.parse(a.t) - Date.parse(b.t));
    let hasHistory = false;
    for (const [window, selector] of [['session', '.session-line'], ['weekly', '.weekly-line']]) {
      const valid = points.filter((p) => validPercent(p[`${id}.${window}`]));
      const visible = valid.length >= 2;
      hasHistory ||= visible;
      card.element.querySelector(selector).setAttribute('points', visible ? valid.map((p) => {
        const x = (Date.parse(p.t) - cutoff) / 86400000 * 600;
        const y = 4 + p[`${id}.${window}`] / 100 * 52;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      }).join(' ') : '');
    }
    card.element.querySelector('.history').hidden = !hasHistory;
  }

  function render() {
    if (!snapshot) return;
    const age = ageSeconds();
    const stale = snapshot.stale === true || age > 600;
    const allWindows = Object.values(snapshot.providers).flatMap((p) => Array.isArray(p?.windows) ? p.windows : []);
    const used = allWindows.filter((w) => w && validPercent(w.percent)).map((w) => w.percent);
    const worst = used.length ? severity(Math.max(...used)) : 'loading';
    // Honour the server's overall status; never understate a window's severity.
    const ranks = { loading: -1, ok: 0, warn: 1, critical: 2 };
    const overall = stale ? 'stale' : ranks[snapshot.severity] > ranks[worst] ? snapshot.severity : worst;
    $('#severity').dataset.severity = overall;
    $('#severity').textContent = { ok: 'All clear', warn: 'Watch usage', critical: 'Running low', stale: 'Stale', loading: 'No data' }[overall];
    $('#updated').textContent = elapsedText(age);
    $('#dashboard').classList.toggle('is-stale', stale);
    const seen = new Date(snapshot.collected_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    $('#notice').textContent = [stale ? `STALE — collector last seen ${seen}` : '', failure].filter(Boolean).join(' · ');
    $('#notice').hidden = !$('#notice').textContent;

    for (const id of Object.keys(providerNames)) {
      const card = cards.get(id) || createCard(id);
      const provider = snapshot.providers[id];
      card.element.querySelector('.plan').textContent = provider?.plan || 'Not connected';
      const reported = Array.isArray(provider?.windows) ? provider.windows : [];
      let count = 0;
      for (const windowId of windows[id]) {
        const data = reported.find((w) => w?.id === windowId);
        const gauge = card.gauges.get(windowId) || (data ? createGauge(card, id, windowId) : null);
        if (!gauge) continue;
        gauge.hidden = !data;
        if (!data) continue;
        count++;
        const valid = validPercent(data.percent);
        const remaining = valid ? Math.round((100 - data.percent) * 10) / 10 : null;
        const meter = gauge.querySelector('.meter');
        const ring = gauge.querySelector('.ring-fill');
        gauge.dataset.severity = stale || !valid ? 'stale' : severity(data.percent);
        gauge.querySelector('.number').textContent = valid ? String(remaining) : '—';
        gauge.querySelector('.percent').hidden = !valid;
        if (valid) meter.setAttribute('aria-valuenow', remaining);
        else meter.removeAttribute('aria-valuenow');
        meter.setAttribute('aria-valuetext', valid ? `${remaining}% remaining${stale ? ', stale data' : ''}` : 'Unavailable');
        const offset = valid ? String(100 - remaining) : '100';
        if (ring.dataset.offset !== offset) {
          if (ring.dataset.offset !== undefined) ring.classList.add('has-value');
          ring.style.strokeDashoffset = offset;
          ring.dataset.offset = offset;
        }
        // A butt cap leaves no coloured dot at zero, while the arc can still
        // animate all the way to empty instead of disappearing immediately.
        ring.style.strokeLinecap = remaining === 0 ? 'butt' : 'round';
        ring.style.opacity = valid ? '1' : '0';
        gauge.querySelector('.reset').textContent = resetText(data.resets_at);
      }
      card.element.querySelector('.provider-empty').hidden = count > 0;
      card.element.querySelector('.provider-empty').textContent = 'No limits reported yet';
    }
  }

  function accept(data) {
    if (!data || !Number.isFinite(Date.parse(data.collected_at)) || !data.providers || typeof data.providers !== 'object' || Array.isArray(data.providers)) {
      throw new Error('Invalid status response');
    }
    snapshot = data;
    receivedAt = Date.now();
    failure = '';
    render();
    for (const [id, card] of cards) renderHistory(card, id);
  }

  async function refresh() {
    if (demo) {
      demoSnapshot ||= makeDemo();
      // A demo collector stays live while the sample percentages remain fixed.
      demoSnapshot.collected_at = new Date(Date.now() - 42000).toISOString();
      accept(demoSnapshot);
      return;
    }
    if (!token || authFailed || request) return;
    const controller = new AbortController();
    request = controller;
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch('/v1/status', { headers: { Authorization: `Bearer ${token}` }, cache: 'no-store', credentials: 'omit', redirect: 'error', signal: controller.signal });
      if (request !== controller) return;
      if (response.status === 401 || response.status === 403) {
        authFailed = true;
        showPairing('This pairing link was not accepted. Paste a new link from your collector.');
        return;
      }
      if (!response.ok) throw new Error(`Status ${response.status}`);
      const data = await response.json();
      if (request !== controller) return;
      accept(data);
    } catch {
      if (request !== controller) return;
      failure = snapshot ? 'Connection lost — retrying every 60 s' : 'Couldn’t load your limits. Retrying every 60 s.';
      if (snapshot) render();
      else {
        $('#severity').textContent = 'Unavailable';
        $('#severity').dataset.severity = 'stale';
        $('#notice').textContent = failure;
        $('#notice').hidden = false;
      }
    } finally {
      clearTimeout(timeout);
      if (request === controller) request = null;
    }
  }

  function showPairing(message = '') {
    $('#pairing').hidden = false;
    $('#dashboard').hidden = true;
    $('#status-group').hidden = true;
    $('#pairing-error').textContent = message;
    $('#pairing-error').hidden = !message;
  }

  function start() {
    request?.abort();
    request = null;
    token = currentToken();
    snapshot = null;
    failure = '';
    authFailed = false;
    cards.clear();
    $('#providers').replaceChildren();
    if (!demo && !token) { showPairing(); return; }
    $('#pairing').hidden = true;
    $('#dashboard').hidden = false;
    $('#status-group').hidden = false;
    $('#demo-badge').hidden = !demo;
    $('#updated').textContent = '';
    $('#severity').textContent = 'Connecting';
    $('#severity').dataset.severity = 'loading';
    $('#notice').textContent = 'Connecting to your collector…';
    $('#notice').hidden = false;
    for (const id of Object.keys(providerNames)) {
      const card = createCard(id);
      card.element.querySelector('.provider-empty').hidden = false;
      card.element.querySelector('.provider-empty').textContent = 'Waiting for your limits';
    }
    refresh();
  }

  $('#pairing-form').addEventListener('submit', (event) => {
    event.preventDefault();
    try {
      const url = new URL($('#pairing-url').value.trim());
      const value = new URLSearchParams(url.hash.slice(1)).get('t')?.trim();
      if (!['https:', 'http:'].includes(url.protocol) || !value) throw new Error('Invalid pairing URL');
      if (url.origin !== location.origin) {
        showPairing('Open the pairing link on its own host to pair this screen.');
        return;
      }
      history.replaceState(null, '', `${location.pathname}${location.search}#${new URLSearchParams({ t: value })}`);
      $('#pairing-url').value = '';
      start();
    } catch { showPairing('Paste the complete pairing URL, including #t= and its token.'); }
  });
  window.addEventListener('hashchange', start);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  setInterval(() => { if (!document.hidden) refresh(); }, 60000);
  setInterval(() => { if (!document.hidden && snapshot) render(); }, 1000);
  start();
})();
