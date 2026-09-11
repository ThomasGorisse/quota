import {
  DEFAULT_SETTINGS,
  buildMergeVariables,
  buildStatus,
  appendHistory,
  bearerToken,
  normalizeSettings,
  validateSnapshot,
} from './logic';
import type { Env, HistoryPoint, Settings, Snapshot } from './types';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, OPTIONS',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type',
  'Access-Control-Max-Age': '86400',
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...CORS },
  });
}

const snapKey = (t: string) => `snap:${t}`;
const histKey = (t: string) => `hist:${t}`;
const settingsKey = (t: string) => `settings:${t}`;
const TRMNL_INDEX = 'tokens:trmnl';

async function readSettings(env: Env, token: string): Promise<Settings> {
  const raw = await env.QUOTA.get<Settings>(settingsKey(token), 'json');
  return { ...DEFAULT_SETTINGS, ...(raw ?? {}) };
}

async function updateTrmnlIndex(env: Env, token: string, enabled: boolean): Promise<void> {
  const list = (await env.QUOTA.get<string[]>(TRMNL_INDEX, 'json')) ?? [];
  const set = new Set(list);
  if (enabled) set.add(token);
  else set.delete(token);
  await env.QUOTA.put(TRMNL_INDEX, JSON.stringify([...set]));
}

/** Never logs the token: it is the credential. */
async function handle(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;

  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });

  if (!path.startsWith('/v1/')) {
    // Static assets are served ahead of the Worker; this is the fallback.
    return new Response('Quota', { status: 200, headers: { 'content-type': 'text/plain' } });
  }

  const token = bearerToken(request.headers.get('authorization'));
  if (!token) return json({ error: 'unauthorized' }, 401);

  if (path === '/v1/ingest' && request.method === 'POST') {
    let body: unknown;
    try {
      body = await request.json();
    } catch {
      return json({ error: 'invalid json' }, 400);
    }
    const snapshot = validateSnapshot(body);
    if (!snapshot) return json({ error: 'invalid snapshot' }, 400);

    const history = (await env.QUOTA.get<HistoryPoint[]>(histKey(token), 'json')) ?? [];
    const next = appendHistory(history, snapshot, Date.now());
    await Promise.all([
      env.QUOTA.put(snapKey(token), JSON.stringify(snapshot), { expirationTtl: 7 * 86400 }),
      env.QUOTA.put(histKey(token), JSON.stringify(next), { expirationTtl: 86400 }),
    ]);
    return json({ ok: true, samples: next.length });
  }

  if (path === '/v1/status' && request.method === 'GET') {
    const snapshot = await env.QUOTA.get<Snapshot>(snapKey(token), 'json');
    if (!snapshot) return json({ error: 'no data yet' }, 404);
    const history = (await env.QUOTA.get<HistoryPoint[]>(histKey(token), 'json')) ?? [];
    const settings = await readSettings(env, token);
    return json(buildStatus(snapshot, history, settings, Date.now()));
  }

  if (path === '/v1/settings') {
    if (request.method === 'GET') return json(await readSettings(env, token));
    if (request.method === 'PUT') {
      let body: unknown;
      try {
        body = await request.json();
      } catch {
        return json({ error: 'invalid json' }, 400);
      }
      const settings = normalizeSettings(body, await readSettings(env, token));
      await env.QUOTA.put(settingsKey(token), JSON.stringify(settings));
      await updateTrmnlIndex(env, token, !!settings.trmnl_webhook_url);
      return json(settings);
    }
  }

  return json({ error: 'not found' }, 404);
}

/** Pushes the latest snapshot of every TRMNL-enabled device to its webhook. */
async function pushToTrmnl(env: Env): Promise<void> {
  const tokens = (await env.QUOTA.get<string[]>(TRMNL_INDEX, 'json')) ?? [];
  const now = Date.now();
  await Promise.all(
    tokens.map(async (token) => {
      try {
        const settings = await readSettings(env, token);
        if (!settings.trmnl_webhook_url) return;
        const snapshot = await env.QUOTA.get<Snapshot>(snapKey(token), 'json');
        if (!snapshot) return;
        const status = buildStatus(snapshot, [], settings, now);
        await fetch(settings.trmnl_webhook_url, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ merge_variables: buildMergeVariables(status, now) }),
        });
      } catch (err) {
        console.error('trmnl push failed', (err as Error).message);
      }
    }),
  );
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await handle(request, env);
    } catch (err) {
      console.error('unhandled', (err as Error).message);
      return json({ error: 'internal error' }, 500);
    }
  },
  async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(pushToTrmnl(env));
  },
};
