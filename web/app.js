(() => {
  'use strict';

  const $ = (selector) => document.querySelector(selector);
  const storageKey = 'quota.token';
  const query = new URLSearchParams(location.search);
  const demoPro = query.getAll('demo').includes('pro');
  const demo = query.get('demo') === '1' || demoPro;
  const displayModes = ['remaining', 'used'];
  const providerNames = { claude: 'Claude', codex: 'Codex', gemini: 'Gemini' };
  const subtitles = { claude: 'Anthropic', codex: 'ChatGPT', gemini: 'Google' };
  // Only reported providers receive cards.
  // A window keeps the label the collector sent — Gemini names its windows after the models
  // it meters, so nothing here may assume `session` and `weekly` exist.
  // Official marks: simple-icons (CC0) for Claude and OpenAI, lobe-icons (MIT) for Gemini.
  // Trademarks of Anthropic, OpenAI and Google, used nominatively to identify the service.
  // See docs/brand/NOTICE.md.
  const marks = {
    claude: '<path d="m4.7144 15.9555 4.7174-2.6471.079-.2307-.079-.1275h-.2307l-.7893-.0486-2.6956-.0729-2.3375-.0971-2.2646-.1214-.5707-.1215-.5343-.7042.0546-.3522.4797-.3218.686.0608 1.5179.1032 2.2767.1578 1.6514.0972 2.4468.255h.3886l.0546-.1579-.1336-.0971-.1032-.0972L6.973 9.8356l-2.55-1.6879-1.3356-.9714-.7225-.4918-.3643-.4614-.1578-1.0078.6557-.7225.8803.0607.2246.0607.8925.686 1.9064 1.4754 2.4893 1.8336.3643.3035.1457-.1032.0182-.0728-.164-.2733-1.3539-2.4467-1.445-2.4893-.6435-1.032-.17-.6194c-.0607-.255-.1032-.4674-.1032-.7285L6.287.1335 6.6997 0l.9957.1336.419.3642.6192 1.4147 1.0018 2.2282 1.5543 3.0296.4553.8985.2429.8318.091.255h.1579v-.1457l.1275-1.706.2368-2.0947.2307-2.6957.0789-.7589.3764-.9107.7468-.4918.5828.2793.4797.686-.0668.4433-.2853 1.8517-.5586 2.9021-.3643 1.9429h.2125l.2429-.2429.9835-1.3053 1.6514-2.0643.7286-.8196.85-.9046.5464-.4311h1.0321l.759 1.1293-.34 1.1657-1.0625 1.3478-.8804 1.1414-1.2628 1.7-.7893 1.36.0729.1093.1882-.0183 2.8535-.607 1.5421-.2794 1.8396-.3157.8318.3886.091.3946-.3278.8075-1.967.4857-2.3072.4614-3.4364.8136-.0425.0304.0486.0607 1.5482.1457.6618.0364h1.621l3.0175.2247.7892.522.4736.6376-.079.4857-1.2142.6193-1.6393-.3886-3.825-.9107-1.3113-.3279h-.1822v.1093l1.0929 1.0686 2.0035 1.8092 2.5075 2.3314.1275.5768-.3218.4554-.34-.0486-2.2039-1.6575-.85-.7468-1.9246-1.621h-.1275v.17l.4432.6496 2.3436 3.5214.1214 1.0807-.17.3521-.6071.2125-.6679-.1214-1.3721-1.9246L14.38 17.959l-1.1414-1.9428-.1397.079-.674 7.2552-.3156.3703-.7286.2793-.6071-.4614-.3218-.7468.3218-1.4753.3886-1.9246.3157-1.53.2853-1.9004.17-.6314-.0121-.0425-.1397.0182-1.4328 1.9672-2.1796 2.9446-1.7243 1.8456-.4128.164-.7164-.3704.0667-.6618.4008-.5889 2.386-3.0357 1.4389-1.882.929-1.0868-.0062-.1579h-.0546l-6.3385 4.1164-1.1293.1457-.4857-.4554.0608-.7467.2307-.2429 1.9064-1.3114Z"/>',
    codex: '<path d="M22.2819 9.8211a5.9847 5.9847 0 0 0-.5157-4.9108 6.0462 6.0462 0 0 0-6.5098-2.9A6.0651 6.0651 0 0 0 4.9807 4.1818a5.9847 5.9847 0 0 0-3.9977 2.9 6.0462 6.0462 0 0 0 .7427 7.0966 5.98 5.98 0 0 0 .511 4.9107 6.051 6.051 0 0 0 6.5146 2.9001A5.9847 5.9847 0 0 0 13.2599 24a6.0557 6.0557 0 0 0 5.7718-4.2058 5.9894 5.9894 0 0 0 3.9977-2.9001 6.0557 6.0557 0 0 0-.7475-7.0729zm-9.022 12.6081a4.4755 4.4755 0 0 1-2.8764-1.0408l.1419-.0804 4.7783-2.7582a.7948.7948 0 0 0 .3927-.6813v-6.7369l2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.504 4.504 0 0 1-4.4945 4.4944zm-9.6607-4.1254a4.4708 4.4708 0 0 1-.5346-3.0137l.142.0852 4.783 2.7582a.7712.7712 0 0 0 .7806 0l5.8428-3.3685v2.3324a.0804.0804 0 0 1-.0332.0615L9.74 19.9502a4.4992 4.4992 0 0 1-6.1408-1.6464zM2.3408 7.8956a4.485 4.485 0 0 1 2.3655-1.9728V11.6a.7664.7664 0 0 0 .3879.6765l5.8144 3.3543-2.0201 1.1685a.0757.0757 0 0 1-.071 0l-4.8303-2.7865A4.504 4.504 0 0 1 2.3408 7.872zm16.5963 3.8558L13.1038 8.364 15.1192 7.2a.0757.0757 0 0 1 .071 0l4.8303 2.7913a4.4944 4.4944 0 0 1-.6765 8.1042v-5.6772a.79.79 0 0 0-.407-.667zm2.0107-3.0231l-.142-.0852-4.7735-2.7818a.7759.7759 0 0 0-.7854 0L9.409 9.2297V6.8974a.0662.0662 0 0 1 .0284-.0615l4.8303-2.7866a4.4992 4.4992 0 0 1 6.6802 4.66zM8.3065 12.863l-2.02-1.1638a.0804.0804 0 0 1-.038-.0567V6.0742a4.4992 4.4992 0 0 1 7.3757-3.4537l-.142.0805L8.704 5.459a.7948.7948 0 0 0-.3927.6813zm1.0976-2.3654l2.602-1.4998 2.6069 1.4998v2.9994l-2.5974 1.4997-2.6067-1.4997Z"/>',
    // The Gemini spark takes the Google gradient, not a flat tint: it is the mark's own
    // palette, and the only provider accent that is not a single colour.
    gemini: '<defs><linearGradient id="gemini-gradient" x1="2" y1="21" x2="21" y2="3" gradientUnits="userSpaceOnUse"><stop stop-color="#4285F4"/><stop offset=".5" stop-color="#9B72CB"/><stop offset="1" stop-color="#D96570"/></linearGradient></defs><path fill="url(#gemini-gradient)" d="M20.616 10.835a14.147 14.147 0 01-4.45-3.001 14.111 14.111 0 01-3.678-6.452.503.503 0 00-.975 0 14.134 14.134 0 01-3.679 6.452 14.155 14.155 0 01-4.45 3.001c-.65.28-1.318.505-2.002.678a.502.502 0 000 .975c.684.172 1.35.397 2.002.677a14.147 14.147 0 014.45 3.001 14.112 14.112 0 013.679 6.453.502.502 0 00.975 0c.172-.685.397-1.351.677-2.003a14.145 14.145 0 013.001-4.45 14.113 14.113 0 016.453-3.678.503.503 0 000-.975 13.245 13.245 0 01-2.003-.678z"/>'
  };

  /** The windows a provider declared, most important first, each with the label it sent. */
  function reportedWindows(id) {
    const list = Array.isArray(snapshot?.providers?.[id]?.windows) ? snapshot.providers[id].windows : [];
    return list
      .filter((w) => w && typeof w.id === 'string' && validPercent(w.percent))
      .map((w, index) => ({ ...w, order: index, text: w.label || w.id }))
      .sort((a, b) => b.percent - a.percent || a.order - b.order);
  }

  function visibleProviders() {
    return Object.keys(providerNames).filter((id) => snapshot?.providers?.[id]);
  }

  let token = '';
  let snapshot = null;
  let receivedAt = 0;
  let request = null;
  let failure = '';
  let authFailed = false;
  // "remaining" prints what is left (Claude's convention), "used" what is spent
  // (Codex's). The worker stores it per device so every screen agrees.
  let displayMode = displayModes.includes(query.get('display')) ? query.get('display') : 'remaining';
  let savingDisplay = false;
  let showHistory = false;
  try { showHistory = localStorage.getItem('quota.history') === 'true'; } catch { /* Use the default. */ }

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
    return {
      collected_at: iso(0), age_seconds: 42, stale: false,
      providers: {
        claude: { plan: 'max', windows: [
          { id: 'session', label: '5 h', percent: 11, resets_at: iso(7800) },
          { id: 'weekly', label: 'Week', percent: 68, resets_at: iso(223200) }
        ], extra: { extra_usage_enabled: false } },
        codex: { plan: demoPro ? 'pro' : 'plus', windows: [
          ...(!demoPro ? [{ id: 'session', label: '5 h', percent: 92, resets_at: iso(2220) }] : []),
          { id: 'weekly', label: 'Week', percent: demoPro ? 74 : 34, resets_at: iso(504000) },
          { id: 'reserve', label: 'Reserve', percent: 10, resets_at: iso(345600) }
        ], extra: { astra_available: true, limit_reached: false } },
        gemini: { plan: 'Standard', windows: [
          { id: 'gemini-3-pro', label: '3 Pro', percent: 62, resets_at: iso(18900) },
          { id: 'gemini-2-5-pro', label: '2.5 Pro', percent: 29, resets_at: iso(50400) }
        ], extra: { buckets: 4 } }
      },
      severity: demoPro ? 'warn' : 'critical',
      display: displayMode,
      history: Array.from({ length: 25 }, (_, hour) => ({
        t: iso(-(24 - hour) * 3600),
        'claude.session': hour < 22 ? 8 + hour * 3 : 5 + (hour - 22) * 3,
        'claude.weekly': 44 + hour,
        ...(!demoPro ? { 'codex.session': 20 + hour * 3 } : {}),
        'codex.weekly': demoPro ? 50 + hour : 10 + hour,
        'gemini.gemini-3-pro': 20 + hour * 2,
        'gemini.gemini-2-5-pro': 6 + hour
      }))

    };
  }

  function providerIsStale(provider) {
    const measured = Date.parse(provider?.measured_at || provider?.extra?.measured_at || '');
    return provider?.stale === true || (Number.isFinite(measured) && Date.now() - measured > 600000);
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
    if (!Number.isFinite(time)) return 'Reset time unknown';
    if (time <= Date.now()) return 'Reset due';
    const minutes = Math.ceil((time - Date.now()) / 60000);
    if (minutes < 60) return `Resets in ${minutes} min`;
    if (minutes < 1440) return `Resets in ${Math.floor(minutes / 60)} h ${minutes % 60} min`;
    return `Resets in ${Math.floor(minutes / 1440)} d ${Math.floor(minutes % 1440 / 60)} h`;
  }
  function elapsedText(age) {
    if (age < 60) return `updated ${Math.floor(age)} s ago`;
    if (age < 3600) return `updated ${Math.floor(age / 60)} min ago`;
    if (age < 86400) return `updated ${Math.floor(age / 3600)} h ago`;
    return `updated ${Math.floor(age / 86400)} d ago`;
  }

  function usedMode() { return displayMode === 'used'; }

  function paintDisplayToggle() {
    for (const button of document.querySelectorAll('#display-toggle button')) {
      button.setAttribute('aria-pressed', String(button.dataset.mode === displayMode));
    }
    $('#footer-note').innerHTML = usedMode()
      ? 'Every number is what\u2019s <strong>used.</strong>'
      : 'Every number is what\u2019s <strong>left.</strong>';
  }

  /** Optimistic: paint the new mode at once, then persist it for the other screens. */
  async function setDisplay(mode) {
    if (!displayModes.includes(mode) || mode === displayMode) return;
    const previous = displayMode;
    displayMode = mode;
    if (demo && snapshot) snapshot.display = mode;
    paintDisplayToggle();
    render();
    if (demo || !token) return;
    savingDisplay = true;
    try {
      const response = await fetch('/v1/settings', {
        method: 'PUT',
        headers: { Authorization: `Bearer ${token}`, 'content-type': 'application/json' },
        body: JSON.stringify({ display: mode }),
        cache: 'no-store', credentials: 'omit', redirect: 'error'
      });
      if (!response.ok) throw new Error(`Settings ${response.status}`);
    } catch {
      displayMode = previous;
      paintDisplayToggle();
      failure = 'Couldn\u2019t save that on your other screens.';
      render();
    } finally {
      savingDisplay = false;
    }
  }

  function createCard(id) {
    const card = document.createElement('article');
    card.className = 'provider';
    card.dataset.provider = id;
    card.setAttribute('aria-labelledby', `${id}-title`);
    const icon = marks[id];
    // Only fixed, local markup is inserted as HTML. API strings use textContent.
    card.innerHTML = `<header class="provider-header"><div class="provider-identity"><span class="provider-icon" aria-hidden="true"><svg viewBox="0 0 24 24">${icon}</svg></span><div><h2 id="${id}-title">${providerNames[id]}</h2><div class="provider-subtitle">${subtitles[id]}</div></div></div><span class="provider-state"></span></header><div class="gauges"></div><button class="details-toggle" type="button" aria-expanded="false">Details</button><p class="provider-empty" hidden>No limits reported yet</p><div class="history" hidden><div class="history-heading"><span>Last 24 hours</span><span class="history-legend"><span class="legend-primary"><i class="history-key"></i><span class="legend-text"></span></span><span class="legend-secondary"><i class="history-key week"></i><span class="legend-text"></span></span></span></div><svg viewBox="0 0 600 60" preserveAspectRatio="none" role="img" aria-label="${providerNames[id]} remaining capacity over the last 24 hours"><line class="history-baseline" x1="0" y1="59" x2="600" y2="59"/><polyline class="session-line"/><polyline class="weekly-line"/></svg></div>`;
    const detailsToggle = card.querySelector('.details-toggle');
    detailsToggle.addEventListener('click', () => {
      const expanded = card.classList.toggle('expanded');
      detailsToggle.setAttribute('aria-expanded', String(expanded));
      detailsToggle.textContent = expanded ? 'Hide details' : 'Details';
    });
    $('#providers').append(card);
    const entry = { element: card, gauges: new Map() };
    cards.set(id, entry);
    return entry;
  }

  function createGauge(card, provider, window) {
    const id = window.id;
    const gauge = document.createElement('section');
    gauge.className = 'gauge';
    gauge.dataset.window = id;
    // Only fixed markup is inserted as HTML; the label the API sent goes in by textContent.
    gauge.innerHTML = `<div class="meter" role="meter" aria-valuemin="0" aria-valuemax="100"><svg class="ring" viewBox="0 0 200 200" aria-hidden="true"><circle class="ring-track" cx="100" cy="100" r="88"/><circle class="ring-fill" cx="100" cy="100" r="88" pathLength="100" stroke-dasharray="100 100"/></svg><div class="readout" aria-hidden="true"><div class="value"><span class="number">—</span><span class="percent">%</span></div><span class="remaining">left</span></div></div><h3 class="gauge-label"></h3><p class="reset"></p>`;
    gauge.querySelector('.gauge-label').textContent = window.text;
    gauge.querySelector('.reset').id = `${provider}-${id}-reset`;
    gauge.querySelector('.meter').setAttribute('aria-describedby', `${provider}-${id}-reset`);
    card.element.querySelector('.gauges').append(gauge);
    card.gauges.set(id, gauge);
    return gauge;
  }

  /** Two lines: the provider's headline window solid, its second one dashed. */
  function renderHistory(card, id, reported) {
    const cutoff = Date.now() - 86400000;
    const points = (Array.isArray(snapshot.history) ? snapshot.history : [])
      .filter((p) => p && Number.isFinite(Date.parse(p.t)) && Date.parse(p.t) >= cutoff && Date.parse(p.t) <= Date.now())
      .sort((a, b) => Date.parse(a.t) - Date.parse(b.t));
    let hasHistory = false;
    for (const [index, lineClass, legendClass] of [[0, '.session-line', '.legend-primary'], [1, '.weekly-line', '.legend-secondary']]) {
      const window = reported[index];
      const key = window ? `${id}.${window.id}` : null;
      const valid = key ? points.filter((p) => validPercent(p[key])) : [];
      const visible = valid.length >= 2;
      const legend = card.element.querySelector(legendClass);
      legend.hidden = !visible;
      legend.querySelector('.legend-text').textContent = window ? window.text : '';
      hasHistory ||= visible;
      card.element.querySelector(lineClass).setAttribute('points', visible ? valid.map((p) => {
        const x = (Date.parse(p.t) - cutoff) / 86400000 * 600;
        const percent = p[key];
        const y = 4 + (usedMode() ? 100 - percent : percent) / 100 * 52;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      }).join(' ') : '');
    }
    card.element.querySelector('.history').hidden = !showHistory || !hasHistory;
  }

  function render() {
    if (!snapshot) return;
    paintDisplayToggle();
    $('#history-toggle').setAttribute('aria-pressed', String(showHistory));
    $('#dashboard').classList.toggle('show-history', showHistory);
    $('#dashboard').setAttribute('aria-label', usedMode() ? 'AI capacity used' : 'AI capacity remaining');
    const age = ageSeconds();
    const stale = snapshot.stale === true || age > 600;
    const partialStale = Object.values(snapshot.providers).some(providerIsStale);
    const allWindows = Object.values(snapshot.providers).flatMap((p) => Array.isArray(p?.windows) ? p.windows : []);
    const used = allWindows.filter((w) => w && validPercent(w.percent)).map((w) => w.percent);
    const worst = used.length ? severity(Math.max(...used)) : 'loading';
    // Honour the server's overall status; never understate a window's severity.
    const ranks = { loading: -1, ok: 0, warn: 1, critical: 2 };
    const overall = stale || partialStale ? 'stale' : ranks[snapshot.severity] > ranks[worst] ? snapshot.severity : worst;
    $('#severity').dataset.severity = overall;
    $('#severity').textContent = { ok: 'Ready', warn: 'Running low', critical: 'Nearly empty', stale: 'Out of date', loading: 'No data' }[overall];
    $('#updated').textContent = elapsedText(age);
    $('#dashboard').classList.toggle('is-stale', stale);
    const seen = new Date(snapshot.collected_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    $('#notice').textContent = [stale ? `Out of date · last update ${seen}` : partialStale ? 'Some providers are out of date. Their last known limits are shown in grey.' : '', failure].filter(Boolean).join(' · ');
    $('#notice').hidden = !$('#notice').textContent;

    const visible = visibleProviders();
    // The grid takes as many columns as there are cards: two providers keep the old
    // layout, a third one makes it a row of three instead of an orphan on line two.
    $('#providers').style.setProperty('--providers', String(Math.max(1, visible.length)));
    $('#dashboard').dataset.providers = String(visible.length);
    for (const [id, card] of cards) {
      if (!visible.includes(id)) { card.element.remove(); cards.delete(id); }
    }
    for (const [providerIndex, id] of visible.entries()) {
      const card = cards.get(id) || createCard(id);
      card.element.querySelector('.history svg')
        .setAttribute('aria-label', `${providerNames[id]} capacity ${usedMode() ? 'used' : 'remaining'} over the last 24 hours`);
      const provider = snapshot.providers[id];
      const providerStale = stale || providerIsStale(provider);
      card.element.classList.toggle('is-stale', providerStale);
      const plan = provider?.plan || '';
      const titlePlan = plan ? plan[0].toUpperCase() + plan.slice(1) : '';
      card.element.querySelector('.provider-subtitle').textContent = id === 'codex'
        ? (/^chatgpt/i.test(plan) ? plan : `ChatGPT ${titlePlan}`.trim())
        : [subtitles[id], titlePlan].filter(Boolean).join(' · ');
      const providerSlot = $('#providers').children[providerIndex];
      if (providerSlot !== card.element) $('#providers').insertBefore(card.element, providerSlot || null);
      const reported = reportedWindows(id);
      const state = providerStale ? 'stale' : reported.length ? severity(reported[0].percent) : 'loading';
      const stateLabel = card.element.querySelector('.provider-state');
      stateLabel.dataset.severity = state;
      stateLabel.textContent = { ok: 'Available', warn: 'Running low', critical: 'Nearly empty', stale: 'Out of date', loading: 'No data' }[state];
      // A window that vanished from the payload must take its gauge with it.
      for (const [windowId, gauge] of card.gauges) {
        if (!reported.some((w) => w.id === windowId)) { gauge.remove(); card.gauges.delete(windowId); }
      }
      let count = 0;
      for (const data of reported) {
        const windowId = data.id;
        const gauge = card.gauges.get(windowId) || createGauge(card, id, data);
        const windowSlot = gauge.parentElement.children[count];
        if (windowSlot !== gauge) gauge.parentElement.insertBefore(gauge, windowSlot || null);
        gauge.style.order = count;
        gauge.classList.toggle('hero', count === 0);
        gauge.querySelector('.gauge-label').textContent = data.text;
        gauge.hidden = false;
        count++;
        const valid = validPercent(data.percent);
        const remaining = valid ? 100 - Math.round(data.percent) : null;
        // Same arc, read the other way round: it grows with usage in "used" mode.
        const shown = valid ? (usedMode() ? Math.round(data.percent) : remaining) : null;
        const word = usedMode() ? 'used' : 'remaining';
        const meter = gauge.querySelector('.meter');
        const ring = gauge.querySelector('.ring-fill');
        gauge.dataset.severity = providerStale || !valid ? 'stale' : severity(data.percent);
        gauge.querySelector('.number').textContent = valid ? String(shown) : '—';
        gauge.querySelector('.percent').hidden = !valid;
        gauge.querySelector('.remaining').textContent = usedMode() ? 'used' : 'left';
        meter.setAttribute('aria-label', `${providerNames[id]} ${data.text} ${word}`);
        if (valid) meter.setAttribute('aria-valuenow', shown);
        else meter.removeAttribute('aria-valuenow');
        meter.setAttribute('aria-valuetext', valid ? `${shown}% ${word}${providerStale ? ', stale data' : ''}` : 'Unavailable');
        const offset = valid ? String(100 - shown) : '100';
        if (ring.dataset.offset !== offset) {
          if (ring.dataset.offset !== undefined) ring.classList.add('has-value');
          ring.style.strokeDashoffset = offset;
          ring.dataset.offset = offset;
        }
        // A butt cap leaves no coloured dot at zero, while the arc can still
        // animate all the way to empty instead of disappearing immediately.
        ring.style.strokeLinecap = shown === 0 ? 'butt' : 'round';
        ring.style.opacity = valid ? '1' : '0';
        gauge.querySelector('.reset').textContent = resetText(data.resets_at);
      }
      card.element.querySelector('.gauges').style.setProperty('--count', Math.max(1, count));
      card.element.dataset.windows = count;
      card.element.querySelector('.provider-empty').hidden = count > 0;
      card.element.querySelector('.provider-empty').textContent = 'No limits reported yet';
      renderHistory(card, id, reported);
    }
  }

  function accept(data) {
    if (!data || !Number.isFinite(Date.parse(data.collected_at)) || !data.providers || typeof data.providers !== 'object' || Array.isArray(data.providers)) {
      throw new Error('Invalid status response');
    }
    snapshot = data;
    receivedAt = Date.now();
    // The server is the source of truth, except while our own PUT is in flight.
    if (!savingDisplay && displayModes.includes(data.display)) displayMode = data.display;
    failure = '';
    render();
  }

  async function refresh() {
    if (demo) {
      demoSnapshot ||= makeDemo();
      // A demo collector stays live while the sample percentages remain fixed.
      demoSnapshot.collected_at = new Date().toISOString();
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
    $('#display-toggle').hidden = true;
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
    $('#display-toggle').hidden = false;
    paintDisplayToggle();
    $('#updated').textContent = '';
    $('#severity').textContent = 'Connecting';
    $('#severity').dataset.severity = 'loading';
    $('#notice').textContent = 'Connecting to your collector…';
    $('#notice').hidden = false;
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
  $('#display-toggle').addEventListener('click', (event) => {
    const button = event.target.closest('button[data-mode]');
    if (button) setDisplay(button.dataset.mode);
  });
  $('#history-toggle').addEventListener('click', () => {
    showHistory = !showHistory;
    try { localStorage.setItem('quota.history', String(showHistory)); } catch { /* Still works for this visit. */ }
    render();
  });
  // Pairing another screen: the link carries the token, so the QR is drawn here, in the page.
  // Sending it to a QR service would be handing out the credential.
  function pairingUrl() {
    return `${location.origin}${location.pathname}#${new URLSearchParams({ t: token })}`;
  }
  function paintQr() {
    const target = $('#qr');
    const message = $('#pair-message');
    if (demo || !token) {
      target.replaceChildren();
      message.textContent = 'Pair this screen with your collector first — then you can pass it on.';
      return;
    }
    try {
      // Level M survives a phone camera at an angle; the URL is short enough for a small code.
      target.innerHTML = QuotaQR.svg(pairingUrl(), { ecc: 'M' });
      message.textContent = 'Anyone who scans this can read your limits. Only show it to a screen you own.';
    } catch {
      target.replaceChildren();
      message.textContent = 'This browser could not draw the code. Use “Copy the link instead”.';
    }
  }
  $('#pair-toggle').addEventListener('click', () => {
    const panel = $('#pair-panel');
    panel.hidden = !panel.hidden;
    $('#pair-toggle').setAttribute('aria-expanded', String(!panel.hidden));
    if (!panel.hidden) paintQr();
    else $('#qr').replaceChildren();
  });
  $('#pair-close').addEventListener('click', () => {
    $('#pair-panel').hidden = true;
    $('#qr').replaceChildren();
    $('#pair-toggle').setAttribute('aria-expanded', 'false');
    $('#pair-toggle').focus();
  });
  $('#pair-copy').addEventListener('click', async () => {
    if (demo || !token) return;
    try {
      await navigator.clipboard.writeText(pairingUrl());
      $('#pair-message').textContent = 'Link copied. Paste it into the other screen, then clear your clipboard.';
    } catch {
      $('#pair-message').textContent = 'Your browser blocked the clipboard. Open ~/.quota/pairing.txt on your Mac instead.';
    }
  });

  let trmnlBusy = false;
  function trmnlMessage(message) {
    $('#trmnl-message').textContent = message;
    $('#trmnl-message').hidden = !message;
  }
  function trmnlControls(busy) {
    trmnlBusy = busy;
    for (const id of ['trmnl-save', 'trmnl-disconnect', 'trmnl-url']) $(`#${id}`).disabled = busy || demo;
  }
  function paintTrmnl(settings) {
    const connected = typeof settings.trmnl_webhook_url === 'string' && !!settings.trmnl_webhook_url;
    $('#trmnl-url').value = connected ? settings.trmnl_webhook_url : '';
    $('#trmnl-save').textContent = connected ? 'Save changes' : 'Connect display';
    $('#trmnl-disconnect').hidden = !connected;
  }
  async function trmnlRequest(method, value) {
    const response = await fetch('/v1/settings', {
      method, headers: { Authorization: `Bearer ${token}`, ...(method === 'PUT' ? { 'content-type': 'application/json' } : {}) },
      ...(method === 'PUT' ? { body: JSON.stringify({ trmnl_webhook_url: value }) } : {}),
      cache: 'no-store', credentials: 'omit', redirect: 'error', signal: AbortSignal.timeout(15000)
    });
    if (!response.ok) throw new Error('Settings unavailable');
    return response.json();
  }
  $('#trmnl-toggle').addEventListener('click', async () => {
    const panel = $('#trmnl-panel');
    panel.hidden = !panel.hidden;
    $('#trmnl-toggle').setAttribute('aria-expanded', String(!panel.hidden));
    if (panel.hidden) return;
    if (demo) { trmnlControls(false); trmnlMessage('Pair this screen with your collector to connect a real TRMNL display.'); return; }
    trmnlControls(true);
    trmnlMessage('Loading your display settings…');
    try { paintTrmnl(await trmnlRequest('GET')); trmnlMessage(''); }
    catch { trmnlMessage('Couldn’t load your display settings. Close this panel and try again.'); }
    finally { trmnlControls(false); }
  });
  $('#trmnl-close').addEventListener('click', () => {
    $('#trmnl-panel').hidden = true;
    $('#trmnl-toggle').setAttribute('aria-expanded', 'false');
    $('#trmnl-toggle').focus();
  });
  async function saveTrmnl(value) {
    if (trmnlBusy || demo || !token) return;
    trmnlControls(true);
    trmnlMessage('Saving…');
    try {
      paintTrmnl(await trmnlRequest('PUT', value));
      trmnlMessage(value ? 'Connected. Your next update will arrive within five minutes.' : 'Disconnected. Quota will stop sending updates to this display.');
    } catch { trmnlMessage('Couldn’t save your display settings. Please try again.'); }
    finally { trmnlControls(false); }
  }
  $('#trmnl-form').addEventListener('submit', (event) => {
    event.preventDefault();
    try {
      const url = new URL($('#trmnl-url').value.trim());
      if (url.protocol !== 'https:' || !['trmnl.com', 'usetrmnl.com'].includes(url.hostname) || !/^\/api\/custom_plugins\/[^/]+$/.test(url.pathname) || url.username || url.password || url.search || url.hash) throw new Error('Invalid webhook');
      saveTrmnl(url.href);
    } catch { trmnlMessage('Paste the complete HTTPS webhook URL from your TRMNL private plugin.'); }
  });
  $('#trmnl-disconnect').addEventListener('click', () => saveTrmnl(null));

  let wakeWanted = false;
  let wakeLock = null;
  function screenNotice(message) {
    $('#screen-notice').textContent = message;
    $('#screen-notice').hidden = !message;
  }
  async function acquireWakeLock() {
    try {
      wakeLock = await navigator.wakeLock.request('screen');
      $('#wake-toggle').setAttribute('aria-pressed', 'true');
      $('#wake-toggle').textContent = 'Keeping awake';
      wakeLock.addEventListener('release', () => {
        wakeLock = null;
        $('#wake-toggle').setAttribute('aria-pressed', 'false');
        $('#wake-toggle').textContent = 'Keep awake';
      });
      screenNotice('');
    } catch {
      wakeWanted = false;
      screenNotice('This browser could not keep the screen awake. Check your device’s display settings.');
    }
  }
  if ('wakeLock' in navigator) {
    $('#wake-toggle').hidden = false;
    $('#wake-toggle').addEventListener('click', async () => {
      wakeWanted = !wakeWanted;
      if (wakeWanted) await acquireWakeLock();
      else await wakeLock?.release();
    });
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && wakeWanted && !wakeLock) acquireWakeLock();
    });
  }
  if (document.fullscreenEnabled) {
    $('#fullscreen-toggle').hidden = false;
    $('#fullscreen-toggle').addEventListener('click', async () => {
      try {
        if (document.fullscreenElement) await document.exitFullscreen();
        else await document.documentElement.requestFullscreen();
        screenNotice('');
      } catch { screenNotice('Full screen is not available in this browser.'); }
    });
    document.addEventListener('fullscreenchange', () => {
      $('#fullscreen-toggle').textContent = document.fullscreenElement ? 'Exit full screen' : 'Full screen';
    });
  }
  window.addEventListener('hashchange', start);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  setInterval(() => { if (!document.hidden) refresh(); }, 60000);
  setInterval(() => { if (!document.hidden && snapshot) render(); }, 1000);
  start();
})();
