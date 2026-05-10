#!/usr/bin/env python3
"""
Generate the Play Store launcher icon (512x512 PNG).

Same visual language as the in-app GMark and the Android launcher icon:
white donut + horizontal tongue on the app's near-black background.

Run:  python scripts/generate_play_store_icon.py
Output: scripts/play_store_icon.png  (512x512, RGBA)
"""

from pathlib import Path
from PIL import Image, ImageDraw

SIZE = 512
SUPERSAMPLE = 4               # render at 4x then downsample for crisp edges
BG = (0x0A, 0x0C, 0x0E, 0xFF)         # app bg
FG = (0xF3, 0xF5, 0xF7, 0xFF)         # text/foreground white

# Geometry as fractions of the final canvas. Multiplied by SUPERSAMPLE during
# render and the result is shrunk back down with a high-quality filter.
OUTER_R_FRAC = 0.34
INNER_R_FRAC = 0.20
TONGUE_HALF_H_FRAC = 0.058
TONGUE_OVERSHOOT_FRAC = 0.012


def draw_icon(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), BG)
    draw = ImageDraw.Draw(img)

    cx = cy = size / 2
    outer_r = size * OUTER_R_FRAC
    inner_r = size * INNER_R_FRAC
    tongue_half_h = size * TONGUE_HALF_H_FRAC
    tongue_overshoot = size * TONGUE_OVERSHOOT_FRAC

    # 1. Outer disk (filled white).
    draw.ellipse(
        (cx - outer_r, cy - outer_r, cx + outer_r, cy + outer_r),
        fill=FG,
    )

    # 2. Knock out the inner hole back to the background color.
    draw.ellipse(
        (cx - inner_r, cy - inner_r, cx + inner_r, cy + inner_r),
        fill=BG,
    )

    # 3. Tongue: filled white rectangle from center extending right past the
    #    outer ring. Drawn AFTER the hole punch so it bridges the inner hole
    #    and the area outside the donut into one connected shape.
    draw.rectangle(
        (
            cx,
            cy - tongue_half_h,
            cx + outer_r + tongue_overshoot,
            cy + tongue_half_h,
        ),
        fill=FG,
    )

    return img


def main() -> None:
    out_path = Path(__file__).parent / "play_store_icon.png"
    # Render at supersampled resolution, then downsample with LANCZOS for
    # crisp anti-aliased edges (Pillow's ellipse rasterizer is too soft at 1x).
    big = draw_icon(SIZE * SUPERSAMPLE)
    img = big.resize((SIZE, SIZE), Image.LANCZOS)
    img.save(out_path, format="PNG", optimize=True)
    print(f"Wrote {out_path}  ({SIZE}x{SIZE}, rendered at {SIZE * SUPERSAMPLE}x)")


if __name__ == "__main__":
    main()
