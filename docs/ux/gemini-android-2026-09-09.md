# Gemini UI/UX critique — Quota Android (app, widgets, Play assets)

- **Model**: `gemini-3.1-pro-preview` (Google Gemini API, `generateContent`)
- **Date**: 2026-09-09
- **Images sent** (in this order):
  - `android/docs/screenshots/home-dark.png`
  - `android/docs/screenshots/home-error.png`
  - `android/docs/screenshots/home-light.png`
  - `android/docs/screenshots/widget-2x1-dark.png`
  - `android/docs/screenshots/widget-2x1-light.png`
  - `android/docs/screenshots/widget-4x2-dark.png`
  - `android/docs/screenshots/widget-4x2-light.png`
  - `android/play/listing/en-US/icon.png`
  - `android/play/listing/en-US/feature-graphic.png`
  - `web/docs/quota-390x844.png` — sent as a consistency reference only, not for critique
- **Prompt**: same persona as the web critique, plus widget legibility at real physical size
  (4x2 ≈ 5 cm), the empty bottom half of the phone screen, the launcher icon judged at 48dp
  next to Google apps (with 3 alternative marks requested), and the feature graphic.

Answer reproduced verbatim below.

---

## The 8 changes, ranked by impact

**1. Widget 4x2: Information collapse at physical scale**
*   **WHAT:** Reduce the 4x2 widget from 5 rings to exactly 2 rings (one for Claude, one for Codex).
*   **WHY:** A 4x2 widget is roughly 7cm wide on a physical screen. Fitting 5 rings means each ring gets ~1.2cm of horizontal space including padding. The "89" text inside becomes ~6sp. The "2h09" subtext becomes ~4sp. It is illegible mush at a normal viewing distance, let alone at a glance.
*   **HOW:** Show only the primary "5 h" limit for each provider. Set ring diameter to 56dp. Set the value text ("89") to 24sp Bold. Set the label ("Claude") to 14sp Medium. Drop the "Week" and "Reserve" rings entirely from this surface.

**2. Web Consistency: The core metric lockup**
*   **WHAT:** Replace the Android app's stacked `89` over a tiny `% left` with the Web version's inline `89%` lockup.
*   **WHY:** The Android app looks like a generic dashboard template; the Web version looks like a premium tool (Linear/Grafana). The 10sp `% left` on Android is completely invisible at two metres. 
*   **HOW:** Set the value text to 48sp, weight 700. Add the `%` sign inline at 24sp, weight 500, baseline-aligned to the number. Remove the word "left" from the rings entirely and place a single "All numbers show % remaining" legend at the bottom of the screen.

**3. The Empty Bottom Half: Sparkline integration**
*   **WHAT:** Expand the Claude and Codex cards vertically to fill the empty bottom half of the screen by integrating the Web version's "Last 24 hours" sparklines.
*   **WHY:** Half the Android home screen is a black void. It feels unfinished and lacks the historical context that makes the Web version actually useful as an analytics surface.
*   **HOW:** Increase the height of both provider cards to 320dp. Add a 1dp stroke sparkline anchored to the bottom of each card (height: 64dp). Use `#4ADE80` (green) for the Codex line and `#F97316` (orange) for the Claude line.

**4. Colour Semantics: The "Full" illusion**
*   **WHAT:** Change the ring track (the empty background path) to a high-contrast solid colour and add a 2dp gap at the 12 o'clock position.
*   **WHY:** In glanceable UI (like Apple Fitness), a massive green ring means "100% used / goal achieved". Because your green ring means "89% *remaining*", it is cognitively backwards. It must look unmistakably like a container that is *emptying* (a battery), not a progress bar that is *filling*.
*   **HOW:** Set the empty track to `#27272A` (dark mode) and `#E4E4E7` (light mode). Add a 2dp transparent stroke at the top dead centre of the track to anchor the "100%" mark, making it visually obvious that the coloured bar is receding from the top.

