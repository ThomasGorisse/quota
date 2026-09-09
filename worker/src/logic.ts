import type {
  DisplayMode,
  HistoryPoint,
  Provider,
  Settings,
  Severity,
  Snapshot,
  Window,
} from './types';

/** A snapshot older than this is stale: the collector runs every 3 min. */
export const STALE_SECONDS = 600;
/** Ring buffer size kept in KV (one sample per ingest). */
export const MAX_HISTORY = 480;
/** History is also trimmed to this age. */
export const HISTORY_WINDOW_SECONDS = 24 * 3600;
/** Points returned by GET /v1/status. */
export const MAX_STATUS_POINTS = 96;

export const DISPLAY_MODES: readonly DisplayMode[] = ['remaining', 'used'];

export const DEFAULT_SETTINGS: Settings = {
  trmnl_webhook_url: null,
  warn: 60,
  critical: 85,
  display: 'remaining',
};

/** Device tokens are 32 random bytes, base64url, unpadded: 43 chars. */
const TOKEN_RE = /^[A-Za-z0-9_-]{43}$/;

export function isValidToken(token: unknown): token is string {
  return typeof token === 'string' && TOKEN_RE.test(token);
}

/** Extracts the bearer token from an Authorization header, or null. */
export function bearerToken(header: string | null): string | null {
  if (!header) return null;
  const m = /^Bearer\s+(\S+)$/i.exec(header.trim());
  if (!m) return null;
  return isValidToken(m[1]) ? m[1] : null;
}

/** <warn ok, warn..critical warn, >critical critical. */
export function severityFor(percent: number, s: Settings): Severity {
  if (percent > s.critical) return 'critical';
  if (percent >= s.warn) return 'warn';
  return 'ok';
}

const RANK: Record<Severity, number> = { ok: 0, warn: 1, critical: 2 };

/** Worst window across every provider. */
export function worstSeverity(snapshot: Snapshot, s: Settings): Severity {
  let worst: Severity = 'ok';
  for (const provider of Object.values(snapshot.providers ?? {})) {
    for (const w of provider.windows ?? []) {
      if (typeof w.percent !== 'number' || Number.isNaN(w.percent)) continue;
      const sev = severityFor(w.percent, s);
      if (RANK[sev] > RANK[worst]) worst = sev;
    }
  }
  return worst;
}

export function ageSeconds(collectedAt: string, now: number): number {
  const t = Date.parse(collectedAt);
  if (Number.isNaN(t)) return Number.MAX_SAFE_INTEGER;
  return Math.max(0, Math.round((now - t) / 1000));
}

export function isStale(age: number): boolean {
  return age > STALE_SECONDS;
}

function windowOf(p: Provider | undefined, id: string): Window | undefined {
  return p?.windows?.find((w) => w.id === id);
}

/** One flat history sample per ingest. */
export function historyPoint(snapshot: Snapshot): HistoryPoint {
  const point: HistoryPoint = { t: snapshot.collected_at };
  for (const [name, provider] of Object.entries(snapshot.providers ?? {})) {
    for (const w of provider.windows ?? []) {
      if (typeof w.percent === 'number') point[`${name}.${w.id}`] = w.percent;
    }
  }
  return point;
}

/** Appends a sample, drops anything older than 24 h, caps the buffer. */
export function appendHistory(
  history: HistoryPoint[],
  snapshot: Snapshot,
  now: number,
): HistoryPoint[] {
  const cutoff = now - HISTORY_WINDOW_SECONDS * 1000;
  const kept = (Array.isArray(history) ? history : []).filter((p) => {
    const t = Date.parse(p?.t as string);
    return !Number.isNaN(t) && t >= cutoff;
  });
  kept.push(historyPoint(snapshot));
  return kept.length > MAX_HISTORY ? kept.slice(kept.length - MAX_HISTORY) : kept;
}

/** Evenly spaced subset, first and last always kept. */
export function downsample<T>(points: T[], max = MAX_STATUS_POINTS): T[] {
  if (points.length <= max) return points.slice();
  const out: T[] = [];
  const step = (points.length - 1) / (max - 1);
  for (let i = 0; i < max; i++) out.push(points[Math.round(i * step)]);
  return out;
}

