import { describe, expect, it } from 'vitest';
import {
  DEFAULT_SETTINGS,
  MAX_STATUS_POINTS,
  appendHistory,
  bearerToken,
  buildMergeVariables,
  buildStatus,
  downsample,
  formatClock,
  formatResetsIn,
  isValidToken,
  isWeeklyWindow,
  normalizeSettings,
  severityFor,
  validateSnapshot,
  worstSeverity,
} from '../src/logic';
import type { MergeWindow } from '../src/logic';
import type { HistoryPoint, Snapshot } from '../src/types';

const NOW = Date.parse('2026-09-09T18:00:00Z');
const TOKEN = 'a'.repeat(43);

function snapshot(claudeSession = 11, weekly = 60, at = '2026-09-09T18:00:00Z'): Snapshot {
  return {
    collected_at: at,
    providers: {
      claude: {
        plan: 'max',
        windows: [
          { id: 'session', label: '5 h', percent: claudeSession, resets_at: '2026-09-09T20:10:00Z' },
          { id: 'weekly', label: 'Week', percent: weekly, resets_at: '2026-09-12T12:00:00Z' },
        ],
      },
      codex: {
        plan: 'plus',
        windows: [
          { id: 'session', label: '5 h', percent: 12, resets_at: '2026-09-09T19:37:07Z' },
          { id: 'weekly', label: 'Week', percent: 6, resets_at: '2026-09-15T13:43:08Z' },
          { id: 'reserve', label: 'Reserve', percent: 10, resets_at: '2026-09-13T15:36:54Z' },
        ],
      },
    },
  };
}

/** Claude with the per-model weekly window the usage API added in September 2026. */
function withFable(): Snapshot {
  const base = snapshot();
  base.providers.claude.windows.push({
    id: 'weekly_fable', label: 'Week · Fable', percent: 18, resets_at: '2026-09-12T12:00:00Z',
  });
  return base;
}

/** A snapshot with the third provider: window ids are model names, not `session`/`weekly`. */
function withGemini(): Snapshot {
  const base = snapshot();
  return {
    ...base,
    providers: {
      ...base.providers,
      gemini: {
        plan: 'Standard',
        windows: [
          { id: 'gemini-3-pro', label: '3 Pro', percent: 62, resets_at: '2026-09-09T23:15:00Z' },
          { id: 'gemini-2-5-pro', label: '2.5 Pro', percent: 29, resets_at: '2026-09-10T00:00:00Z' },
        ],
        extra: { buckets: 4 },
      },
    },
  };
}

describe('token', () => {
  it('accepts a 43-char base64url token only', () => {
    expect(isValidToken(TOKEN)).toBe(true);
    expect(isValidToken('a'.repeat(42))).toBe(false);
    expect(isValidToken('a'.repeat(44))).toBe(false);
    expect(isValidToken('a'.repeat(42) + '+')).toBe(false);
    expect(isValidToken(undefined)).toBe(false);
  });

  it('parses the Authorization header', () => {
    expect(bearerToken(`Bearer ${TOKEN}`)).toBe(TOKEN);
    expect(bearerToken(`bearer ${TOKEN}`)).toBe(TOKEN);
    expect(bearerToken(TOKEN)).toBeNull();
    expect(bearerToken('Bearer short')).toBeNull();
    expect(bearerToken(null)).toBeNull();
  });
});

describe('severity', () => {
  it('uses <60 ok, 60-85 warn, >85 critical', () => {
    expect(severityFor(0, DEFAULT_SETTINGS)).toBe('ok');
    expect(severityFor(59.9, DEFAULT_SETTINGS)).toBe('ok');
    expect(severityFor(60, DEFAULT_SETTINGS)).toBe('warn');
    expect(severityFor(85, DEFAULT_SETTINGS)).toBe('warn');
    expect(severityFor(85.1, DEFAULT_SETTINGS)).toBe('critical');
  });

  it('takes the worst window across providers', () => {
    expect(worstSeverity(snapshot(11, 10), DEFAULT_SETTINGS)).toBe('ok');
    expect(worstSeverity(snapshot(11, 60), DEFAULT_SETTINGS)).toBe('warn');
    expect(worstSeverity(snapshot(99, 10), DEFAULT_SETTINGS)).toBe('critical');
  });

  it('honours custom thresholds', () => {
    const strict = { ...DEFAULT_SETTINGS, warn: 10, critical: 40 };
    expect(worstSeverity(snapshot(11, 5), strict)).toBe('warn');
    expect(worstSeverity(snapshot(11, 5), { ...strict, critical: 10 })).toBe('critical');
  });
});

