import math, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 8
def P(x, y): return (x * S, y * S)

def rgba(h, a=255):
    h = h.lstrip('#')
    if len(h) == 8:
        return (int(h[2:4],16), int(h[4:6],16), int(h[6:8],16), int(h[0:2],16))
    return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)

def gradient(size, c0, c1, radial=False):
    w, h = size
    if radial:
        yy, xx = np.mgrid[0:h, 0:w]
        t = np.clip(np.hypot(xx - w * .3, yy - h * .25) / (w * 1.0), 0, 1)[..., None]
    else:
        t = (np.add.outer(np.arange(h), np.arange(w)) / (w + h - 2))[..., None]
    a = np.array(rgba(c0), float); b = np.array(rgba(c1), float)
    return Image.fromarray((a * (1 - t) + b * t).astype(np.uint8))

def capsule(d, x0, y0, x1, y1, w, fill):
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy); ux, uy = dx / L, dy / L; vx, vy = -uy, ux; h = w / 2
    d.polygon([P(x0 + vx*h, y0 + vy*h), P(x1 + vx*h, y1 + vy*h), P(x1 - vx*h, y1 - vy*h), P(x0 - vx*h, y0 - vy*h)], fill=fill)
    for (x, y) in ((x0, y0), (x1, y1)):
        d.ellipse([P(x - h, y - h), P(x + h, y + h)], fill=fill)

# Geometry (108 space), centre 54,54
W = 11.0
def glyph_masks(size):
    a = Image.new('L', size, 0); da = ImageDraw.Draw(a)
    b = Image.new('L', size, 0); db = ImageDraw.Draw(b)
    # Stroke B "\\": continuous, behind
    capsule(db, 36.5, 36.5, 71.5, 71.5, W, 255)
    # Stroke A "/": rounded at the bottom left, tapering to a paper-plane point at the top right
    h = W / 2
    x0, y0 = 36.5, 71.5
    tipx, tipy = 76.5, 31.5
    ux, uy = (tipx - x0) / math.hypot(tipx - x0, tipy - y0), (tipy - y0) / math.hypot(tipx - x0, tipy - y0)
    vx, vy = -uy, ux
    mx, my = x0 + ux * 26, y0 + uy * 26   # where the taper starts
    da.polygon([P(x0 + vx*h, y0 + vy*h), P(mx + vx*h, my + vy*h), P(tipx, tipy), P(mx - vx*h*1.0, my - vy*h*1.0), P(x0 - vx*h, y0 - vy*h)], fill=255)
    da.ellipse([P(x0 - h, y0 - h), P(x0 + h, y0 + h)], fill=255)
    return a, b

def render(v):
    size = (108 * S, 108 * S)
    img = gradient(size, *v['bg'], radial=v.get('radial', False))
    if v.get('glass'):
        ov = Image.new('RGBA', size, (0,0,0,0)); d = ImageDraw.Draw(ov)
        d.ellipse([P(10,8), P(58,56)], fill=(255,255,255,40))
        d.ellipse([P(52,50), P(110,108)], fill=(255,255,255,26))
        img = Image.alpha_composite(img, ov)
    if v.get('ring'):
        ov = Image.new('RGBA', size, (0,0,0,0)); d = ImageDraw.Draw(ov)
        d.ellipse([P(23,23), P(85,85)], outline=rgba(v['ring']), width=int(2.2 * S))
        img = Image.alpha_composite(img, ov)
    a, b = glyph_masks(size)
    if v.get('glow'):
        glow = Image.new('RGBA', size, rgba(v['glow'], 0))
        m = Image.fromarray(np.maximum(np.array(a), np.array(b)))
        glow.putalpha(m.filter(ImageFilter.GaussianBlur(3.5 * S)).point(lambda p: int(p * .9)))
        img = Image.alpha_composite(img, glow)
    if v.get('shadow'):
        sh = Image.new('RGBA', size, (0, 0, 0, 0))
        m = Image.fromarray(np.maximum(np.array(a), np.array(b))).filter(ImageFilter.GaussianBlur(2.5 * S))
        sh.putalpha(m.point(lambda p: int(p * .35)))
        img.alpha_composite(sh, (int(0.0 * S), int(2.0 * S)))
    fb = gradient(size, *v['b']) if isinstance(v['b'], list) else Image.new('RGBA', size, rgba(v['b']))
    lb = Image.new('RGBA', size, (0, 0, 0, 0)); lb.paste(fb, (0, 0), b); img = Image.alpha_composite(img, lb)
    # shadow of the front stroke on the back one
    sh = Image.new('RGBA', size, (0, 0, 0, 0))
    sm = a.filter(ImageFilter.GaussianBlur(2.2 * S))
    sm = Image.fromarray((np.array(sm).astype(float) * (np.array(b) / 255.0) * 0.45).astype(np.uint8))
    sh.putalpha(sm); img = Image.alpha_composite(img, sh)
    fa = gradient(size, *v['a']) if isinstance(v['a'], list) else Image.new('RGBA', size, rgba(v['a']))
    la = Image.new('RGBA', size, (0, 0, 0, 0)); la.paste(fa, (0, 0), a); img = Image.alpha_composite(img, la)
    img = img.crop((18 * S, 18 * S, 90 * S, 90 * S)).resize((216, 216), Image.LANCZOS)
    mask = Image.new('L', (216, 216), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 215, 215], radius=52, fill=255)
    out = Image.new('RGBA', (216, 216), (0,0,0,0)); out.paste(img, (0, 0), mask)
    return out

V = [
 dict(name='XGram', bg=['#2AABEE','#6C5CE7'], a='#FFFFFF', b='#B3FFFFFF', shadow=True),
 dict(name='Aurora', bg=['#00C9A7','#845EC2'], a='#FFFFFF', b='#B3FFFFFF', shadow=True),
 dict(name='Sunset', bg=['#FFB347','#FF5E62'], a='#FFFFFF', b='#B3FFFFFF', shadow=True),
 dict(name='Midnight', bg=['#0B1026','#1F2C56'], a=['#00E5FF','#7C4DFF'], b='#8000E5FF', glow='#00E5FF'),
 dict(name='Emerald', bg=['#11998E','#38EF7D'], a='#FFFFFF', b='#B3FFFFFF', shadow=True),
 dict(name='Gold', bg=['#141414','#2E2A24'], a=['#F7D774','#C9932B'], b='#90C9932B', ring='#40E9C46A'),
 dict(name='Glass', bg=['#5B86E5','#9D50BB'], a='#FFFFFF', b='#A0FFFFFF', glass=True),
 dict(name='Mono', bg=['#F4F4F4','#DCDCDC'], a='#111111', b='#80111111'),
 dict(name='Night', bg=['#1A1A2E','#16213E'], a=['#4FC3F7','#7C4DFF'], b='#704FC3F7'),
 dict(name='Rose', bg=['#F857A6','#FF5858'], a='#FFFFFF', b='#B3FFFFFF', shadow=True),
]
cols = 5
sheet = Image.new('RGBA', (cols * 240, 2 * 260 + 140), (28, 28, 30, 255))
for i, v in enumerate(V):
    im = render(v)
    sheet.paste(im, ((i % cols) * 240 + 12, (i // cols) * 260 + 12), im)
    ImageDraw.Draw(sheet).text(((i % cols) * 240 + 100, (i // cols) * 260 + 236), v['name'], fill=(230,230,230,255))
# launcher-size row
for i, v in enumerate(V):
    im = render(v).resize((96, 96), Image.LANCZOS)
    sheet.paste(im, (12 + i * 118, 2 * 260 + 20), im)
sheet.save(sys.argv[1])
print('ok')
