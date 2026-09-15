# Quota on TRMNL

A private plugin with the **webhook** strategy: the Cloudflare Worker pushes the latest
snapshot every 5 minutes, TRMNL redraws on its own refresh schedule.

## Set it up

```bash
bash trmnl/setup.sh --dry-run   # prints every request, sends none
bash trmnl/setup.sh
```

The script needs a TRMNL **Developer** licence: that is what unlocks the Account API key
(trmnl.com → Account → API key, starts with `user_`). Put it in `~/.quota/trmnl-api-key`
(chmod 600) or in `$TRMNL_API_KEY`. It creates or reuses a private plugin named `Quota` with
the webhook strategy, uploads [`quota.liquid`](./quota.liquid) as `markup_full`, adds the
plugin to the device playlist, registers the webhook URL with the relay using the device
token from `~/.quota/config.json`, and renders a preview to `~/.quota/trmnl-preview.png`.

One step stays manual, and it is TRMNL's fault, not ours: **no endpoint exposes a private
plugin's UUID** — not the list, not `/details`, not `/archive` — and `POST
/api/devices/{id}/playlist_items` accepts only that UUID (the integer id answers *Plugin
setting not found*). So the script stops once, tells you to copy the Webhook URL from
`https://trmnl.com/plugin_settings/<id>/edit`, and you re-run it with
`--webhook-url <url>`. It remembers the URL in `~/.quota/trmnl-webhook` afterwards.

Verified against the live API on 2026-09-15: private plugins are `plugin_id` 37 (override
with `--plugin-id`); the markup linter **rejects a hand-written `view view--full` wrapper**,
so the template starts at `.layout`; screenshots take **no** `device_model` (`og` and
`og_png` are both refused) and their job ids expire within seconds.

## By hand instead

1. **Create the plugin.** TRMNL dashboard → *Plugins* → *Private Plugin* → *Add new*.
   Name it `Quota`, strategy **Webhook**. Save.
2. **Paste the markup.** Open the plugin's *Edit Markup* tab, select the **Full** view and
   paste [`quota.liquid`](./quota.liquid). Save. (The other views are optional; the
   template is written for 800 × 480.)
3. **Copy the webhook URL.** It is on the plugin page, of the form
   `https://trmnl.com/api/custom_plugins/<PLUGIN_SETTINGS_UUID>`.
4. **Tell the worker about it**, with your device token from `~/.quota/config.json`:

   ```bash
   TOKEN=$(python3 -c "import json,pathlib;print(json.load(open(pathlib.Path.home()/'.quota/config.json'))['token'])")
   curl -X PUT https://quota.sceneview.dev/v1/settings \
     -H "Authorization: Bearer $TOKEN" \
     -H 'content-type: application/json' \
     -d '{"trmnl_webhook_url":"https://trmnl.com/api/custom_plugins/<PLUGIN_SETTINGS_UUID>"}'
   ```

   The next cron tick (within 5 minutes) delivers the first payload. To preview the
   template before pairing the display, use this version 2 sample in TRMNL's markup editor:

   ```json
   {
     "layout_version": 2,
     "display_word": "left",
     "updated": "18:48",
     "stale": false,
     "claude_present": true,
     "claude_has_value": true,
     "claude_plan": "Anthropic · Max",
     "claude_value": 32,
     "claude_label": "Week",
     "claude_reset": "Resets in 2 d 14 h",
     "claude_state": "warn",
     "claude_state_label": "Running low",
     "claude_detail": "5 h 89% left",
     "codex_present": true,
     "codex_has_value": true,
     "codex_plan": "ChatGPT Plus",
     "codex_value": 8,
     "codex_label": "5 h",
     "codex_reset": "Resets in 37 min",
     "codex_state": "critical",
     "codex_state_label": "Nearly empty",
     "codex_detail": "Week 66% left · Reserve 90% left",
     "gemini_present": true,
     "gemini_has_value": true,
     "gemini_plan": "Google · Standard",
     "gemini_value": 38,
     "gemini_label": "3 Pro",
     "gemini_reset": "Resets in 5 h 15 min",
     "gemini_state": "warn",
     "gemini_state_label": "Running low",
     "gemini_detail": "2.5 Pro 71% left"
   }
   ```

   For a manual webhook POST, wrap those variables in `{"merge_variables": {...}}`.

Remove the integration with `-d '{"trmnl_webhook_url":null}'`; the worker drops the token
from its `tokens:trmnl` index and stops pushing.

## Merge variables

| Variable | Type | Meaning |
|---|---|---|
| `layout_version` | int | Must be `2`; earlier payloads show a waiting message |
| `display_word` | string | `left` or `used`, matching the device setting |
| `updated` | string | `HH:MM` UTC of the last collection |
| `stale` | bool | The overall snapshot is out of date |
| `<provider>_present` | bool | Only reported providers receive a column |
| `<provider>_has_value` | bool | A valid measured window exists; otherwise show “No data” |
| `<provider>_plan` | string | Provider and subscription label |
| `<provider>_value` | int | Display-ready percentage, already converted to left/used |
| `<provider>_label` | string | Most consumed window’s original label (`5 h`, `Week`, `3 Pro`) |
| `<provider>_reset` | string | Human-readable reset time |
| `<provider>_state` | string | `ok`, `warn`, `critical`, or `stale` (including provider cache age) |
| `<provider>_state_label` | string | Human-readable status |
| `<provider>_detail` | string | Up to three additional windows with percentages and display word |

`<provider>` is `claude`, `codex`, or `gemini`. The Worker sorts each provider’s valid
windows by usage and formats the variables. The template must **not** subtract the
value from 100 a second time. Unreported windows never become 100% remaining.

## Notes

- Designed for TRMNL OG, landscape **800 × 480**, full view. The three providers share
  equal-width columns, with 192 px rings and secondary limits below.
- 1-bit e-ink: no colour or opacity. The ring track is a dashed hairline; the value arc
  is solid. Critical status uses an inverted badge, warning an outline, and stale data
  a dashed outline and explicit “Out of date” label.
- The template accepts variables at the root or under `merge_variables`, and escapes
  provider strings before rendering.
- `.claude/skills/trmnl/` vendors TRMNL's own agent skill (MIT, usetrmnl/trmnl-agent-skills
  @ 3f9062e): the design-system rules this template has to satisfy.
- After saving the plugin, add it to the device playlist and confirm the physical
  display refreshes. The browser preview checks layout; actual device delivery requires
  the TRMNL dashboard and a connected device.
