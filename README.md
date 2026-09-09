# Quota — your AI usage at a glance

Claude and Codex (ChatGPT) rate limits, on every screen you look at: phone widget, web page on
any display, e-ink desk device, watch. One collector on the Mac where the sessions live, one
tiny backend, many thin clients.

## Live

- Worker: **https://quota.mcp-tools-lab.workers.dev** (Cloudflare, KV namespace `QUOTA`,
  cron `*/5 * * * *`). Deploy with `cd worker && npx wrangler deploy`.
- Dashboard: same host, `/#t=<token>`. The collector prints that pairing URL on first run
  and writes it to `~/.quota/pairing.txt` — it carries the device token, so treat it as a
  credential.
- Mac collector: LaunchAgent `com.gorisse.thomas.quota.collector`, every 180 s,
  `bash collector/install.sh`. Log: `~/.quota/collector.log`.

## Architecture

```
Mac (collector, LaunchAgent, every 3 min)
  ├─ Claude:  keychain "Claude Code-credentials" → GET api.anthropic.com/api/oauth/usage
  └─ Codex:   ~/.codex/auth.json → GET chatgpt.com/backend-api/wham/usage
        │  POST /v1/ingest  (Bearer <device token>)
        ▼
Cloudflare Worker + KV  (worker/)
  ├─ GET  /v1/status      → latest snapshot + 24 h history + stale flag   (all clients)
  ├─ GET  /               → web dashboard (web/), token in URL fragment    (any screen)
  ├─ PUT  /v1/settings    → TRMNL webhook URL, alert thresholds
  └─ cron */5 → push merge_variables to TRMNL webhook (trmnl/)
        ▲
Android app + Glance widget (android/) · iOS app + WidgetKit + Watch (ios/, phase 2)
```

Tokens never leave the Mac. The backend only ever sees percentages and reset times.

## API contract (v1) — all clients depend on this, change it in one PR with every client

`POST /v1/ingest` — body sent by the collector:

```json
{
  "collected_at": "2026-09-09T17:20:00Z",
  "providers": {
    "claude": {
      "plan": "max",
      "windows": [
        {"id": "session", "label": "5 h",  "percent": 11, "resets_at": "2026-09-09T19:50:00Z"},
        {"id": "weekly",  "label": "Week", "percent": 60, "resets_at": "2026-09-12T12:00:00Z"}
      ],
      "extra": {"extra_usage_enabled": false}
    },
    "codex": {
      "plan": "plus",
      "windows": [
        {"id": "session", "label": "5 h",     "percent": 12, "resets_at": "2026-09-09T19:37:07Z"},
        {"id": "weekly",  "label": "Week",    "percent": 6,  "resets_at": "2026-09-15T13:43:08Z"},
        {"id": "reserve", "label": "Reserve", "percent": 10, "resets_at": "2026-09-13T15:36:54Z"}
      ],
      "extra": {"astra_available": true, "limit_reached": false}
    }
  }
}
```

`GET /v1/status` — what every client renders:

```json
{
  "collected_at": "…", "age_seconds": 42, "stale": false,
  "providers": { … same as ingest … },
  "severity": "ok | warn | critical",          // worst window: <60 ok, 60–85 warn, >85 critical
  "history": [{"t": "…", "claude.session": 11, "claude.weekly": 60, "codex.session": 12, "codex.weekly": 6}]
}
```

Auth: `Authorization: Bearer <token>` on every route; the token is a 32-byte random string the
collector generates on first run and prints as a pairing URL `https://<host>/#t=<token>`.

## Layout

| Dir | What |
|---|---|
| `collector/` | Python 3 script + LaunchAgent plist, zero dependencies |
| `worker/` | Cloudflare Worker (TypeScript), KV namespace `QUOTA` |
| `web/` | Dashboard, static, served by the worker at `/` |
| `android/` | Compose app + Glance widget, package `com.gorisse.thomas.quota` |
| `trmnl/` | Liquid template for the TRMNL plugin |
| `docs/` | Design tokens, decisions |