**5. Widget 2x1: Microscopic labels**
*   **WHAT:** Remove the "Claude" and "Codex" text labels from the 2x1 widget entirely.
*   **WHY:** A 2x1 widget is roughly 3.5cm wide. The text labels render at an equivalent of 8sp. They are unreadable at a glance and steal vertical space from the rings.
*   **HOW:** Rely purely on brand colours and provider logos. Left ring: Anthropic orange (`#F97316`). Right ring: OpenAI green (`#10B981`). Increase ring thickness to 6dp. Center the value text at 18sp Bold.

**6. Home Screen Hierarchy: The "Critical" pill**
*   **WHAT:** Demote the top-left red "Critical" pill to a simple dot and text.
*   **WHY:** The eye lands immediately on the bright red, high-contrast pill in the top left, bypassing the actual quota data. It dominates the visual hierarchy despite being metadata.
*   **HOW:** Remove the red background container. Use a single 8dp red dot (`#EF4444`) followed by the word "Critical" in 14sp `#A1A1AA` text, baseline-aligned with the "updated 47 s ago" text.

**7. Web Consistency: Provider branding**
*   **WHAT:** Replace the generic coloured dots next to "Claude" and "Codex" with the actual provider logos used on the Web version.
*   **WHY:** The coloured dots look cheap. The Web version uses the Anthropic asterisk and OpenAI flower, which instantly grounds the data and aids rapid visual parsing.
*   **HOW:** Import the 24x24dp SVG logos from the web build. Tint the Anthropic logo `#F97316` and the OpenAI logo `#10B981`. Place them exactly where the current coloured dots are.

**8. Feature Graphic: Missed value proposition**
*   **WHAT:** Replace the single-ring graphic with an isometric 3D render of the 4x2 widget on a phone home screen.
*   **WHY:** The current graphic (one ring, "75%") is redundant with the app icon and fails to explain the app's core value: seeing *multiple* AI limits at a glance without opening an app.
*   **HOW:** Background `#09090B`. Center an angled mockup of an Android device showing the new 2-ring 4x2 widget. Headline text (48sp Bold, White): "All your AI limits." Subhead (24sp, `#A1A1AA`): "Right on your home screen."

## The app icon — 3 alternative marks

The current icon (a thin green ring on black) disappears on dark wallpapers, looks like a utility app from 2012, and lacks a distinct silhouette at 48dp next to bright Google apps.

1.  **The Battery Q:** 
    A solid white (`#FFFFFF`) squircle background. In the centre, a bold, stylized letter 'Q' drawn in dark slate (`#18181B`). The circular body of the 'Q' is broken at the top right. A bright green (`#10B981`) thick stroke (8dp) fills exactly the left half of the 'Q', mimicking a depletion gauge. It reads instantly as "Quota" and "Measurement" at 48dp.
2.  **The Dual Gauge:** 
    A bright Anthropic-peach (`#FFEDD5`) squircle background. Two interlocking, thick (10dp) semi-circles forming an abstract 'S' or infinity loop. The top semi-circle is OpenAI green (`#10B981`), the bottom is Anthropic orange (`#EA580C`). It provides a high-contrast, highly distinctive silhouette that pops against standard white app icons.
3.  **The Depletion Dash:** 
    A solid black (`#000000`) squircle background. Three thick, horizontal pill shapes stacked vertically, left-aligned. The top pill is 100% width (green `#10B981`), the middle is 60% width (amber `#F59E0B`), the bottom is 20% width (red `#EF4444`). Corner radius of pills: 16dp. It instantly conveys "decreasing limits" at 48dp without relying on thin circular strokes that turn to mush at small sizes.

## 3 things that already work — do not touch

1.  **The Dark/Light Theme Contrast:** The background colours (`#18181B` for dark, `#FFFFFF` for light) provide an excellent, stark baseline contrast for the vibrant ring colours. Do not soften them to greys.
2.  **The Error State Clarity:** The error state (`home-error.png`) doesn't try to be cute with illustrations. It gives the exact DNS resolution failure string. For a power-user tracking API limits, this raw technical clarity is exactly what is needed to debug.
3.  **The Ring Stroke Width (on Home Screen):** The physical thickness of the rings on the main Android app home screen is bold and unapologetic. It makes the primary data point instantly readable from across a desk. Keep this exact stroke-to-diameter ratio.
