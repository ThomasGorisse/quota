# App icon candidates — Quota

Generated with Gemini `gemini-3-pro-image` (flat vector, no text, single centred mark,
mark kept inside the central 66 % so the OS rounded-square mask never clips it).
Source of the first three marks: the "The app icon — 3 alternative marks" section of
[`docs/ux/gemini-android-2026-09-09.md`](../../ux/gemini-android-2026-09-09.md); the
fourth is ours. Contact sheet at 512 / 96 / 48 px against the current icon: [`sheet.png`](./sheet.png).

| File | What it is | Why it might win |
|---|---|---|
| [`01-battery-q.png`](./01-battery-q.png) | A chunky letter Q on `#0B0F14`; the ring is split down the middle, left half green `#22C55E`, right half off-white `#F3F4F6`, like a half-drained gauge. | The only candidate that carries the name. Reads as "Q" and as "measurement" in the same stroke, and the heavy letterform survives 48 px where the current thin ring does not. |
| [`02-dual-gauge.png`](./02-dual-gauge.png) | Two interlocking thick rings, one navy one off-white, on a bright green `#22C55E` field — the green-background variant. | The only light-field icon of the set: it is the one that pops on a bright wallpaper and next to white Google icons, where all the dark candidates go quiet. Two rings = two providers. |
| [`03-depletion-dash.png`](./03-depletion-dash.png) | Three left-aligned pills on `#0B0F14`, descending: green 100 %, amber 60 %, red 25 %. | Tells the actual product story — *several* limits at a glance, not one. No thin strokes at all, so it is the most robust shape of the set at small size, and the traffic-light order is legible before you know what the app does. |
| [`04-fuel-wedge.png`](./04-fuel-wedge.png) | Ours: one solid disc as a two-tone pie, three quarters green `#22C55E`, an amber `#F59E0B` wedge cut out at the upper right, separated by a navy notch. | Biggest ink coverage of the four, so it is the loudest at 48 px and the easiest to recognise blurred or in a folder. Purely a fill, never a stroke — immune to the mush that killed the current ring. |

## Recommendation (ranked)

1. **`03-depletion-dash`** — it is the only mark that says *multiple* quotas, which is the
   whole point of the widget, and it is the safest silhouette at 48 px.
2. **`04-fuel-wedge`** — strongest presence and cleanest at small size, but a two-tone pie
   is a common analytics shape; it wins on legibility, not on ownership.
3. **`01-battery-q`** — most brandable of the four and holds up at 48 px, though the tail
   is the first detail to go on a busy wallpaper.
4. **`02-dual-gauge`** — keep it as the light-background variant to test against bright
   wallpapers; on its own the interlocked rings are the least self-explanatory of the set.

Rejected and regenerated once: the first `02` (teal + terracotta on green) lost all contrast
and collapsed into a blob at 48 px; the first `01` had too thin a stroke.
