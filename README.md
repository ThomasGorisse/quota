# Quota — your AI usage at a glance

Claude, Codex (ChatGPT) and Gemini rate limits, on every screen you look at: phone widget, web page on
any display, e-ink desk device, watch. One collector on the Mac where the sessions live, one
tiny backend, many thin clients.

## Live

- Worker: **https://quota.sceneview.dev** (Cloudflare, KV namespace `QUOTA`,
  cron `*/5 * * * *`). Deploy with `cd worker && npx wrangler deploy`.
- Dashboard: same host, `/#t=<token>`. The collector prints that pairing URL on first run
  and writes it to `~/.quota/pairing.txt` — it carries the device token, so treat it as a
  credential.
- Mac collector: LaunchAgent `com.gorisse.thomas.quota.collector`, every 180 s,
  `bash collector/install.sh`. Log: `~/.quota/collector.log`.
- Install: one prompt pasted into Claude Code, Codex CLI or Gemini CLI —
  *Read https://quota.sceneview.dev/install (curl it if you have no web access) and follow it
  to install Quota on this Mac.* The agent reads [`web/install.md`](web/install.md), served
  as markdown by the Worker at `/install` and `/install.md`. The zip stays as the manual path.

## Architecture

```
Mac (collector, LaunchAgent, every 3 min)
  ├─ Claude:  keychain "Claude Code-credentials" → GET api.anthropic.com/api/oauth/usage
  ├─ Codex:   ~/.codex/auth.json → GET chatgpt.com/backend-api/wham/usage
  └─ Gemini:  ~/.gemini/oauth_creds.json → POST cloudcode-pa.googleapis.com
              /v1internal:retrieveUserQuota   (absent credential → provider omitted)
        │  POST /v1/ingest  (Bearer <device token>)
        ▼
Cloudflare Worker + KV  (worker/)
  ├─ GET  /v1/status      → latest snapshot + 24 h history + stale flag   (all clients)
  ├─ GET  /               → web dashboard (web/), token in URL fragment    (any screen)
  ├─ GET  /install        → web/install.md as text/markdown          (one-prompt install)
  ├─ PUT  /v1/settings    → TRMNL webhook URL, alert thresholds, display mode
  └─ cron */5 → push merge_variables to TRMNL webhook (trmnl/)
        ▲
Android app + Glance widget (android/) · iOS app + WidgetKit + Watch (ios/)
```

Provider credentials are used only to authenticate with their provider; they never reach the relay. The backend receives usage percentages, reset times, plan labels, freshness metadata and a random pairing identifier.

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
    },
    "gemini": {
      "plan": "Standard",
      "windows": [
        {"id": "gemini-3-pro",   "label": "3 Pro",   "percent": 62, "resets_at": "2026-09-09T22:35:00Z"},
        {"id": "gemini-2-5-pro", "label": "2.5 Pro", "percent": 29, "resets_at": "2026-09-10T07:20:00Z"}
      ],
      "extra": {"buckets": 4}
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
  "display": "remaining",                      // how every client prints a percent: "remaining" | "used"
  "history": [{"t": "…", "claude.session": 11, "claude.weekly": 60, "codex.session": 12, "gemini.gemini-3-pro": 62}]
}
```

`GET /v1/settings` · `PUT /v1/settings` — per-device preferences, merged with the stored ones:

```json
{
  "trmnl_webhook_url": null,   // https:// only, anything else clears it
  "warn": 60,                  // 0–100, out-of-range values are ignored
  "critical": 85,              // 0–100
  "display": "remaining"       // "remaining" (default) | "used" — anything else is ignored
}
```

`display` is the one knob every client shares: `remaining` prints the capacity left (Claude's
apps), `used` prints the capacity spent (Codex's convention). It never changes the numbers on the
wire — `percent` is always **used** percent — nor the severity thresholds, which always run on
used percent. `GET /v1/status` echoes it as a top-level `display` field so a client only has to
read the status to render the same way as every other screen.

Auth: `Authorization: Bearer <token>` on every route; the token is a 32-byte random string the
collector generates on first run and prints as a pairing URL `https://<host>/#t=<token>`.

## Layout

| Dir | What |
|---|---|
| `collector/` | Python 3 script + LaunchAgent plist, zero dependencies |
| `worker/` | Cloudflare Worker (TypeScript), KV namespace `QUOTA` |
| `web/` | Dashboard, static, served by the worker at `/` |
| `android/` | Compose app + Glance widget, package `com.gorisse.thomas.quota` |
| `trmnl/` | Liquid template for the TRMNL plugin, plus `setup.sh` to provision it |
| `tools/` | `build-collector-zip.sh` rebuilds everything `web/downloads/` serves |
| `docs/` | Design tokens, decisions |

## Current delivery

Public setup: https://quota.sceneview.dev/setup.html — one prompt, with the zip as fallback.
Rebuild the downloads after touching `collector/` or `trmnl/`:
`bash tools/build-collector-zip.sh` (reproducible; an unchanged collector produces no diff).

See [the complete platform handoff](docs/RELEASE-2026-09-11.md) for all original target devices and verified release state.
