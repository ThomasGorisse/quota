#!/usr/bin/env python3
"""Generate the Quota store mark: Play icon, feature graphic and launcher foreground.

One source of truth for the geometry, so the launcher icon and the store icon are the
same drawing. Palette is `docs/DESIGN.md`; the mark is the app's own gauge — a ring,
three quarters full, value colour `ok`.

    python3 android/scripts/generate-play-assets.py

Everything is drawn at 4x and downsampled: Pillow has no antialiased arc.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PLAY = ROOT / "play"
RES = ROOT / "app/src/main/res"

BG = "#0B0F14"          # background dark
OK = "#22C55E"          # severity ok — the ring value
TEXT = "#F3F4F6"        # text primary dark
MUTED = "#9AA4B2"       # text secondary dark

SS = 4                  # supersampling factor
SWEEP = 0.75            # three quarters full, like a healthy quota
START_DEG = -90.0       # 12 o'clock


def _rgb(h: str) -> tuple[int, int, int]:
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))  # type: ignore[return-value]


def mix(bg: str, fg: str, t: float) -> tuple[int, int, int]:
    """`fg` at `t` opacity over opaque `bg`, composited by hand.

    Pillow does NOT blend the `outline=` of `ellipse`/`arc` even on an "RGBA" draw: an
    RGBA outline lands at full opacity. The first draft passed the 15 % track as an alpha
    tuple and got a track pixel-identical to the value arc, so the mark read as a plain
    "O" instead of a gauge. Compositing here keeps it deterministic.
    """
    b, f = _rgb(bg), _rgb(fg)
    return tuple(round(b[i] + (f[i] - b[i]) * t) for i in range(3))  # type: ignore[return-value]


def _font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    for path, index in (("/System/Library/Fonts/SFNS.ttf", None),
                        ("/System/Library/Fonts/HelveticaNeue.ttc", 1),
                        ("/System/Library/Fonts/Supplemental/Arial Bold.ttf", None)):
        try:
            f = ImageFont.truetype(path, size) if index is None \
                else ImageFont.truetype(path, size, index=index)
        except OSError:
            continue
        if bold:
            try:  # SFNS is variable: ask for the real Bold cut, not a faux one.
                f.set_variation_by_name("Bold")
            except (OSError, AttributeError, ValueError):
                pass
        return f
    return ImageFont.load_default()


def ring(draw: ImageDraw.ImageDraw, cx: float, cy: float, rc: float,
         stroke: float, colour: str, sweep: float = SWEEP,
         track: tuple[int, int, int] | None = None) -> None:
    """A ring with a track and a round-capped value arc (the DESIGN.md gauge).

    `rc` is the radius of the stroke *centreline*. Pillow draws `arc`/`ellipse` inward
    from the bounding box, so the box has to be inflated by half the stroke — and the
    caps have to sit on `rc`, not on the box. Getting that wrong puts two blobs outside
    the ring, which is exactly what the first draft of this mark did.
    """
    h = stroke / 2.0
    box = (cx - rc - h, cy - rc - h, cx + rc + h, cy + rc + h)
    draw.ellipse(box, outline=track or mix(BG, colour, 0.22), width=int(round(stroke)))
    end = START_DEG + 360.0 * sweep
    draw.arc(box, START_DEG, end, fill=colour, width=int(round(stroke)))
    # Round caps: Pillow's arc is butt-capped, so cap it by hand, on the centreline.
    for deg in (START_DEG, end):
        a = math.radians(deg)
        px, py = cx + rc * math.cos(a), cy + rc * math.sin(a)
        draw.ellipse((px - h, py - h, px + h, py + h), fill=colour)


def icon(size: int, bg: str | None = BG, margin_ratio: float = 0.24) -> Image.Image:
    """The square mark. `bg=None` gives the transparent adaptive-icon foreground."""
    s = size * SS
    img = Image.new("RGBA", (s, s), bg if bg else (0, 0, 0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    stroke = s * 0.092
    rc = s * (0.5 - margin_ratio) - stroke / 2
    ring(d, s / 2, s / 2, rc, stroke=stroke, colour=OK)
    return img.resize((size, size), Image.LANCZOS)


def feature_graphic(w: int = 1024, h: int = 500) -> Image.Image:
    W, H = w * SS, h * SS
    img = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(img, "RGBA")

    # Ring on the left third, wordmark and tagline to its right, on an 8-grid rhythm.
    stroke = H * 0.052
    rc = H * 0.26 - stroke / 2
    cx, cy = W * 0.235, H / 2
    ring(d, cx, cy, rc, stroke=stroke, colour=OK)

    pct = _font(int(H * 0.155))
    label = "75%"
    bb = d.textbbox((0, 0), label, font=pct)
    d.text((cx - (bb[2] - bb[0]) / 2 - bb[0], cy - (bb[3] - bb[1]) / 2 - bb[1]),
           label, font=pct, fill=TEXT)

    x = W * 0.42
    word = _font(int(H * 0.235))
    wb = d.textbbox((0, 0), "Quota", font=word)
    d.text((x - wb[0], cy - (wb[3] - wb[1]) - H * 0.035 - wb[1]),
           "Quota", font=word, fill=TEXT)

    tag = _font(int(H * 0.085), bold=False)
    d.text((x, cy + H * 0.045), "AI usage at a glance", font=tag, fill=MUTED)

    return img.resize((w, h), Image.LANCZOS)


def main() -> None:
    (PLAY / "listing/en-US").mkdir(parents=True, exist_ok=True)
    icon(512).convert("RGB").save(PLAY / "listing/en-US/icon.png")
    feature_graphic().convert("RGB").save(PLAY / "listing/en-US/feature-graphic.png")

    # Launcher: the same mark, transparent, sized to the adaptive-icon safe zone. The
    # foreground layer is 108x108dp, the mask crops to 72 and only the central 66 is
    # guaranteed visible, so the ring's OUTER diameter must be <= 66/108 = 0.611 of the
    # layer. margin 0.235 puts it at 0.53 — filling the safe zone without touching it.
    for density, px in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                        ("xxhdpi", 324), ("xxxhdpi", 432)):
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        icon(px, bg=None, margin_ratio=0.235).save(out / "ic_launcher_foreground.png")

    print("wrote", PLAY / "listing/en-US/icon.png")
    print("wrote", PLAY / "listing/en-US/feature-graphic.png")
    print("wrote mipmap-*/ic_launcher_foreground.png")


if __name__ == "__main__":
    main()