describe('staleness', () => {
  it('flags a snapshot older than 10 min and keeps its severity', () => {
    const old = snapshot(90, 10, '2026-09-09T17:40:00Z');
    const s = buildStatus(old, [], DEFAULT_SETTINGS, NOW);
    expect(s.age_seconds).toBe(1200);
    expect(s.stale).toBe(true);
    expect(s.severity).toBe('critical');
  });

  it('is fresh at exactly 10 min', () => {
    const s = buildStatus(snapshot(1, 1, '2026-09-09T17:50:00Z'), [], DEFAULT_SETTINGS, NOW);
    expect(s.age_seconds).toBe(600);
    expect(s.stale).toBe(false);
  });
});

describe('history', () => {
  it('flattens a snapshot into one point', () => {
    const [p] = appendHistory([], snapshot(), NOW);
    expect(p).toEqual({
      t: '2026-09-09T18:00:00Z',
      'claude.session': 11,
      'claude.weekly': 60,
      'codex.session': 12,
      'codex.weekly': 6,
      'codex.reserve': 10,
    });
  });

  it('drops samples older than 24 h', () => {
    const stale: HistoryPoint[] = [{ t: '2026-09-08T17:00:00Z' }, { t: '2026-09-09T17:00:00Z' }];
    const next = appendHistory(stale, snapshot(), NOW);
    expect(next.map((p) => p.t)).toEqual(['2026-09-09T17:00:00Z', '2026-09-09T18:00:00Z']);
  });

  it('caps the buffer at 480 samples', () => {
    let h: HistoryPoint[] = [];
    for (let i = 0; i < 500; i++) {
      h = appendHistory(h, snapshot(i % 100, 0, new Date(NOW - (500 - i) * 1000).toISOString()), NOW);
    }
    expect(h).toHaveLength(480);
  });

  it('downsamples to at most 96 points, keeping the ends', () => {
    const points = Array.from({ length: 480 }, (_, i) => ({ t: String(i) }));
    const out = downsample(points);
    expect(out).toHaveLength(MAX_STATUS_POINTS);
    expect(out[0].t).toBe('0');
    expect(out[out.length - 1].t).toBe('479');
    expect(downsample(points.slice(0, 12))).toHaveLength(12);
  });
});

