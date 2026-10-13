"""Images for XGram's X (Twitter) account: docs/xgram/x/

  python tools/xgram/x_assets.py            (all of them)
  python tools/xgram/x_assets.py header     (one: header, avatar, release, features)

x_header.png   1500x500  profile header, the lower left kept free for the round avatar
x_avatar.png   400x400   the app icon, full bleed (X crops it to a circle)
x_release.png  1600x900  release announcement: features and real screenshots
x_features_2.png 1600x900 second image: app icon styles with their wallpapers, icon sets

Same look as the README banner (github_assets.py): the blue to indigo gradient, Roboto, the woven X.
"""
import importlib.util
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, 'docs', 'xgram', 'x')
FONTS = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'fonts')
SCREENS = os.path.join(ROOT, '.github', 'assets', 'screens')
WALLS = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'xgram_wallpapers')
K = 2  # supersampling of every canvas

BLUE, INDIGO = '#2AABEE', '#6C5CE7'
INK = (52, 58, 150, 255)  # glyph colour on the white chips, the gradient's dark middle


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


brand = load('xg_brand', os.path.join(HERE, 'brand_assets.py'))
icon_styles = load('xg_icon_styles', os.path.join(HERE, 'styles.py'))  # Neon, Turbo... app icons
# render3.py draws its preview sheet at import; take only its functions and the colour variants
_src = open(os.path.join(HERE, 'render3.py'), encoding='utf-8').read()
r3 = {}
exec(compile(_src.split('cols = 5')[0], 'render3.py', 'exec'), r3)
sys.path.insert(0, os.path.join(HERE, 'icon_packs'))
import styles as pack_styles  # noqa: E402  the icon pack line shapes
from dsl import core  # noqa: E402
from geometry import flatten  # noqa: E402


# ---------------------------------------------------------------- basics

def font(weight, px):
    return ImageFont.truetype(os.path.join(FONTS, 'Roboto-%s.ttf' % weight), int(px * K))


def rgba(h, a=255):
    return brand.rgba(h, a)


def backdrop(w, h, angle=20, glow=None):
    img = brand.gradient((w * K, h * K), BLUE, INDIGO, angle=angle).convert('RGBA')
    if glow:
        x0, y0, x1, y1, a = glow
        g = Image.new('RGBA', img.size, (0, 0, 0, 0))
        ImageDraw.Draw(g).ellipse([x0 * K, y0 * K, x1 * K, y1 * K], fill=(255, 255, 255, a))
        img = Image.alpha_composite(img, g.filter(ImageFilter.GaussianBlur(110 * K)))
    return img


def finish(img, name, quality=None):
    w, h = img.width // K, img.height // K
    out = img.resize((w, h), Image.LANCZOS).convert('RGB')
    path = name if os.path.isabs(name) else os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path, optimize=True)
    print(path, out.size, '%.0f KB' % (os.path.getsize(path) / 1024))


def shadow_paste(img, tile, x, y, mask=None, blur=16, dy=10, alpha=90, color=(10, 16, 60)):
    """Pastes tile (RGBA) at canvas coords (already K-scaled) with a soft drop shadow."""
    m = mask if mask is not None else tile.split()[3]
    sh = Image.new('RGBA', img.size, (0, 0, 0, 0))
    s = Image.new('RGBA', tile.size, color + (alpha,))
    sh.paste(s, (int(x), int(y + dy)), m)
    img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(blur)))
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    layer.paste(tile, (int(x), int(y)), m)
    return Image.alpha_composite(img, layer)


def text_w(d, s, f):
    return d.textlength(s, font=f)


# ---------------------------------------------------------------- app icons

