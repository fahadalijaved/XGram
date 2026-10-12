"""Banner for the repository README: .github/assets/banner.png

  python tools/xgram/readme_banner.py
"""
import importlib.util
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, '.github', 'assets', 'banner.png')
FONT = os.path.join(ROOT, 'app', 'jni', 'tgvoip', 'third_party', 'webrtc', 'examples', 'objc', 'AppRTCMobile', 'ios', 'resources', 'Roboto-Regular.ttf')

spec = importlib.util.spec_from_file_location('brand', os.path.join(HERE, 'brand_assets.py'))
brand = importlib.util.module_from_spec(spec)
spec.loader.exec_module(brand)
sys.path.insert(0, os.path.join(HERE, 'icon_packs'))
import styles  # noqa: E402
from dsl import core  # noqa: E402
from geometry import flatten  # noqa: E402

W, H = 1280, 420
K = 2  # supersampling


def icon_layer(names, size, origin_x, origin_y, cols, gap, alpha):
    """XGram Duotone icons drawn in white, faint, as a texture."""
    layer = Image.new('RGBA', (W * K, H * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    s = size * K / 24.0
    for i, name in enumerate(names):
        ox = (origin_x + (i % cols) * (size + gap)) * K
        oy = (origin_y + (i // cols) * (size + gap)) * K
        strokes, fills, closed = styles._resolve(core.ICONS[name])
        for pts, _ in flatten(closed, step=0.3):
            if len(pts) > 2:
                d.polygon([(ox + x * s, oy + y * s) for x, y in pts], fill=(255, 255, 255, int(alpha * 0.35)))
        for pts, closed_path in flatten(strokes, step=0.3):
            p = [(ox + x * s, oy + y * s) for x, y in pts]
            d.line(p, fill=(255, 255, 255, alpha), width=max(1, int(1.9 * s)), joint='curve')
            r = 0.95 * s
            for x, y in (p[0], p[-1]):
                d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, alpha))
        for pts, _ in flatten(fills, step=0.3):
            if len(pts) > 2:
                d.polygon([(ox + x * s, oy + y * s) for x, y in pts], fill=(255, 255, 255, alpha))
    return layer


def main():
    img = brand.gradient((W * K, H * K), '#2AABEE', '#6C5CE7', angle=20).convert('RGBA')
    glow = Image.new('RGBA', (W * K, H * K), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([-200 * K, -260 * K, 620 * K, 560 * K], fill=(255, 255, 255, 40))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(90 * K)))

    names = ['chat_bubble', 'ghost', 'forum', 'history', 'palette', 'bookmark',
             'lock', 'stars', 'phone', 'camera', 'notifications', 'translate']
    img = Image.alpha_composite(img, icon_layer(names, 40, 880, 36, 6, 28, 44))  # two rows, above the tagline

    # app icon tile with a soft shadow
    tile = brand.avatar_community(300)  # the X on white, with stacked cards: XGram's community look
    mask = Image.new('L', tile.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, tile.width - 1, tile.height - 1], fill=255)
    big = tile.resize((240 * K, 240 * K), Image.LANCZOS)
    m = mask.resize(big.size, Image.LANCZOS)
    shadow = Image.new('RGBA', (W * K, H * K), (0, 0, 0, 0))
    sx, sy = 76 * K, 90 * K
    sh = Image.new('RGBA', big.size, (10, 16, 60, 90))
    shadow.paste(sh, (sx, sy + 10 * K), m)
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(18 * K)))
    img.paste(big, (sx, sy), m)

    d = ImageDraw.Draw(img)
    title = ImageFont.truetype(FONT, 112 * K)
    tag = ImageFont.truetype(FONT, 33 * K)
    chips = ImageFont.truetype(FONT, 22 * K)
    tx = 372 * K
    d.text((tx, 92 * K), 'XGram', font=title, fill=(255, 255, 255, 255), stroke_width=2 * K, stroke_fill=(255, 255, 255, 255))
    d.text((tx + 4 * K, 232 * K), 'A Telegram client for people who want more.', font=tag, fill=(255, 255, 255, 235))
    x = tx + 4 * K
    pills = Image.new('RGBA', (W * K, H * K), (0, 0, 0, 0))
    pd = ImageDraw.Draw(pills)
    labels = ('Forum topics', 'Ghost mode', 'Message history', 'Icon packs', 'Foldables')
    spots = []
    for label in labels:
        w = d.textlength(label, font=chips) + 30 * K
        pd.rounded_rectangle([x, 296 * K, x + w, 336 * K], radius=20 * K, fill=(255, 255, 255, 52))
        spots.append((x, label))
        x += w + 12 * K
    img = Image.alpha_composite(img, pills)
    d = ImageDraw.Draw(img)
    for x, label in spots:
        d.text((x + 15 * K, 303 * K), label, font=chips, fill=(255, 255, 255, 255))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.resize((W, H), Image.LANCZOS).convert('RGB').save(OUT, optimize=True)
    print(OUT)


if __name__ == '__main__':
    main()
