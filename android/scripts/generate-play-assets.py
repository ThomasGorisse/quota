#!/usr/bin/env python3
"""Generate the Quota store mark: Play icon, feature graphic and launcher foreground.

One source of truth, so the launcher icon and the store icon are the same drawing. The
app mark is `docs/brand/icon-candidates/01-battery-q.png` — the chunky Q whose ring is
split green/off-white like a half-drained gauge. It is a generated raster, not a vector,
so everything here is a crop-and-scale of that file rather than a redrawing of it: the
one thing this script must never do is produce a *second*, slightly different Q.

    python3 android/scripts/generate-play-assets.py

The feature graphic is still drawn here, at 4x then downsampled: Pillow has no
antialiased arc.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PLAY = ROOT / "play"
RES = ROOT / "app/src/main/res"
SOURCE = ROOT.parent / "docs/brand/icon-candidates/01-battery-q.png"

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


# Luminance window that separates the mark from its near-black field. The generated PNG
# has a noisy background (dominant #09121B, +/- 2 per channel), so a single threshold
# would fray the edges: below LUM_FLOOR is background, above LUM_CEIL is mark, in between
# is the antialiased rim and becomes partial alpha. The green #22C55E sits at luma 136,
# well above LUM_CEIL, so the value half of the ring stays fully opaque.
LUM_FLOOR, LUM_CEIL = 32, 110

# Adaptive icons are a 108dp layer the mask crops to 72dp, of which only the central 66dp
# is guaranteed visible: 66/108 = 0.611. The source already keeps its Q inside its own
# central 66 %, which is too close to that limit to survive the mask on every OEM shape,
# so the mark is re-fitted to 0.58 of the layer here.
SAFE_RATIO = 0.58


def _source() -> Image.Image:
    return Image.open(SOURCE).convert("RGB")


def mark(size: int, safe_ratio: float = SAFE_RATIO,
         monochrome: bool = False) -> Image.Image:
    """The Q, cut out of its background, scaled into `safe_ratio` and centred.

    The cut-out keeps the source RGB and only replaces the alpha, so the antialiased rim
    still carries the source's own near-black. That is deliberate: every consumer here
    composites onto [BG], which is that same near-black, so the rim lands seam-free
    instead of the grey halo an unpremultiplied resize would leave.
    """
    src = _source()
    lum = src.convert("L")
    span = LUM_CEIL - LUM_FLOOR
    alpha = lum.point(
        lambda v: 0 if v < LUM_FLOOR else 255 if v > LUM_CEIL
        else int((v - LUM_FLOOR) * 255 / span)
    )
    body = Image.new("RGB", src.size, "#FFFFFF") if monochrome else src
    cut = body.convert("RGBA")
    cut.putalpha(alpha)

    # Trim to the mark itself: the source's own margin is not ours to inherit.
    box = alpha.point(lambda v: 255 if v > 127 else 0).getbbox()
    cut = cut.crop(box)

    target = int(round(size * safe_ratio))
    scale = target / max(cut.width, cut.height)
    cut = cut.resize(
        (max(1, round(cut.width * scale)), max(1, round(cut.height * scale))),
        Image.LANCZOS,
    )
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(cut, ((size - cut.width) // 2, (size - cut.height) // 2), cut)
    return out


def icon(size: int, bg: str | None = BG, safe_ratio: float = SAFE_RATIO) -> Image.Image:
    """The square app icon. `bg=None` gives the transparent adaptive-icon foreground."""
    layer = mark(size, safe_ratio)
    if bg is None:
        return layer
    out = Image.new("RGBA", (size, size), bg)
    out.alpha_composite(layer)
    return out


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


DENSITIES = (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
             ("xxhdpi", 324), ("xxxhdpi", 432))


def main() -> None:
    (PLAY / "listing/en-US").mkdir(parents=True, exist_ok=True)
    # The store icon is the launcher icon flattened onto its own background layer, so the
    # two cannot drift: same crop, same scale, same field.
    icon(512).convert("RGB").save(PLAY / "listing/en-US/icon.png")
    feature_graphic().convert("RGB").save(PLAY / "listing/en-US/feature-graphic.png")

    for density, px in DENSITIES:
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        icon(px, bg=None).save(out / "ic_launcher_foreground.png")
        # Themed icons: the same silhouette, white, for the system to tint. Alpha is what
        # the platform reads, so the white is only there to keep the rim from darkening.
        mark(px, monochrome=True).save(out / "ic_launcher_monochrome.png")

    print("wrote", PLAY / "listing/en-US/icon.png")
    print("wrote", PLAY / "listing/en-US/feature-graphic.png")
    print("wrote mipmap-*/ic_launcher_foreground.png")
    print("wrote mipmap-*/ic_launcher_monochrome.png")


if __name__ == "__main__":
    main()
