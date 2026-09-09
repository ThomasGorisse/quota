#!/usr/bin/env python3
"""Generate the Quota store mark: Play icon, feature graphic and launcher foreground.

One source of truth, so the launcher icon and the store icon are the same drawing. The
app mark is `docs/brand/icon-candidates/01-battery-q.png` — the chunky Q whose ring is
split green/off-white like a half-drained gauge. It is a generated raster, not a vector,
so everything here is a crop-and-scale of that file rather than a redrawing of it: the
one thing this script must never do is produce a *second*, slightly different Q.

    python3 android/scripts/generate-play-assets.py

The feature graphic is the same crop again — mark, wordmark, tagline — composed at 4x and
downsampled, because Pillow's text is only as smooth as the size it is drawn at. It used
to draw its own three-quarter ring, which is precisely the second Q this file forbids:
the store page showed one mark and the launcher another.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PLAY = ROOT / "play"
RES = ROOT / "app/src/main/res"
SOURCE = ROOT.parent / "docs/brand/icon-candidates/01-battery-q.png"

BG = "#0B0F14"          # background dark
TEXT = "#F3F4F6"        # text primary dark
MUTED = "#9AA4B2"       # text secondary dark

SS = 4                  # supersampling factor


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


TAGLINE = "Claude and Codex limits on your home screen"


def feature_graphic(w: int = 1024, h: int = 500) -> Image.Image:
    """Mark at left, wordmark and one-line tagline to its right, on the dark field.

    [TAGLINE] says the same thing as `play/listing/en-US/short-description.txt`. It is
    one line by contract, so the size is *fitted* to the column rather than guessed:
    the first draft guessed, and the sentence ran off the right edge of the 1024 px
    canvas — which no amount of looking at the code, as opposed to the picture, catches.
    """
    W, H = w * SS, h * SS
    img = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(img, "RGBA")

    # The one Q, from the one source: no arc is drawn here.
    box = int(round(H * 0.58))
    glyph = mark(box, safe_ratio=0.98)
    cx, cy = W * 0.185, H / 2
    img.alpha_composite(glyph, (int(cx - box / 2), int(cy - box / 2)))

    x = int(W * 0.36)
    column = int(W * 0.94) - x          # 6 % of the canvas kept clear on the right

    word = _font(int(H * 0.235))
    wb = d.textbbox((0, 0), "Quota", font=word)
    d.text((x - wb[0], cy - (wb[3] - wb[1]) - H * 0.04 - wb[1]),
           "Quota", font=word, fill=TEXT)

    size = int(H * 0.088)
    while size > 8:
        tag = _font(size, bold=False)
        if d.textlength(TAGLINE, font=tag) <= column:
            break
        size -= 2
    d.text((x, cy + H * 0.05), TAGLINE, font=tag, fill=MUTED)

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
