# Quota glance rules

Quota answers how much capacity is available at a glance. It does not predict hours of work
from a percentage. One gauge is one selected constraint; ring size never encodes a plan,
provider, number of windows, or available capacity.

## Selection and truth

- Provider order is Claude, Codex, Gemini. A provider omitted by `/v1/status` has no card.
  A reported provider with no valid windows says **No data**, never 100% left.
- A valid window has a finite USED percentage in 0…100. The hero is the valid window with
  the highest USED percentage. Ties retain backend window order. An overall hero compares
  providers in canonical order, retaining the first on a tie.
- Never choose by window id, shortest duration, or the current display mode. `session`,
  `weekly`, `reserve`, and model names have no selection privilege.
- Use the backend label, falling back to the id when empty. Keep the selected window's
  value, severity, identity and reset together. A reset does not refill a local value.
- `remaining` displays `100 - round(used)` and the arc empties; `used` displays `round(used)`
  and the arc fills. Selection and severity use the original USED value, before rounding.
- `stale` from the server wins; the existing client freshness limit is 600 seconds. A stale
  reading stays visible in grey and is labelled **Out of date**. Do not extend this limit
  just to keep an old Watch reading coloured.
- The collector retains failed providers for up to 24 hours with their original measurement time.
  `provider.measured_at` and `provider.stale` distinguish their freshness from the snapshot.
  Recompute age locally while offline. Omitted providers stay omitted; do not resurrect removed providers.

## Hierarchy

- Phone cards and the web: one fixed 112 dp/pt/px hero per provider in the compact row layout.
  Web desktop uses one 224 px gauge per provider, with intrinsic card height.
- Small widgets and Watch: one overall hero. Medium/large widgets: one overall hero with
  the other providers' selected windows as quiet text, in canonical order.
- TRMNL: one 192 px gauge per reported provider. All columns use the same gauge size,
  whether there are one, two or three providers.
- The number and `%` are one typographic value: **32%**. Put **left** or **used** directly
  underneath. In inline supporting text, use **Week 32% left**.
- Read the hero first, then its window label, then its reset. In vertical layouts the label
  sits below the gauge. Compact phone/web rows put the label and reset together beside it.
  Never put a standalone window heading above a gauge.
- Provider identity is a small official mark plus name; the plan is a subtitle, never a chip
  competing with the hero. Codex is always **Codex**, with **ChatGPT Pro**, **ChatGPT Plus**,
  or the reported ChatGPT plan underneath. Do not duplicate a `ChatGPT` prefix.
- Other windows are supporting text/chips, never additional rings. Reserve has no special
  placement or styling. Omit secondary windows before shrinking the hero on a small surface.
- History is optional web detail, off on entry. The phone glance has no history plot.
  There is no continuous motion, animated status badge, or animated reordering.

## Words and time

| USED | State | Colour |
|---|---|---|
| < 60 | Ready | `#22C55E` |
| 60…85 inclusive | Running low | `#F59E0B` |
| > 85 | Nearly empty | `#EF4444` |
| Stale | Out of date | `#6B7280` |

Durations are identical on every surface: **Resets in 37 min**, **Resets in 2 h 10 min**,
**Resets in 2 d 14 h**. Round positive time up to whole minutes; at a day or more show days
and whole remaining hours. Keep both units, including zero lower units. A past reset is
**Reset due**; missing or invalid time is **Reset time unknown**. No `2h09`, bare `2d`,
unlabelled minutes, or invented `5 h window` fallback.

Tiny widgets may replace the reset line with **Out of date**. Circular/corner system
accessories may omit a visible reset or full identity; their accessibility reading must
still identify the selected provider and window. They never fabricate a zero when empty.

## Tokens

- Background `#0B0F14` dark / `#F6F7F9` light. Surface `#141A22` / `#FFFFFF`.
- Text `#F3F4F6` / `#111827`; secondary `#9AA4B2` / `#6B7280`.
- Neutral ring track; never a dim version of the severity colour. Existing native track
  variants remain supported. Glance retains the translucent bitmap track for theme changes.
- Claude mark `#D97757`; Codex mark `#ECECEC` dark / `#0D0D0D` light. No legacy green.
- Gemini mark uses `#4285F4 → #9B72CB → #D96570`; Glance flattens it to `#8E75B2`.
  Stale brand marks are neutral; all e-ink marks are black. Reuse the vendored silhouettes
  and [brand notice](brand/NOTICE.md). Do not fetch replacement assets.
- System fonts, tabular numbers, hero weight 600. Glance uses its already-supported Medium
  weight; exact 600/tabular font control needs native verification before changing that API.
- Radius 20 cards / 12 chips; primary spacing on an 8 grid, 4 for compact text rhythm.
- Hero strokes 8 on phone, 6–8 on widgets/Watch. Web's 10-unit stroke scales with its SVG;
  TRMNL uses a 12-unit black stroke. Tracks stay neutral.
- The arc alone animates on value change, 400 ms ease-out. Respect reduced motion. Widget
  snapshots and e-ink never animate. State colour and text change immediately.

## E-ink semantics

White background, black ink, no greys, shadows or gradients. Ready has a plain label;
Running low has a solid outlined label; Nearly empty has a black label with white lettering;
Out of date has a dashed label and a dashed ring, retaining the last reported number.
These shapes carry the distinction without hue.

`buildMergeVariables` supplies layout version 3, `display_word`, and per provider its
presence, plan and `<provider>_windows`: every valid window, weekly buckets first, each with
its label, display-ready value, reset, state and `weekly` flag. No window is elected a hero
any more — the board shows all of them, so Claude's per-model `Week · Fable` bucket is a row
of its own rather than a fragment of a detail line. This private presentation adapter does
not change `GET /v1/status`. The Liquid template refuses legacy variables instead of guessing
missing values. Update the template and Worker together.

## 11 September visual update

Phone surfaces use a warm ivory light background and deep green-black dark background, generous
card spacing and quiet provider identity. Severity colors retain the same meaning. Web desk mode
offers opt-in Wake Lock and fullscreen; no background motion or decorative 3D is required.