def app_icon(v, px, shape='squircle', span=72):
    """A colour variant from render3.py (XGram, Aurora, Midnight...) at any size; shape squircle, circle or square.
    span: how much of the 108-unit adaptive icon is shown (72 = the launcher crop, more = a smaller X)."""
    S = r3['S']
    size = (108 * S, 108 * S)
    img = r3['gradient'](size, *v['bg'], radial=v.get('radial', False))
    P, rg = r3['P'], r3['rgba']
    if v.get('glass'):
        ov = Image.new('RGBA', size, (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
        d.ellipse([P(10, 8), P(58, 56)], fill=(255, 255, 255, 40))
        d.ellipse([P(52, 50), P(110, 108)], fill=(255, 255, 255, 26))
        img = Image.alpha_composite(img, ov)
    if v.get('ring'):
        ov = Image.new('RGBA', size, (0, 0, 0, 0))
        ImageDraw.Draw(ov).ellipse([P(23, 23), P(85, 85)], outline=rg(v['ring']), width=int(2.2 * S))
        img = Image.alpha_composite(img, ov)
    a, b = r3['glyph_masks'](size)
    both = Image.fromarray(np.maximum(np.array(a), np.array(b)))
    if v.get('glow'):
        glow = Image.new('RGBA', size, rg(v['glow'], 0))
        glow.putalpha(both.filter(ImageFilter.GaussianBlur(3.5 * S)).point(lambda p: int(p * .9)))
        img = Image.alpha_composite(img, glow)
    if v.get('shadow'):
        sh = Image.new('RGBA', size, (0, 0, 0, 0))
        sh.putalpha(both.filter(ImageFilter.GaussianBlur(2.5 * S)).point(lambda p: int(p * .35)))
        img.alpha_composite(sh, (0, int(2.0 * S)))
    paint = lambda c: r3['gradient'](size, *c) if isinstance(c, list) else Image.new('RGBA', size, rg(c))
    lb = Image.new('RGBA', size, (0, 0, 0, 0)); lb.paste(paint(v['b']), (0, 0), b); img = Image.alpha_composite(img, lb)
    sh = Image.new('RGBA', size, (0, 0, 0, 0))
    sm = a.filter(ImageFilter.GaussianBlur(2.2 * S))
    sh.putalpha(Image.fromarray((np.array(sm).astype(float) * (np.array(b) / 255.0) * 0.45).astype(np.uint8)))
    img = Image.alpha_composite(img, sh)
    la = Image.new('RGBA', size, (0, 0, 0, 0)); la.paste(paint(v['a']), (0, 0), a); img = Image.alpha_composite(img, la)
    lo, hi = int((54 - span / 2) * S), int((54 + span / 2) * S)
    img = img.crop((lo, lo, hi, hi)).resize((px, px), Image.LANCZOS)
    return clip(img, shape)


def clip(img, shape):
    px = img.width
    if shape == 'square':
        return img
    mask = Image.new('L', (px * 4, px * 4), 0)
    d = ImageDraw.Draw(mask)
    if shape == 'circle':
        d.ellipse([0, 0, px * 4 - 1, px * 4 - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, px * 4 - 1, px * 4 - 1], radius=int(px * 4 * .24), fill=255)
    out = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask.resize((px, px), Image.LANCZOS))
    return out


def style_icon(key, px):
    fn = dict((k, f) for k, _, f in icon_styles.STYLES)[key]
    return icon_styles.render(fn, out=px)


VARIANTS = dict((v['name'], v) for v in r3['V'])


# ---------------------------------------------------------------- glyphs (24-unit line icons, XGram Duotone)

def glyph(name, px, color=INK):
    """A Duotone icon (or one of the custom ones below) as an RGBA image px wide."""
    ss = 4
    big = px * ss
    s = big / 24.0
    m = Image.new('L', (big, big), 0)
    soft = Image.new('L', (big, big), 0)
    d, ds = ImageDraw.Draw(m), ImageDraw.Draw(soft)
    w = max(1, int(1.9 * s))
    if name in CUSTOM:
        CUSTOM[name](d, ds, s, w)
    else:
        strokes, fills, closed = pack_styles._resolve(core.ICONS[name])
        for pts, _ in flatten(closed, step=0.3):
            if len(pts) > 2:
                ds.polygon([(x * s, y * s) for x, y in pts], fill=255)
        for pts, _ in flatten(strokes, step=0.3):
            p = [(x * s, y * s) for x, y in pts]
            d.line(p, fill=255, width=w, joint='curve')
            r = w / 2
            for x, y in (p[0], p[-1]):
                d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        for pts, _ in flatten(fills, step=0.3):
            if len(pts) > 2:
                d.polygon([(x * s, y * s) for x, y in pts], fill=255)
    alpha = np.maximum(np.array(m, float), np.array(soft, float) * .32)
    out = Image.new('RGBA', (big, big), color[:3] + (0,))
    out.putalpha(Image.fromarray(alpha.astype(np.uint8)))
    return out.resize((px, px), Image.LANCZOS)


def _rr(d, box, s, w, r=2.2, fill=None):
    d.rounded_rectangle([c * s for c in box], radius=r * s, outline=None if fill else 255, fill=fill, width=w)


def g_qr(d, ds, s, w):
    for x, y in ((3, 3), (14, 3), (3, 14)):
        _rr(d, (x, y, x + 7, y + 7), s, w, 2)
        _rr(d, (x + 2.4, y + 2.4, x + 4.6, y + 4.6), s, w, .6, fill=255)
    for x, y in ((14, 14), (18.6, 14), (14, 18.6), (18.6, 18.6), (16.3, 16.3)):
        _rr(d, (x - .1, y - .1, x + 2.5, y + 2.5), s, w, .6, fill=255)


def g_grid(d, ds, s, w):
    for i, (x, y) in enumerate(((3, 3), (13.5, 3), (3, 13.5), (13.5, 13.5))):
        _rr(d, (x, y, x + 7.5, y + 7.5), s, w, 2.4)
        if i == 1:
            _rr(ds, (x, y, x + 7.5, y + 7.5), s, w, 2.4, fill=255)


def g_panes(d, ds, s, w):
    _rr(ds, (2.5, 4.5, 10, 19.5), s, w, 2.4, fill=255)
    _rr(d, (2.5, 4.5, 21.5, 19.5), s, w, 2.6)
    d.line([(10 * s, 4.5 * s), (10 * s, 19.5 * s)], fill=255, width=w)
    for y in (8.5, 12, 15.5):
        d.line([(13.2 * s, y * s), (18.5 * s, y * s)], fill=255, width=max(1, int(w * .8)))


def g_x(d, ds, s, w):
    # the woven X: back stroke soft, front stroke solid with the paper-plane tip (render3 geometry, 108 -> 24)
    k = 24 / 72.0 * s
    t = lambda x, y: ((x - 18) * k, (y - 18) * k)
    h = 5.5
    a0, a1 = t(36.5, 36.5), t(71.5, 71.5)
    ds.line([a0, a1], fill=255, width=int(11 * k))
    for c in (a0, a1):
        ds.ellipse([c[0] - h * k, c[1] - h * k, c[0] + h * k, c[1] + h * k], fill=255)
    d.line([a0, a1], fill=110, width=int(11 * k))
    for c in (a0, a1):
        d.ellipse([c[0] - h * k, c[1] - h * k, c[0] + h * k, c[1] + h * k], fill=110)
    x0, y0, tx, ty = 36.5, 71.5, 76.5, 31.5
    L = math.hypot(tx - x0, ty - y0)
    ux, uy = (tx - x0) / L, (ty - y0) / L
    vx, vy = -uy, ux
    mx, my = x0 + ux * 26, y0 + uy * 26
    d.polygon([t(x0 + vx * h, y0 + vy * h), t(mx + vx * h, my + vy * h), t(tx, ty), t(mx - vx * h, my - vy * h), t(x0 - vx * h, y0 - vy * h)], fill=255)
    c = t(x0, y0)
    d.ellipse([c[0] - h * k, c[1] - h * k, c[0] + h * k, c[1] + h * k], fill=255)


CUSTOM = dict(qr=g_qr, grid=g_grid, panes=g_panes, xmark=g_x)


def chip(px, name, radius=.3, fill=(255, 255, 255, 255), color=INK, scale=.6):
    """A white rounded tile with the glyph in the brand ink."""
    t = Image.new('RGBA', (px * 4, px * 4), (0, 0, 0, 0))
    ImageDraw.Draw(t).rounded_rectangle([0, 0, px * 4 - 1, px * 4 - 1], radius=int(px * 4 * radius), fill=fill)
    t = t.resize((px, px), Image.LANCZOS)
    g = glyph(name, int(px * scale), color)
    t.alpha_composite(g, ((px - g.width) // 2, (px - g.height) // 2))
    return t


# ---------------------------------------------------------------- phone frame

def phone(shot, screen_w, cut=None):
    """A screenshot in a slim dark phone frame with a status bar in the toolbar colour. Sizes in canvas px.
    cut: end the screen at this row and fill the rest with the list background (hides the debug version line)."""
    im = Image.open(os.path.join(SCREENS, shot)).convert('RGB')
    if cut:
        bg = im.getpixel((240, cut + 12))
        short = im.crop((0, 0, im.width, cut))
        im = Image.new('RGB', im.size, bg)
        im.paste(short, (0, 0))
    bar_c = im.getpixel((6, 6))
    sh = int(im.height * screen_w / im.width)
    status = int(screen_w * .075)
    screen = Image.new('RGB', (screen_w, sh + status), bar_c)
    screen.paste(im.resize((screen_w, sh), Image.LANCZOS), (0, status))
    d = ImageDraw.Draw(screen)
    f = ImageFont.truetype(os.path.join(FONTS, 'Roboto-Medium.ttf'), int(status * .46))
    d.text((int(screen_w * .07), status * .55), '9:41', font=f, fill=(255, 255, 255), anchor='lm')
    cx, cy, r = screen_w / 2, status * .55, status * .2
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(12, 12, 18))
    # battery and signal, simple shapes
    bx = screen_w * .9
    d.rounded_rectangle([bx - status * .42, cy - status * .17, bx, cy + status * .17], radius=status * .06, fill=(255, 255, 255))
    for i in range(3):
        hh = status * (.12 + .08 * i)
        x = bx - status * .75 + i * status * .1
        d.rectangle([x, cy + status * .17 - hh * 1.4, x + status * .06, cy + status * .17], fill=(255, 255, 255))
    bezel = max(4, int(screen_w * .032))
    W, H = screen_w + 2 * bezel, screen.height + 2 * bezel
    rad_o, rad_i = int(screen_w * .13), int(screen_w * .105)
    ss = 3
    frame = Image.new('RGBA', (W * ss, H * ss), (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle([0, 0, W * ss - 1, H * ss - 1], radius=rad_o * ss, fill=(22, 24, 38, 255))
    fd.rounded_rectangle([ss * 1.2, ss * 1.2, W * ss - 1 - ss * 1.2, H * ss - 1 - ss * 1.2], radius=rad_o * ss - ss, outline=(70, 74, 100, 255), width=int(ss * 1.4))
    frame = frame.resize((W, H), Image.LANCZOS)
    m = Image.new('L', (screen.width * ss, screen.height * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, screen.width * ss - 1, screen.height * ss - 1], radius=rad_i * ss, fill=255)
    frame.paste(screen, (bezel, bezel), m.resize(screen.size, Image.LANCZOS))
    return frame


# ---------------------------------------------------------------- header

def header():
    W, H = 1500, 500
    img = backdrop(W, H, angle=18, glow=(250, -300, 1100, 520, 46))
    # all 16 app icons on a tilted grid at the right, fading out toward the text
    order = ['neon', 'Sunset', 'pixel', 'Midnight', 'Emerald',
             'Aurora', 'retro', 'Rose', 'turbo', 'Night',
             'stardust', 'Gold', 'XGram', 'Glass', 'origami',
             'Mono', 'Midnight', 'neon', 'Aurora', 'retro',
             'Rose', 'turbo', 'Emerald', 'pixel', 'Sunset']
    px, gap, n = 104, 28, 5
    side = n * px + (n - 1) * gap + 80
    layer = Image.new('RGBA', (side * K, side * K), (0, 0, 0, 0))
    styles6 = set(k for k, _, _ in icon_styles.STYLES)
    for i, key in enumerate(order):
        c, r = i % n, i // n
        t = style_icon(key, px * K) if key in styles6 else app_icon(VARIANTS[key], px * K)
        layer = shadow_paste(layer, t, (40 + c * (px + gap)) * K, (40 + r * (px + gap)) * K, blur=10 * K, dy=6 * K, alpha=80)
    layer = layer.rotate(-14, resample=Image.BICUBIC, expand=True)
    full = Image.new('RGBA', img.size, (0, 0, 0, 0))
    cx, cy = 1385, 250
    full.paste(layer, (int(cx * K - layer.width / 2), int(cy * K - layer.height / 2)), layer)
    xs = np.arange(W * K) / K
    ramp = (np.clip((xs - 1110) / 190.0, 0, 1) ** 1.4)[None, :].repeat(H * K, 0)
    al = np.array(full.split()[3], float) * ramp
    full.putalpha(Image.fromarray(al.astype(np.uint8)))
    img = Image.alpha_composite(img, full)

    d = ImageDraw.Draw(img)
    tx = 450 * K
    title = font('Bold', 136)
    d.text((tx, 230 * K), 'XGram', font=title, fill=(255, 255, 255, 255), anchor='ls')
    d.text((tx + 6 * K, 292 * K), 'Telegram X, supercharged.', font=font('Medium', 40), fill=(255, 255, 255, 240), anchor='ls')
    labels = ('Ghost mode', 'Message history', 'Forum topics')
    f = font('Medium', 25)
    x = tx + 4 * K
    pills = Image.new('RGBA', img.size, (0, 0, 0, 0))
    pd = ImageDraw.Draw(pills)
    spots = []
    for lab in labels:
        w = text_w(d, lab, f) + 36 * K
        pd.rounded_rectangle([x, 330 * K, x + w, 376 * K], radius=23 * K, fill=(255, 255, 255, 50))
        spots.append((x + w / 2, lab))
        x += w + 12 * K
    img = Image.alpha_composite(img, pills)
    d = ImageDraw.Draw(img)
    for cx, lab in spots:
        d.text((cx, 353 * K), lab, font=f, fill=(255, 255, 255, 255), anchor='mm')
    finish(img, 'x_header.png')


def avatar():
    t = app_icon(VARIANTS['XGram'], 400 * K, shape='square', span=84)
    img = Image.new('RGBA', t.size)
    img.paste(t)
    finish(img, 'x_avatar.png')


# ---------------------------------------------------------------- release

FEATURES = [
    ('ghost', 'Ghost mode', 'No read receipts, no typing'),
    ('history', 'Message history', 'Deleted & edited, kept'),
    ('group', 'Communities', 'Just like the official app'),
    ('forum', 'Forum topics', 'List or tabs, done right'),
    ('grid', 'Icon sets', 'Solar, Phosphor, Pixel +3'),
    ('xmark', '16 app icons', 'With themes & wallpapers'),
    ('download', 'In-app updates', 'Stable or CI builds'),
    ('qr', 'QR login', 'Even on a single phone'),
    ('panes', 'Two-pane layout', 'For foldables & tablets'),
    ('bookmark', 'Bookmarks', 'Find any message again'),
    ('link', 'Clean links', 'Tracking junk stripped'),
    ('palette', 'Material You', 'Colors from your wallpaper'),
]


def release(name='x_release.png', footer=(('Free', 1), ('  ·  ', 0), ('No ads', 1), ('  ·  ', 0), ('Telegram group: ', 0), ('t.me/XGramTD', 1))):
    W, H = 1600, 900
    img = backdrop(W, H, angle=22, glow=(-260, -340, 760, 520, 50))
    M = 64
    # phones first, on the right, rising from the bottom edge
    specs = [  # shot, screen width, centre x, centre y, tilt
        ('topics.png', 250, 1150, 500, 7, None),
        ('icon-packs.png', 250, 1414, 500, -7, None),
        ('xgram-settings.png', 300, 1282, 470, 0, 745),
    ]
    for shot, sw, cx, cy, tilt, cut in specs:
        p = phone(shot, sw * K, cut)
        if tilt:
            p = p.rotate(tilt, resample=Image.BICUBIC, expand=True)
        img = shadow_paste(img, p, cx * K - p.width / 2, cy * K - p.height / 2, blur=22 * K, dy=14 * K, alpha=110)

    d = ImageDraw.Draw(img)
    # title block
    icon = app_icon(VARIANTS['XGram'], 92 * K)
    img = shadow_paste(img, icon, M * K, 56 * K, blur=10 * K, dy=5 * K, alpha=80)
    d = ImageDraw.Draw(img)
    tx = (M + 92 + 26) * K
    ft = font('Bold', 64)
    d.text((tx, 114 * K), 'XGram', font=ft, fill=(255, 255, 255, 255), anchor='ls')
    d.text((tx + 2 * K, 152 * K), 'Telegram X, supercharged', font=font('Medium', 30), fill=(255, 255, 255, 230), anchor='ls')

    # feature grid: 2 columns x 6 rows
    gx, gy, cw, rh = M, 206, 462, 94
    csz = 62
    ft, fs = font('Bold', 31), font('Regular', 26)
    for i, (g, title, sub) in enumerate(FEATURES):
        c, r = i % 2, i // 2
        x, y = gx + c * cw, gy + r * rh
        img = shadow_paste(img, chip(csz * K, g), x * K, y * K, blur=8 * K, dy=4 * K, alpha=55)
        d = ImageDraw.Draw(img)
        d.text(((x + csz + 20) * K, (y + 28) * K), title, font=ft, fill=(255, 255, 255, 255), anchor='ls')
        d.text(((x + csz + 20) * K, (y + 61) * K), sub, font=fs, fill=(255, 255, 255, 222), anchor='ls')

    # footer
    fy = 822
    line = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(line).rectangle([M * K, (fy - 46) * K, (M + 880) * K, (fy - 45) * K], fill=(255, 255, 255, 70))
    img = Image.alpha_composite(img, line)
    d = ImageDraw.Draw(img)
    f1, f1b = font('Medium', 29), font('Bold', 29)
    x = M * K
    for part, bold in footer:
        f = f1b if bold else f1
        d.text((x, fy * K), part, font=f, fill=(255, 255, 255, 255), anchor='ls')
        x += text_w(d, part, f)
    d.text((M * K, (fy + 40) * K), 'Unofficial Telegram client, based on Telegram X. Not affiliated with Telegram.',
           font=font('Regular', 21), fill=(255, 255, 255, 190), anchor='ls')
    finish(img, name)


# ---------------------------------------------------------------- features 2: styles and wallpapers

def features2(file_name='x_features_2.png', note='XGram · unofficial Telegram client, based on Telegram X · t.me/XGramTD'):
    W, H = 1600, 900
    img = backdrop(W, H, angle=22, glow=(900, -360, 1900, 420, 40))
    M = 64
    d = ImageDraw.Draw(img)
    d.text((M * K, 112 * K), 'Make it yours', font=font('Bold', 64), fill=(255, 255, 255, 255), anchor='ls')
    d.text((M * K + 2 * K, 156 * K), 'Pick an app icon, and its theme and wallpaper come along',
           font=font('Medium', 30), fill=(255, 255, 255, 230), anchor='ls')
    styles6 = [('neon', 'Neon'), ('turbo', 'Turbo'), ('retro', 'Retro'), ('pixel', 'Pixel'), ('origami', 'Origami'), ('stardust', 'Stardust')]
    cw, gap = 225, 24
    x0 = M
    top, ch = 196, 410
    ipx = 96
    for i, (key, name) in enumerate(styles6):
        x = x0 + i * (cw + gap)
        wp = Image.open(os.path.join(WALLS, key + '.jpg')).convert('RGB')
        # crop to the card ratio, keeping the art's centre
        r = cw / ch
        wh = wp.height
        ww = int(wh * r)
        if ww > wp.width:
            ww = wp.width; wh = int(ww / r)
        left = (wp.width - ww) // 2
        up = (wp.height - wh) // 2
        wp = wp.crop((left, up, left + ww, up + wh)).resize((cw * K, ch * K), Image.LANCZOS)
        m = Image.new('L', (cw * K * 2, ch * K * 2), 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, cw * K * 2 - 1, ch * K * 2 - 1], radius=28 * K * 2, fill=255)
        card = wp.convert('RGBA')
        card.putalpha(m.resize((cw * K, ch * K), Image.LANCZOS))
        img = shadow_paste(img, card, x * K, top * K, blur=18 * K, dy=10 * K, alpha=100)
        ic = style_icon(key, ipx * K)
        img = shadow_paste(img, ic, (x + (cw - ipx) / 2) * K, (top + ch - ipx / 2) * K, blur=10 * K, dy=6 * K, alpha=110)
        d = ImageDraw.Draw(img)
        d.text(((x + cw / 2) * K, (top + ch + ipx / 2 + 44) * K), name, font=font('Bold', 31), fill=(255, 255, 255, 255), anchor='ms')

    # bottom strip: the ten color icons, and the facts
    by = 790
    colours = ['XGram', 'Aurora', 'Sunset', 'Midnight', 'Emerald', 'Gold', 'Glass', 'Rose', 'Night', 'Mono']
    s = 56
    g = 14
    for i, n in enumerate(colours):
        t = app_icon(VARIANTS[n], s * K)
        img = shadow_paste(img, t, (M + i * (s + g)) * K, (by - s / 2) * K, blur=6 * K, dy=3 * K, alpha=60)
    d = ImageDraw.Draw(img)
    fx = M + 10 * (s + g) + 24
    d.text((fx * K, (by - 6) * K), '+ 10 color icons, each with its own theme', font=font('Bold', 29), fill=(255, 255, 255, 255), anchor='ls')
    d.text((fx * K, (by + 30) * K), '6 icon sets · Material You · bubble and emoji sizes', font=font('Regular', 26), fill=(255, 255, 255, 225), anchor='ls')
    d.text((M * K, (H - 30) * K), note,
           font=font('Regular', 21), fill=(255, 255, 255, 190), anchor='ls')
    finish(img, file_name)


JOBS = dict(header=header, avatar=avatar, release=release, features=features2)

if __name__ == '__main__':
    for key in (sys.argv[1:] or JOBS):
        JOBS[key]()
