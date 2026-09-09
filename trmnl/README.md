# Quota on TRMNL

A private plugin with the **webhook** strategy: the Cloudflare Worker pushes the latest
snapshot every 5 minutes, TRMNL redraws on its own refresh schedule.

## Set it up (4 steps)

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

   The next cron tick (within 5 minutes) delivers the first payload. To force one now,
   POST the same body shape the worker sends:

   ```bash
   curl -X POST https://trmnl.com/api/custom_plugins/<PLUGIN_SETTINGS_UUID> \
     -H 'content-type: application/json' \
     -d '{"merge_variables":{"claude_session":18,"claude_weekly":61,"codex_session":10,"codex_weekly":81,"codex_reserve":10,"claude_session_resets_in":"3h 1m","claude_weekly_resets_in":"2d 19h","codex_session_resets_in":"4h 54m","codex_weekly_resets_in":"1h 58m","codex_reserve_resets_in":"3d 22h","severity":"warn","updated":"18:48","stale":false}}'
   ```

Remove the integration with `-d '{"trmnl_webhook_url":null}'`; the worker drops the token
from its `tokens:trmnl` index and stops pushing.

## Merge variables

| Variable | Type | Meaning |
|---|---|---|
| `claude_session`, `claude_weekly` | int | percent **used** in that window |
| `codex_session`, `codex_weekly`, `codex_reserve` | int | percent **used** |
| `gemini_session`, `gemini_weekly` | int | percent **used**, most-consumed model first |
| `gemini_present` | bool | false until the Gemini CLI is paired; the third column only renders when true |
| `<name>_label` | string | the window's own name (`5 h`, `Week`, `3 Pro`) |
| `<name>_resets_in` | string | `"2h 10m"`, `"3d 4h"`, `"now"`, or `""` if unknown |
| `severity` | string | `ok` · `warn` · `critical` (worst window) |
| `updated` | string | `HH:MM`, UTC, of the last collection |
| `stale` | bool | true if the collector has been silent for over 10 min |

The variables carry **used** percent, like every other Quota client; the template renders
`100 - used` because the number worth reading on a desk device is what is left. The whole
payload is well under TRMNL's 2 kb limit.

## Notes

- 1-bit e-ink: no colour, no opacity. The ring track is a dashed hairline, the value arc a
  solid 14 px stroke — severity is carried by the number and the footer word, not by hue.
- The template reads each variable both flat (`{{ claude_session }}`) and namespaced
  (`{{ merge_variables.claude_session }}`), so it survives either TRMNL payload shape.
