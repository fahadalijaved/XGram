"""Images for the GitHub repository: .github/assets/

  python tools/xgram/github_assets.py            (all of them)
  python tools/xgram/github_assets.py banner     (one: banner, social, features, styles)

banner.png          1600x520  top of the README
social-preview.png  1280x640  the card shown when the repo link is shared (Settings -> General -> Social preview)
features.png        1600x900  feature overview with screenshots, in the README
make-it-yours.png   1600x900  app icon styles with their wallpapers, in the README

Same look as the X images (x_assets.py), whose drawing code this reuses.
"""
import importlib.util
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, '.github', 'assets')

_spec = importlib.util.spec_from_file_location('xg_x_assets', os.path.join(HERE, 'x_assets.py'))
xa = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(xa)
K = xa.K

WALL = ['neon', 'Sunset', 'pixel', 'Midnight', 'Emerald',
        'Aurora', 'retro', 'Rose', 'turbo', 'Night',
        'stardust', 'Gold', 'XGram', 'Glass', 'origami',
        'Mono', 'Midnight', 'neon', 'Aurora', 'retro',
        'Rose', 'turbo', 'Emerald', 'pixel', 'Sunset']


def out(name):
    return os.path.join(OUT, name)


def icon_wall(img, W, H, cx, cy, fade_from, fade_len, px=104, gap=28, n=5):
    """All the app icons on a tilted grid centred at (cx, cy), fading out to the left from fade_from."""
    side = n * px + (n - 1) * gap + 80
    layer = Image.new('RGBA', (side * K, side * K), (0, 0, 0, 0))
    styles6 = set(k for k, _, _ in xa.icon_styles.STYLES)
    for i, key in enumerate(WALL[:n * n]):
        c, r = i % n, i // n
        t = xa.style_icon(key, px * K) if key in styles6 else xa.app_icon(xa.VARIANTS[key], px * K)
        layer = xa.shadow_paste(layer, t, (40 + c * (px + gap)) * K, (40 + r * (px + gap)) * K, blur=10 * K, dy=6 * K, alpha=80)
    layer = layer.rotate(-14, resample=Image.BICUBIC, expand=True)
    full = Image.new('RGBA', img.size, (0, 0, 0, 0))
    full.paste(layer, (int(cx * K - layer.width / 2), int(cy * K - layer.height / 2)), layer)
    xs = np.arange(W * K) / K
    ramp = (np.clip((xs - fade_from) / float(fade_len), 0, 1) ** 1.4)[None, :].repeat(H * K, 0)
    al = np.array(full.split()[3], float) * ramp
    full.putalpha(Image.fromarray(al.astype(np.uint8)))
    return Image.alpha_composite(img, full)


def pills(img, labels, x, top, h, size, gap=12):
    d = ImageDraw.Draw(img)
    f = xa.font('Medium', size)
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    pd = ImageDraw.Draw(layer)
    spots = []
    for lab in labels:
        w = xa.text_w(d, lab, f) + h * .78 * K
        pd.rounded_rectangle([x, top * K, x + w, (top + h) * K], radius=h / 2 * K, fill=(255, 255, 255, 50))
        spots.append((x + w / 2, lab))
        x += w + gap * K
    img = Image.alpha_composite(img, layer)
    d = ImageDraw.Draw(img)
    for cx, lab in spots:
        d.text((cx, (top + h / 2) * K), lab, font=f, fill=(255, 255, 255, 255), anchor='mm')
    return img, x / K


def banner():
    W, H = 1600, 520
    img = xa.backdrop(W, H, angle=18, glow=(-200, -320, 900, 540, 46))
    img = icon_wall(img, W, H, 1470, 260, 1150, 210)
    icon = xa.app_icon(xa.VARIANTS['XGram'], 164 * K)
    img = xa.shadow_paste(img, icon, 96 * K, 128 * K, blur=14 * K, dy=8 * K, alpha=90)
    d = ImageDraw.Draw(img)
    tx = 300 * K
    d.text((tx, 238 * K), 'XGram', font=xa.font('Bold', 132), fill=(255, 255, 255, 255), anchor='ls')
    d.text((tx + 6 * K, 296 * K), 'Telegram X, supercharged.', font=xa.font('Medium', 40), fill=(255, 255, 255, 240), anchor='ls')
    img, _ = pills(img, ('Ghost mode', 'Message history', 'Forum topics', 'Communities'), tx + 4 * K, 330, 46, 25)
    xa.finish(img, out('banner.png'))


def social():
    W, H = 1280, 640
    img = xa.backdrop(W, H, angle=20, glow=(-260, -300, 760, 560, 50))
    img = icon_wall(img, W, H, 1150, 320, 830, 200, px=112, gap=30)
    icon = xa.app_icon(xa.VARIANTS['XGram'], 128 * K)
    img = xa.shadow_paste(img, icon, 80 * K, 96 * K, blur=12 * K, dy=6 * K, alpha=90)
    d = ImageDraw.Draw(img)
    d.text((76 * K, 380 * K), 'XGram', font=xa.font('Bold', 136), fill=(255, 255, 255, 255), anchor='ls')
    d.text((82 * K, 436 * K), 'Telegram X, supercharged.', font=xa.font('Medium', 38), fill=(255, 255, 255, 240), anchor='ls')
    img, _ = pills(img, ('Ghost mode', 'Message history', 'Forum topics'), 82 * K, 468, 46, 25)
    d = ImageDraw.Draw(img)
    d.text((82 * K, 578 * K), 'Free  ·  No ads  ·  Open source (GPLv3)  ·  t.me/XGramTD',
           font=xa.font('Medium', 24), fill=(255, 255, 255, 215), anchor='ls')
    xa.finish(img, out('social-preview.png'))


def features():
    xa.release(out('features.png'), footer=(('Free', 1), ('  ·  ', 0), ('No ads', 1), ('  ·  ', 0), ('Open source', 1),
                                            ('  ·  ', 0), ('t.me/XGramTD', 1)))


def styles():
    xa.features2(out('make-it-yours.png'), note='XGram · unofficial Telegram client, based on Telegram X · open source, GPLv3')


JOBS = dict(banner=banner, social=social, features=features, styles=styles)

if __name__ == '__main__':
    for key in (sys.argv[1:] or JOBS):
        JOBS[key]()
