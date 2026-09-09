# Gemini UI/UX critique — Quota web

- **Model**: `gemini-3.1-pro-preview` (Google Gemini API, `generateContent`)
- **Date**: 2026-09-09
- **Images sent** (in this order):
  - `web/docs/quota-1920x1080.png`
  - `web/docs/quota-1920x1080-used.png`
  - `web/docs/quota-1024x768.png`
  - `web/docs/quota-390x844.png`
  - `web/docs/quota-390x844-used.png`
- **Prompt**: senior product designer (bar: Apple Fitness rings, Linear, Grafana); glanceable
  status surface read in one second at two metres, on desktop, phone-on-stand and a 7" display.
  Asked for the 8 highest-impact changes ranked, then 3 things not to touch.

Answer reproduced verbatim below.

---

## The 8 changes, ranked by impact

**1. Kill the "Used" mode entirely — it destroys the 2-metre glanceability**
*   **WHAT:** Remove the Left/Used toggle. Hardcode the entire application to the "Left" (gas tank) mental model. 
*   **WHY:** You are using the exact same physical arc length and colours to mean opposite things. A 90% full green ring means "Lots left, good" in one mode, and "Almost entirely consumed, bad" in the other. At two metres, the user cannot read the 12px "used/left" label. They just see a green circle. If the state changes, the visual truth inverts. This is a fatal flaw for a status board.
*   **HOW:** Delete the `<button>` group for Left/Used. Standardize the data pipeline to only output remaining quota.

**2. Delete the sparklines — they violate your own product definition**
*   **WHAT:** Completely remove the "Last 24 hours" charts and their associated legends at the bottom of the cards.
*   **WHY:** You explicitly stated this is "not an analytics tool" but a "glanceable status surface". A 1px thick dark-red line on a dark-grey background is literally invisible at two metres. They are pure decoration that steals ~120px of critical vertical space on every form factor.
*   **HOW:** Apply `display: none` to the chart container. Re-allocate that vertical space to center the rings vertically within the cards.

**3. Fix the "inverted light theme" contrast on secondary text**
*   **WHAT:** Increase the contrast and size of the "resets in X h" and "updated X s ago" text.
*   **WHY:** The current text is a mid-grey (looks like `#8A919E`) on a dark background (`#1A1D24`). It fails WCAG contrast ratios and blurs into the background at two metres. 
*   **HOW:** Change the colour to `#D1D5DB` (or `rgba(255, 255, 255, 0.8)`). Increase the font size from ~14px to `18px` on desktop/tablet, and `16px` on mobile. Change the font-weight to `500`.

**4. Anchor the ring titles ("5 h", "Week") to the rings**
*   **WHAT:** Enlarge the time-horizon labels and move them closer to the rings.
*   **WHY:** At two metres, the eye sees a giant "89%" but the label telling you *what* is 89% ("5 h") is tiny and floating too far above the ring. The hierarchy is broken; the context is disconnected from the data.
*   **HOW:** Increase the font size of "5 h" and "Week" to `22px`. Set font-weight to `600`. Set colour to `#F3F4F6`. Reduce the `margin-bottom` (or gap) between this label and the SVG ring to `12px`.

**5. Standardize the Reserve gauge**
*   **WHAT:** Promote the Reserve gauge to be a primary ring, identical in size and styling to the "5 h" and "Week" rings.
*   **WHY:** Tucking it into the bottom right corner breaks the grid, ruins the symmetry of the Codex card versus the Claude card, and forces the text to be so small it is unreadable at a distance. If it's a quota, treat it like a quota.
*   **HOW:** On desktop and tablet, change the Codex card to a 3-column grid and place Reserve as the third equal-sized ring. On mobile (390x844), place it full-width below the first two rings, matching their exact diameter (approx `160px`).

**6. Purge the header boilerplate**
*   **WHAT:** Delete the text "Your AI usage at a glance" and the "Demo" toggle.
*   **WHY:** "Your AI usage at a glance" is marketing copy on a utilitarian dashboard. It tells the user what they already know and pushes the actual data down the screen. The Demo toggle is dev-only UI leaking into production space.
*   **HOW:** Remove the DOM nodes. Vertically center the "Quota" logo with the "Watch usage" button and the "updated..." timestamp. Shrink the header's total height to `64px`.

**7. De-clutter the inside of the rings**
*   **WHAT:** Shrink the `%` sign and delete the word "left" from inside the rings.
*   **WHY:** The `%` sign is currently so large it competes with the actual integer. Because we are standardizing the entire board to "Left" (Change 1) and adding a footer note ("Every number is what's left"), repeating the word "left" inside every single ring is redundant noise.
*   **HOW:** Target the `%` span and set `font-size: 0.4em` relative to the number. Delete the `<div class="label">left</div>` node entirely.

**8. Increase the contrast of the empty ring tracks**
*   **WHAT:** Change the background stroke of the rings (the "empty" part of the gas tank) to a neutral, higher-contrast grey.
*   **WHY:** Currently, the empty track uses a very dark, low-opacity tint of the active colour (e.g., dark green). On the `#1A1D24` background, it vanishes at two metres. If the user cannot see the full circle, they cannot accurately judge the proportion of the filled arc.
*   **HOW:** Change the `stroke` property of the background SVG circle to `#374151` (Gray-700) or `rgba(255, 255, 255, 0.15)`. Do not tint it green or amber.

***

## 3 things that already work — do not touch

**1. The primary integer typography**
The massive, bold, stark-white numbers (88, 94, 89) inside the rings are perfectly executed. They are the only elements that truly respect the "one second from two metres" rule. Do not shrink them, do not change their font weight, and do not make them coloured.

**2. The card-level grouping**
Splitting Claude and Codex into two massive, distinct cards with their respective logos anchored in the top-left is excellent. It creates an immediate, unambiguous mental model of "Provider A" vs "Provider B" without requiring the user to read any subheadings.

**3. The active ring colour palette**
The vibrant neon green and the stark amber are excellent choices for a dark theme. They have high luminosity, they pop beautifully against the dark background, and they immediately convey status (Green = Safe, Amber = Warning) before the brain even processes the numbers. Keep these exact hex codes.
