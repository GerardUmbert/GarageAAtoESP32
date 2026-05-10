#!/usr/bin/env python3
"""
Generate the Play Store feature graphic (1024x500 PNG).

Layout: white "G" donut+tongue mark on the left, app name + tagline on the
right. Same visual language as the launcher icon.

Run:  python scripts/generate_play_store_feature_graphic.py
Output: scripts/play_store_feature_graphic.png  (1024x500, RGB)
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
SUPERSAMPLE = 3               # render at 3x then downsample for crisp edges

BG = (0x0A, 0x0C, 0x0E)               # app bg (RGB; feature graphic is opaque)
FG = (0xF3, 0xF5, 0xF7)               # white
DIM = (0x8A, 0x93, 0x9C)              # subtle grey for tagline

# Mark geometry — sized so it fills the left third of the 500px-tall banner
# with comfortable breathing room. Center placed at x ≈ 250, y = 250.
MARK_CX_FRAC = 250 / 1024
MARK_CY_FRAC = 0.5
MARK_OUTER_R_FRAC = 170 / 500           # vs height (500)
MARK_INNER_R_FRAC = 100 / 500
MARK_TONGUE_HALF_H_FRAC = 28 / 500
MARK_TONGUE_OVERSHOOT_FRAC = 7 / 500

# Text — anchored a bit right of the mark
TITLE_TEXT = "GarageAA"
TAGLINE_TEXT = "Open your garage from your dashboard"
TEXT_LEFT_FRAC = 480 / 1024
TITLE_TOP_FRAC = 175 / 500
TAGLINE_TOP_FRAC = 305 / 500
TITLE_SIZE_FRAC = 84 / 500
TAGLINE_SIZE_FRAC = 26 / 500


def load_font(size_px: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    # Prefer common Windows fonts that render cleanly at large sizes.
    # Falls back to PIL default if none are present (won't be pretty but won't crash).
    candidates = (
        ["seguisb.ttf", "segoeuib.ttf", "arialbd.ttf", "calibrib.ttf"]
        if bold
        else ["segoeui.ttf", "arial.ttf", "calibri.ttf"]
    )
    for name in candidates:
        try:
            return ImageFont.truetype(name, size_px)
        except OSError:
            continue
    return ImageFont.load_default()


def draw_mark(draw: ImageDraw.ImageDraw, w: int, h: int) -> None:
    cx = w * MARK_CX_FRAC
    cy = h * MARK_CY_FRAC
    outer_r = h * MARK_OUTER_R_FRAC
    inner_r = h * MARK_INNER_R_FRAC
    tongue_half_h = h * MARK_TONGUE_HALF_H_FRAC
    tongue_overshoot = h * MARK_TONGUE_OVERSHOOT_FRAC

    # Outer disk
    draw.ellipse(
        (cx - outer_r, cy - outer_r, cx + outer_r, cy + outer_r),
        fill=FG,
    )
    # Knock out the inner hole
    draw.ellipse(
        (cx - inner_r, cy - inner_r, cx + inner_r, cy + inner_r),
        fill=BG,
    )
    # Tongue extending right past the outer ring
    draw.rectangle(
        (
            cx,
            cy - tongue_half_h,
            cx + outer_r + tongue_overshoot,
            cy + tongue_half_h,
        ),
        fill=FG,
    )


def draw_text(draw: ImageDraw.ImageDraw, w: int, h: int) -> None:
    text_left = w * TEXT_LEFT_FRAC
    title_top = h * TITLE_TOP_FRAC
    tagline_top = h * TAGLINE_TOP_FRAC
    title_size = int(h * TITLE_SIZE_FRAC)
    tagline_size = int(h * TAGLINE_SIZE_FRAC)

    title_font = load_font(title_size, bold=True)
    tagline_font = load_font(tagline_size, bold=False)

    draw.text((text_left, title_top), TITLE_TEXT, font=title_font, fill=FG)
    draw.text((text_left, tagline_top), TAGLINE_TEXT, font=tagline_font, fill=DIM)


def render(w: int, h: int) -> Image.Image:
    img = Image.new("RGB", (w, h), BG)
    draw = ImageDraw.Draw(img)
    draw_mark(draw, w, h)
    draw_text(draw, w, h)
    return img


def main() -> None:
    out_path = Path(__file__).parent / "play_store_feature_graphic.png"
    big = render(W * SUPERSAMPLE, H * SUPERSAMPLE)
    img = big.resize((W, H), Image.LANCZOS)
    img.save(out_path, format="PNG", optimize=True)
    print(f"Wrote {out_path}  ({W}x{H}, rendered at {W * SUPERSAMPLE}x{H * SUPERSAMPLE})")


if __name__ == "__main__":
    main()
