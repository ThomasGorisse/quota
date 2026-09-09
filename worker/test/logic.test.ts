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
  normalizeSettings,
  severityFor,
  validateSnapshot,
  worstSeverity,
} from '../src/logic';
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
  it('flattens everything the TRMNL template needs', () => {
    const status = buildStatus(snapshot(), [], DEFAULT_SETTINGS, NOW);
    const v = buildMergeVariables(status, NOW);
    expect(v.claude_session).toBe(11);
    expect(v.claude_weekly).toBe(60);
    expect(v.codex_session).toBe(12);
    expect(v.codex_weekly).toBe(6);
    expect(v.codex_reserve).toBe(10);
    expect(v.claude_session_resets_in).toBe('2h 10m');
    expect(v.severity).toBe('warn');
    expect(v.updated).toBe('18:00');
    expect(v.stale).toBe(false);
    expect(JSON.stringify(v).length).toBeLessThan(2048);
  });

  it('formats countdowns', () => {
    expect(formatResetsIn('2026-09-09T18:45:00Z', NOW)).toBe('45m');
    expect(formatResetsIn('2026-09-12T16:00:00Z', NOW)).toBe('2d 22h');
    expect(formatResetsIn('2026-09-09T17:00:00Z', NOW)).toBe('now');
    expect(formatResetsIn(null, NOW)).toBe('');
    expect(formatClock('nope')).toBe('--:--');
  });

  it('defaults a missing provider to zero instead of throwing', () => {
    const status = buildStatus({ collected_at: '2026-09-09T18:00:00Z', providers: {} }, [], DEFAULT_SETTINGS, NOW);
    const v = buildMergeVariables(status, NOW);
    expect(v.codex_reserve).toBe(0);
    expect(v.codex_reserve_resets_in).toBe('');
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