/** "2h 10m", "45m", "3d 4h", "now". */
export function formatResetsIn(resetsAt: string | null | undefined, now: number): string {
  if (!resetsAt) return '';
  const t = Date.parse(resetsAt);
  if (Number.isNaN(t)) return '';
  let secs = Math.round((t - now) / 1000);
  if (secs <= 0) return 'now';
  const d = Math.floor(secs / 86400);
  secs -= d * 86400;
  const h = Math.floor(secs / 3600);
  secs -= h * 3600;
  const m = Math.floor(secs / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

/** HH:MM in UTC. */
export function formatClock(iso: string): string {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return '--:--';
  return new Date(t).toISOString().slice(11, 16);
}

export interface StatusBody {
  collected_at: string;
  age_seconds: number;
  stale: boolean;
  providers: Record<string, Provider>;
  severity: Severity;
  /** Every client renders the same way; thresholds still run on used percent. */
  display: DisplayMode;
  history: HistoryPoint[];
}

export function buildStatus(
  snapshot: Snapshot,
  history: HistoryPoint[],
  settings: Settings,
  now: number,
): StatusBody {
  const age = ageSeconds(snapshot.collected_at, now);
  return {
    collected_at: snapshot.collected_at,
    age_seconds: age,
    stale: isStale(age),
    providers: snapshot.providers ?? {},
    severity: worstSeverity(snapshot, settings),
    display: settings.display ?? DEFAULT_SETTINGS.display,
    history: downsample(history ?? []),
  };
}

/**
 * The windows a provider declares, in the order it declared them. Gemini names its windows
 * after the models it meters, so nothing downstream may assume `session` / `weekly` exist.
 */
function declaredWindows(p: Provider | undefined): Window[] {
  return Array.isArray(p?.windows) ? p.windows : [];
}

/** Flat variables for the TRMNL webhook (max 2 kb of payload). */
export function buildMergeVariables(
  status: StatusBody,
  now: number,
): Record<string, string | number | boolean> {
  const claude = status.providers.claude;
  const codex = status.providers.codex;
  const gemini = status.providers.gemini;
  const vars: Record<string, string | number | boolean> = {
    severity: status.severity,
    updated: formatClock(status.collected_at),
    stale: status.stale,
  };
  const pairs: Array<[string, Provider | undefined, string]> = [
    ['claude_session', claude, 'session'],
    ['claude_weekly', claude, 'weekly'],
    ['codex_session', codex, 'session'],
    ['codex_weekly', codex, 'weekly'],
    ['codex_reserve', codex, 'reserve'],
  ];
  for (const [key, provider, id] of pairs) {
    const w = windowOf(provider, id);
    vars[key] = typeof w?.percent === 'number' ? Math.round(w.percent) : 0;
    vars[`${key}_resets_in`] = formatResetsIn(w?.resets_at, now);
  }

  // Gemini is positional, not named: the collector sends its buckets most-consumed first,
  // so window 0 is the headline and window 1 is the secondary. `gemini_present` is what the
  // template branches on — an absent Gemini must draw no column at all, not an empty one.
  const geminiWindows = declaredWindows(gemini);
  vars.gemini_present = geminiWindows.length > 0;
  vars.gemini_plan = gemini?.plan ?? '';
  for (const [index, key] of [[0, 'gemini_session'], [1, 'gemini_weekly']] as const) {
    const w = geminiWindows[index];
    vars[key] = typeof w?.percent === 'number' ? Math.round(w.percent) : 0;
    vars[`${key}_label`] = w?.label ?? '';
    vars[`${key}_resets_in`] = formatResetsIn(w?.resets_at, now);
  }
  return vars;
}

function parseWindows(raw: unknown): Window[] {
  if (!Array.isArray(raw)) return [];
  const out: Window[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue;
    const w = item as Record<string, unknown>;
    out.push({
      id: String(w.id ?? ''),
      label: String(w.label ?? ''),
      percent: typeof w.percent === 'number' ? w.percent : 0,
      resets_at: typeof w.resets_at === 'string' ? w.resets_at : null,
    });
  }
  return out;
}

/** Rejects anything that is not a plausible ingest body. */
export function validateSnapshot(body: unknown): Snapshot | null {
  if (!body || typeof body !== 'object') return null;
  const b = body as Record<string, unknown>;
  const collected =
    typeof b.collected_at === 'string' && !Number.isNaN(Date.parse(b.collected_at))
      ? b.collected_at
      : null;
  if (!collected) return null;
  if (!b.providers || typeof b.providers !== 'object') return null;
  const providers: Record<string, Provider> = {};
  for (const [name, raw] of Object.entries(b.providers as Record<string, unknown>)) {
    if (!raw || typeof raw !== 'object') continue;
    const p = raw as Record<string, unknown>;
    providers[name] = {
      plan: typeof p.plan === 'string' ? p.plan : null,
      windows: parseWindows(p.windows),
      extra:
        p.extra && typeof p.extra === 'object'
          ? (p.extra as Record<string, unknown>)
          : undefined,
    };
  }
  return { collected_at: collected, providers };
}

export function normalizeSettings(body: unknown, previous: Settings): Settings {
  const b = (body ?? {}) as Record<string, unknown>;
  const num = (v: unknown, fallback: number) =>
    typeof v === 'number' && v >= 0 && v <= 100 ? v : fallback;
  let url = previous.trmnl_webhook_url ?? null;
  if ('trmnl_webhook_url' in b) {
    const raw = b.trmnl_webhook_url;
    url = typeof raw === 'string' && raw.startsWith('https://') ? raw : null;
  }
  // An unknown display value is ignored, like an out-of-range threshold.
  const display = DISPLAY_MODES.includes(b.display as DisplayMode)
    ? (b.display as DisplayMode)
    : (previous.display ?? DEFAULT_SETTINGS.display);
  return {
    trmnl_webhook_url: url,
    warn: num(b.warn, previous.warn),
    critical: num(b.critical, previous.critical),
    display,
  };
}
