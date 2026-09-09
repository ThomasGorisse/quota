import { beforeEach, describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import type { Env } from '../src/types';

const TOKEN = 'a'.repeat(43);
const AUTH = { Authorization: `Bearer ${TOKEN}` };

/** Just enough of KVNamespace for the routes and the cron. */
function fakeKv() {
  const store = new Map<string, string>();
  return {
    store,
    async get(key: string, type?: string) {
      const raw = store.get(key);
      if (raw === undefined) return null;
      return type === 'json' ? JSON.parse(raw) : raw;
    },
    async put(key: string, value: string) {
      store.set(key, value);
    },
  } as unknown as Env['QUOTA'] & { store: Map<string, string> };
}

function snapshotBody(at = new Date().toISOString()) {
  return {
    collected_at: at,
    providers: {
      claude: {
        plan: 'max',
        windows: [
          { id: 'session', label: '5 h', percent: 18, resets_at: '2026-09-09T19:50:00Z' },
          { id: 'weekly', label: 'Week', percent: 61, resets_at: '2026-09-12T12:00:00Z' },
        ],
      },
      codex: {
        plan: 'plus',
        windows: [{ id: 'session', label: '5 h', percent: 10, resets_at: null }],
      },
    },
  };
}

const req = (path: string, init: RequestInit = {}) =>
  new Request(`https://quota.example${path}`, init);

let env: Env & { QUOTA: ReturnType<typeof fakeKv> };

beforeEach(() => {
  env = { QUOTA: fakeKv() } as never;
});

describe('routes', () => {
  it('rejects a missing or malformed bearer token', async () => {
    expect((await worker.fetch(req('/v1/status'), env)).status).toBe(401);
    const bad = req('/v1/status', { headers: { Authorization: 'Bearer nope' } });
    expect((await worker.fetch(bad, env)).status).toBe(401);
  });

  it('answers a CORS preflight from any origin', async () => {
    const res = await worker.fetch(req('/v1/status', { method: 'OPTIONS' }), env);
    expect(res.status).toBe(204);
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*');
  });

  it('404s before the first ingest, then serves the snapshot', async () => {
    expect((await worker.fetch(req('/v1/status', { headers: AUTH }), env)).status).toBe(404);

    const ingest = await worker.fetch(
      req('/v1/ingest', { method: 'POST', headers: AUTH, body: JSON.stringify(snapshotBody()) }),
      env,
    );
    expect(ingest.status).toBe(200);

    const res = await worker.fetch(req('/v1/status', { headers: AUTH }), env);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.severity).toBe('warn');
    expect(body.stale).toBe(false);
    expect(body.history).toHaveLength(1);
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*');
  });

  it('rejects a body that is not a snapshot', async () => {
    const res = await worker.fetch(
      req('/v1/ingest', { method: 'POST', headers: AUTH, body: '{"nope":1}' }),
      env,
    );
    expect(res.status).toBe(400);
  });

  it('keeps one device from reading another', async () => {
    await worker.fetch(
      req('/v1/ingest', { method: 'POST', headers: AUTH, body: JSON.stringify(snapshotBody()) }),
      env,
    );
    const other = req('/v1/status', { headers: { Authorization: `Bearer ${'b'.repeat(43)}` } });
    expect((await worker.fetch(other, env)).status).toBe(404);
  });

  it('stores settings and maintains the TRMNL index', async () => {
    const put = (body: unknown) =>
      worker.fetch(req('/v1/settings', { method: 'PUT', headers: AUTH, body: JSON.stringify(body) }), env);

    await put({ trmnl_webhook_url: 'https://trmnl.com/api/custom_plugins/abc', warn: 50 });
    expect(JSON.parse(env.QUOTA.store.get('tokens:trmnl')!)).toEqual([TOKEN]);
    const got = (await (await worker.fetch(req('/v1/settings', { headers: AUTH }), env)).json()) as {
      warn: number;
    };
    expect(got.warn).toBe(50);

    await put({ display: 'used' });
    await put({ display: 'sideways' });
    await worker.fetch(
      req('/v1/ingest', { method: 'POST', headers: AUTH, body: JSON.stringify(snapshotBody()) }),
      env,
    );
    const status = (await (
      await worker.fetch(req('/v1/status', { headers: AUTH }), env)
    ).json()) as { display: string };
    expect(status.display).toBe('used');

    await put({ trmnl_webhook_url: null });
    expect(JSON.parse(env.QUOTA.store.get('tokens:trmnl')!)).toEqual([]);
  });
});

describe('cron', () => {
  const run = async () => {
    const waits: Promise<unknown>[] = [];
    await worker.scheduled({} as never, env, {
      waitUntil: (p: Promise<unknown>) => waits.push(p),
    } as never);
    await Promise.all(waits);
  };

  it('posts merge variables to every configured webhook', async () => {
    const fetchMock = vi.fn(async () => new Response('ok'));
    vi.stubGlobal('fetch', fetchMock);

    await worker.fetch(
      req('/v1/ingest', { method: 'POST', headers: AUTH, body: JSON.stringify(snapshotBody()) }),
      env,
    );
    await worker.fetch(
      req('/v1/settings', {
        method: 'PUT',
        headers: AUTH,
        body: JSON.stringify({ trmnl_webhook_url: 'https://trmnl.com/api/custom_plugins/abc' }),
      }),
      env,
    );

    await run();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('https://trmnl.com/api/custom_plugins/abc');
    const body = JSON.parse(init.body as string);
    expect(body.merge_variables.claude_session).toBe(18);
    expect(body.merge_variables.codex_reserve).toBe(0);
    expect(body.merge_variables.severity).toBe('warn');
    expect(JSON.stringify(body).length).toBeLessThan(2048);
    vi.unstubAllGlobals();
  });

  it('does nothing when no device has a webhook', async () => {
    const fetchMock = vi.fn(async () => new Response('ok'));
    vi.stubGlobal('fetch', fetchMock);
    await run();
    expect(fetchMock).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