describe('merge variables', () => {
  it('lists every window per provider, weekly ones first', () => {
    const status = buildStatus(snapshot(), [], DEFAULT_SETTINGS, NOW);
    const v = buildMergeVariables(status, NOW);
    expect(v.layout_version).toBe(3);
    expect(v.display_word).toBe('left');
    expect(v.updated).toBe('18:00');
    expect(v.stale).toBe(false);
    expect(v.claude_present).toBe(true);
    expect(v.claude_plan).toBe('Anthropic · Max');
    // No hero any more: the whole list travels, weekly first, declared order inside a group.
    expect(v.claude_windows).toEqual([
      {
        id: 'weekly', label: 'Week', value: 40, reset: 'Resets in 2 d 18 h',
        state: 'warn', state_label: 'Running low', weekly: true,
      },
      {
        id: 'session', label: '5 h', value: 89, reset: 'Resets in 2 h 10 min',
        state: 'ok', state_label: 'Ready', weekly: false,
      },
    ]);
    expect(v.codex_plan).toBe('ChatGPT Plus');
    // Codex reserve refills weekly, so it rides with the weekly group, ahead of the session.
    expect((v.codex_windows as MergeWindow[]).map((w) => [w.id, w.value, w.weekly])).toEqual([
      ['weekly', 94, true],
      ['reserve', 90, true],
      ['session', 88, false],
    ]);
    expect(JSON.stringify(v).length).toBeLessThan(4096);
  });

  it('gives the per-model weekly window its own row', () => {
    const v = buildMergeVariables(buildStatus(withFable(), [], DEFAULT_SETTINGS, NOW), NOW);
    const rows = v.claude_windows as MergeWindow[];
    expect(rows.map((w) => w.id)).toEqual(['weekly', 'weekly_fable', 'session']);
    const fable = rows[1];
    expect(fable.label).toBe('Week · Fable');
    expect(fable.value).toBe(82);
    expect(fable.weekly).toBe(true);
    expect(fable.state).toBe('ok');
  });

  it('counts the used percent when the display says used', () => {
    const status = buildStatus(snapshot(), [], { ...DEFAULT_SETTINGS, display: 'used' }, NOW);
    const v = buildMergeVariables(status, NOW);
    expect(v.display_word).toBe('used');
    expect((v.claude_windows as MergeWindow[]).map((w) => w.value)).toEqual([60, 11]);
  });

  it('formats countdowns', () => {
    expect(formatResetsIn('2026-09-09T18:45:00Z', NOW)).toBe('45m');
    expect(formatResetsIn('2026-09-12T16:00:00Z', NOW)).toBe('2d 22h');
    expect(formatResetsIn('2026-09-09T17:00:00Z', NOW)).toBe('now');
    expect(formatResetsIn(null, NOW)).toBe('');
    expect(formatClock('nope')).toBe('--:--');
  });

  it('never turns a missing window into 100% left', () => {
    // Regression: a ChatGPT Pro plan has no `session` window. The board used to
    // read the absent value as 0% used and print a full Codex ring.
    const base = snapshot();
    const noSession: Snapshot = {
      ...base,
      providers: {
        ...base.providers,
        codex: { plan: 'chatgpt pro 5x', windows: [], extra: undefined },
      },
    };
    const v = buildMergeVariables(buildStatus(noSession, [], DEFAULT_SETTINGS, NOW), NOW);
    expect(v.codex_present).toBe(true);
    expect(v.codex_windows).toEqual([]);
    expect(v.codex_plan).toBe('chatgpt pro 5x');
  });

  it('drops a window whose percentage is out of range instead of drawing it empty', () => {
    const base = snapshot();
    base.providers.claude.windows[1].percent = 140;
    const v = buildMergeVariables(buildStatus(base, [], DEFAULT_SETTINGS, NOW), NOW);
    expect((v.claude_windows as MergeWindow[]).map((w) => w.id)).toEqual(['session']);
  });

  it('says nothing at all about a provider the collector never sent', () => {
    const status = buildStatus({ collected_at: '2026-09-09T18:00:00Z', providers: {} }, [], DEFAULT_SETTINGS, NOW);
    const v = buildMergeVariables(status, NOW);
    expect(v.codex_present).toBe(false);
    expect(v.codex_plan).toBeUndefined();
    expect(v.codex_windows).toBeUndefined();
  });

  it('says Gemini is absent when the collector skipped it', () => {
    const v = buildMergeVariables(buildStatus(snapshot(), [], DEFAULT_SETTINGS, NOW), NOW);
    expect(v.gemini_present).toBe(false);
    expect(v.gemini_windows).toBeUndefined();
  });

  it('treats every Gemini bucket as a weekly window', () => {
    const v = buildMergeVariables(buildStatus(withGemini(), [], DEFAULT_SETTINGS, NOW), NOW);
    expect(v.gemini_present).toBe(true);
    expect(v.gemini_plan).toBe('Google · Standard');
    const rows = v.gemini_windows as MergeWindow[];
    expect(rows.map((w) => [w.label, w.value, w.weekly])).toEqual([
      ['3 Pro', 38, true],
      ['2.5 Pro', 71, true],
    ]);
    expect(rows[0].reset).toBe('Resets in 5 h 15 min');
    expect(rows[0].state).toBe('warn');
  });

  it('sorts weekly windows first whatever the provider vocabulary', () => {
    expect(isWeeklyWindow('claude', 'weekly')).toBe(true);
    expect(isWeeklyWindow('claude', 'weekly_fable')).toBe(true);
    expect(isWeeklyWindow('claude', 'session')).toBe(false);
    expect(isWeeklyWindow('codex', 'reserve')).toBe(true);
    expect(isWeeklyWindow('codex', 'session')).toBe(false);
    expect(isWeeklyWindow('gemini', 'gemini-3-pro')).toBe(true);
  });

  it('marks every window of a stale snapshot out of date', () => {
    const v = buildMergeVariables(
      buildStatus(snapshot(11, 60, '2026-09-09T17:00:00Z'), [], DEFAULT_SETTINGS, NOW),
      NOW,
    );
    expect(v.stale).toBe(true);
    expect((v.claude_windows as MergeWindow[]).every((w) => w.state === 'stale')).toBe(true);
    expect((v.claude_windows as MergeWindow[])[0].state_label).toBe('Out of date');
  });

  it('stays inside the TRMNL payload budget with three providers and eight windows', () => {
    const wide = withGemini();
    wide.providers.claude.windows.push(
      { id: 'weekly_fable', label: 'Week · Fable', percent: 18, resets_at: '2026-09-12T12:00:00Z' },
    );
    wide.providers.gemini.windows.push(
      { id: 'gemini-3-flash', label: '3 Flash', percent: 10, resets_at: '2026-09-09T21:00:00Z' },
    );
    const v = buildMergeVariables(buildStatus(wide, [], DEFAULT_SETTINGS, NOW), NOW);
    expect(JSON.stringify({ merge_variables: v }).length).toBeLessThan(4096);
  });
});

