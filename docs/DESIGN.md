# Quota design tokens

Dark first (it lives on a second screen, all day). Light must work too.

- Background `#0B0F14` dark / `#F6F7F9` light. Surface `#141A22` / `#FFFFFF`.
- Text primary `#F3F4F6` / `#111827`; secondary `#9AA4B2` / `#6B7280`.
- Severity: ok `#22C55E`, warn `#F59E0B`, critical `#EF4444`, stale `#6B7280` (grey ring, no colour).
- Provider accents: Claude `#D97757` in both schemes; Codex `#ECECEC` on dark / `#0D0D0D` on
  light — OpenAI's current brand is monochrome, and the legacy ChatGPT green `#10A37F` is
  retired; Gemini the Google gradient `#4285F4 → #9B72CB → #D96570`, swept where the platform
  makes it cheap (CSS `conic-gradient`, Android `SweepGradient`, SwiftUI `AngularGradient`)
  and flattened to `#8E75B2` where it does not (Glance widget bitmaps, e-ink).
- Gauge: ring, stroke 12 (phone) / 8 (widget), track at 15 % opacity. Centre: the big number,
  below it "left" or "used", under the ring "resets in 2 h 10".
- Display mode (`display` in `GET /v1/status`, one setting for every client): `remaining` is the
  default — the number you want to read is what is left, and the arc empties as you spend.
  `used` is Codex's convention — the number is what is spent and the arc fills. Same colours,
  same thresholds: severity always runs on the used percent.
- Provider marks: the official Claude spark, OpenAI logo and Gemini spark, `fill: currentColor`
  tinted with the provider accent, in a 12-radius square at 12 % of that accent.
  See [`brand/NOTICE.md`](brand/NOTICE.md) — trademarks, used nominatively.
- Type: system (Inter / SF / Roboto), tabular numbers, 600 for the big number.
- Provider order, everywhere: Claude, Codex, Gemini. A provider the backend does not report
  is **absent**, not an empty card: no Gemini data means no Gemini card, no Gemini ring.
- Radius 20 cards, 12 chips. Spacing 8-grid.
- Motion: ring animates on value change only, 400 ms ease-out. Nothing else moves.
