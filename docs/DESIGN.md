# Quota design tokens

Dark first (it lives on a second screen, all day). Light must work too.

- Background `#0B0F14` dark / `#F6F7F9` light. Surface `#141A22` / `#FFFFFF`.
- Text primary `#F3F4F6` / `#111827`; secondary `#9AA4B2` / `#6B7280`.
- Severity: ok `#22C55E`, warn `#F59E0B`, critical `#EF4444`, stale `#6B7280` (grey ring, no colour).
- Provider accents: Claude `#D97757`, Codex `#10A37F`.
- Gauge: ring, stroke 12 (phone) / 8 (widget), track at 15 % opacity, value = **remaining**, not used
  (the number you want to read is what is left). Centre: big remaining %, below: "resets in 2 h 10".
- Type: system (Inter / SF / Roboto), tabular numbers, 600 for the big number.
- Radius 20 cards, 12 chips. Spacing 8-grid.
- Motion: ring animates on value change only, 400 ms ease-out. Nothing else moves.