describe('a third provider', () => {
  it('rides the same contract with no id the worker knows', () => {
    const parsed = validateSnapshot(JSON.parse(JSON.stringify(withGemini())));
    expect(parsed?.providers.gemini.windows).toHaveLength(2);
    expect(parsed?.providers.gemini.plan).toBe('Standard');
    expect(parsed?.providers.gemini.extra).toEqual({ buckets: 4 });
  });

  it('counts towards severity and history like any other', () => {
    const critical = withGemini();
    critical.providers.gemini.windows[0].percent = 91;
    expect(worstSeverity(critical, DEFAULT_SETTINGS)).toBe('critical');
    const status = buildStatus(critical, appendHistory([], critical, NOW), DEFAULT_SETTINGS, NOW);
    expect(status.history[0]['gemini.gemini-3-pro']).toBe(91);
    expect(status.providers.gemini.windows[1].label).toBe('2.5 Pro');
  });
});

describe('validation', () => {
  it('rejects junk and keeps a good snapshot', () => {
    expect(validateSnapshot(null)).toBeNull();
    expect(validateSnapshot({ providers: {} })).toBeNull();
    expect(validateSnapshot({ collected_at: 'nope', providers: {} })).toBeNull();
    const ok = validateSnapshot(snapshot());
    expect(ok?.providers.claude.windows).toHaveLength(2);
  });

  it('keeps a provider that only reports an error', () => {
    const s = validateSnapshot({
      collected_at: '2026-09-09T18:00:00Z',
      providers: { claude: { plan: null, windows: [], extra: { error: 'expired token' } } },
    });
    expect(s?.providers.claude.extra).toEqual({ error: 'expired token' });
  });

  it('defaults the display mode to remaining and echoes it in the status', () => {
    expect(DEFAULT_SETTINGS.display).toBe('remaining');
    expect(buildStatus(snapshot(), [], DEFAULT_SETTINGS, NOW).display).toBe('remaining');
    const used = { ...DEFAULT_SETTINGS, display: 'used' as const };
    const status = buildStatus(snapshot(), [], used, NOW);
    expect(status.display).toBe('used');
    // Thresholds keep running on the used percent whatever the display mode.
    expect(status.severity).toBe('warn');
  });

  it('round-trips the display mode and ignores an invalid one', () => {
    const used = normalizeSettings({ display: 'used' }, DEFAULT_SETTINGS);
    expect(used.display).toBe('used');
    expect(normalizeSettings({ display: 'sideways' }, used).display).toBe('used');
    expect(normalizeSettings({ display: 42 }, used).display).toBe('used');
    expect(normalizeSettings({}, used).display).toBe('used');
    expect(normalizeSettings({ display: 'remaining' }, used).display).toBe('remaining');
  });

  it('merges settings and only accepts https webhooks', () => {
    const s = normalizeSettings({ trmnl_webhook_url: 'https://trmnl.com/api/custom_plugins/x' }, DEFAULT_SETTINGS);
    expect(s.trmnl_webhook_url).toBe('https://trmnl.com/api/custom_plugins/x');
    expect(s.warn).toBe(60);
    expect(normalizeSettings({ trmnl_webhook_url: 'http://evil' }, s).trmnl_webhook_url).toBeNull();
    expect(normalizeSettings({ warn: 999 }, s).warn).toBe(60);
    expect(normalizeSettings({}, s).trmnl_webhook_url).toBe(s.trmnl_webhook_url);
  });
});


describe('truthful cached readings', () => {
  it('ages providers independently and does not invent history samples', () => {
    const snap = snapshot();
    snap.providers.claude.extra = { measured_at: '2026-09-09T17:40:00Z', error: 'HTTP 429' };
    const status = buildStatus(snap, appendHistory([], snap, NOW), DEFAULT_SETTINGS, NOW);
    expect(status.stale).toBe(false);
    expect(status.providers.claude.stale).toBe(true);
    expect(status.providers.codex.stale).toBe(false);
    expect(status.history[0]['claude.session']).toBeUndefined();
    expect((buildMergeVariables(status, NOW).claude_windows as MergeWindow[])[0].state).toBe('stale');
  });
  it('never converts malformed percentages into available capacity', () => {
    const snap = snapshot();
    snap.providers.claude.windows = [undefined, null, {}, { percent: '80' }, { percent: -1 }, { percent: 101 }] as never;
    expect(validateSnapshot(snap)?.providers.claude.windows).toEqual([]);
  });
});
