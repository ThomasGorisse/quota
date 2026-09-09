# Brand marks

`claude.svg` (the Claude spark), `openai.svg` and `gemini.svg` are the marks of the three
services Quota reads. They are **trademarks of Anthropic PBC, OpenAI, Inc. and Google LLC**
respectively, and are used here **nominatively** — only to identify which provider a card
shows. Quota is not affiliated with, endorsed by, or sponsored by any of them.

## Sources, pinned

| File | Source | Licence | Pinned version |
|---|---|---|---|
| `claude.svg` | [simple-icons](https://github.com/simple-icons/simple-icons) `claude.svg` | CC0 1.0 Universal | `simple-icons@16.30.0`, fetched 2026-09-09 |
| `openai.svg` | simple-icons `openai.svg` | CC0 1.0 Universal | `simple-icons@16.30.0`, fetched 2026-09-09 |
| `gemini.svg` | [@lobehub/icons-static-svg](https://github.com/lobehub/lobe-icons) `gemini-color.svg` | MIT | `@lobehub/icons-static-svg@1.95.0`, fetched 2026-09-09 |

**Never re-fetch `openai.svg` from `simple-icons@latest`.** It was removed from simple-icons
master after the 2025 OpenAI rebrand — the raw master URL now 404s, while npm 16.30.0 still
ships it. The vendored copy above is the record; a `@latest` fetch would silently empty it.

## Tints

The marks are drawn with `fill: currentColor` and tinted per provider — never recoloured to
anything but the tint below, never stretched, never altered path by path.

| Provider | Dark | Light |
|---|---|---|
| Claude | `#D97757` | `#D97757` |
| Codex | `#ECECEC` | `#0D0D0D` |
| Gemini | Google Gemini gradient `#4285F4 → #9B72CB → #D96570`, flat `#8E75B2` where a gradient is not cheap | same |

Codex has no hue on purpose: OpenAI's current brand is monochrome (the legacy ChatGPT green
`#10A37F` was retired in the 2025 rebrand), and pure black or pure white is unreadable on
Quota's own surfaces, so it takes the near-neutral of the theme it sits on.

`gemini.svg` is vendored in the colour form it was published in — a blue body under Google's
four-colour gradients (`#08B962`, `#F94543`, `#FABC12`) — and is kept only as provenance for
the path. The clients redraw that same single path under the blue → violet → pink sweep in the
table above: the same geometry, a different palette. Do not read the vendored file's stops as
the tint to use.
