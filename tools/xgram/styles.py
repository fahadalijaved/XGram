"""XGram icon *styles* (Neon, Turbo, Retro, Pixel, Origami, Stardust): one shape list per layer, drawn both as
Android vector XML (the real launcher icons) and with PIL (previews and legacy PNGs), so the two always match."""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw

NL = chr(10)
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'res')

# ---------------------------------------------------------------- glyph geometry (108 viewport), as gen_android.py
W = 11.0
H = W / 2
P0 = (36.5, 71.5)
TIP = (76.5, 31.5)
_L = math.hypot(TIP[0] - P0[0], TIP[1] - P0[1])
U = ((TIP[0] - P0[0]) / _L, (TIP[1] - P0[1]) / _L)
V = (-U[1], U[0])
M = (P0[0] + U[0] * 26, P0[1] + U[1] * 26)
B0, B1 = (36.5, 36.5), (71.5, 71.5)


def add(a, b, k=1.0):
    return (a[0] + b[0] * k, a[1] + b[1] * k)


def arc(c, r, a0, a1, n=12):
    return [(c[0] + r * math.cos(a0 + (a1 - a0) * i / n), c[1] + r * math.sin(a0 + (a1 - a0) * i / n)) for i in range(n + 1)]


def ang(v):
    return math.atan2(v[1], v[0])


def _arc_between(c, r, d0, d1, via):
    """Arc around c from direction d0 to d1 passing through direction via."""
    a0, a1, av = ang(d0), ang(d1), ang(via)
    # choose the sweep that contains av
    for sweep in ((a1 - a0) % (2 * math.pi), (a1 - a0) % (2 * math.pi) - 2 * math.pi):
        t = ((av - a0) % (2 * math.pi)) if sweep > 0 else -((a0 - av) % (2 * math.pi))
        if (sweep > 0 and 0 <= t <= sweep) or (sweep < 0 and sweep <= t <= 0):
            return arc(c, r, a0, a0 + sweep, 14)
    raise ValueError


NEG_U = (-U[0], -U[1])
NEG_V = (-V[0], -V[1])


def front_outline():
    """The front stroke: rounded at the bottom left, tapering to a paper-plane point."""
    pts = [add(P0, V, H), add(M, V, H), TIP, add(M, NEG_V, H), add(P0, NEG_V, H)]
    pts += _arc_between(P0, H, NEG_V, V, NEG_U)[1:-1]
    return pts


def back_outline():
    """The back stroke as a closed stadium."""
    d = ((B1[0] - B0[0]) / math.hypot(B1[0] - B0[0], B1[1] - B0[1]), (B1[1] - B0[1]) / math.hypot(B1[0] - B0[0], B1[1] - B0[1]))
    n = (-d[1], d[0])
    nn = (-n[0], -n[1])
    pts = [add(B0, n, H), add(B1, n, H)]
    pts += _arc_between(B1, H, n, nn, d)[1:-1]
    pts += [add(B1, nn, H), add(B0, nn, H)]
    pts += _arc_between(B0, H, nn, n, (-d[0], -d[1]))[1:-1]
    return pts


def front_halves():
    """Origami: the front stroke split along its axis into the lit (upper-left) and shaded half."""
    lit = [P0] + _arc_between(P0, H, NEG_U, NEG_V, (NEG_U[0] + NEG_V[0], NEG_U[1] + NEG_V[1])) + [add(M, NEG_V, H), TIP, M]
    shade = [P0] + _arc_between(P0, H, NEG_U, V, (NEG_U[0] + V[0], NEG_U[1] + V[1])) + [add(M, V, H), TIP, M]
    return lit, shade


def translate(pts, dx, dy):
    return [(x + dx, y + dy) for (x, y) in pts]


def inside(pt, poly):
    x, y = pt
    c = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            c = not c
        j = i
    return c


def sparkle(c, r, inner=.26):
    pts = []
    for i in range(8):
        a = -math.pi / 2 + i * math.pi / 4
        rr = r if i % 2 == 0 else r * inner
        pts.append((c[0] + rr * math.cos(a), c[1] + rr * math.sin(a)))
    return pts


# ---------------------------------------------------------------- shapes
# paint: '#RRGGBB' / '#AARRGGBB', or ('lin', [(offset, color), ...], x0, y0, x1, y1)
def poly(pts, paint, alpha=None):
    return dict(k='poly', pts=pts, paint=paint, alpha=alpha)


def outline(pts, width, paint, alpha=None):
    return dict(k='outline', pts=pts, w=width, paint=paint, alpha=alpha)


def line(a, b, width, paint, alpha=None):
    return dict(k='line', a=a, b=b, w=width, paint=paint, alpha=alpha)


def circle(c, r, paint, alpha=None):
    return dict(k='circle', c=c, r=r, paint=paint, alpha=alpha)


def rects(items, paint, alpha=None):
    return dict(k='rects', rects=items, paint=paint, alpha=alpha)


def lin(stops, x0=0, y0=0, x1=108, y1=108):
    return ('lin', stops, x0, y0, x1, y1)


FULL = [(0, 0), (108, 0), (108, 108), (0, 108)]

# ---------------------------------------------------------------- styles


def neon():
    bg = [poly(FULL, lin([(0, '#1C0A3A'), (1, '#04030E')])),
          circle((54, 54), 34, '#7B2CFF', .10), circle((54, 54), 22, '#7B2CFF', .08)]
    back, front = back_outline(), front_outline()
    fg = []
    for w, a in ((8, .10), (5, .22)):
        fg.append(outline(back, w, '#00E5FF', a))
    fg += [outline(back, 2.2, '#00E5FF'), outline(back, .8, '#E6FFFF', .9)]
    fg.append(poly(front, '#13092B'))  # hides the back tube where the front one crosses it
    for w, a in ((8, .10), (5, .22)):
        fg.append(outline(front, w, '#FF2BD6', a))
    fg += [outline(front, 2.2, '#FF2BD6'), outline(front, .8, '#FFE6F9', .9)]
    return bg, fg


def xform(op, k, dx, dy):
    """Scales an op about the centre by k and moves it."""
    t = lambda p: (54 + (p[0] - 54) * k + dx, 54 + (p[1] - 54) * k + dy)
    op = dict(op)
    if 'pts' in op:
        op['pts'] = [t(p) for p in op['pts']]
    if 'a' in op:
        op['a'], op['b'] = t(op['a']), t(op['b'])
    if 'w' in op:
        op['w'] = op['w'] * k
    return op


def turbo():
    bg = [poly(FULL, lin([(0, '#FFB400'), (.5, '#FF6A00'), (1, '#FF2D55')]))]
    k, dx, dy = .84, 4.5, -4.5
    front = front_outline()
    glyph = [line(B0, B1, W, '#FFFFFF', .7)]
    for d, a in ((11, .18), (5.5, .34)):
        glyph.append(poly(translate(front, -U[0] * d, -U[1] * d), '#FFFFFF', a))
    glyph.append(poly(translate(front, 0, 1.6), '#000000', .14))
    glyph.append(poly(front, '#FFFFFF'))
    fg = [xform(op, k, dx, dy) for op in glyph]
    # speed lines streaming behind the plane, inside the launcher's visible circle
    tail = (54 + (P0[0] - 54) * k + dx, 54 + (P0[1] - 54) * k + dy)
    for off, start, length, a in ((-7.5, 9, 7, .5), (0, 15, 9, .75), (7.5, 9, 7, .5)):
        s0 = add(add(tail, NEG_U, start), V, off)
        fg.insert(1, line(s0, add(s0, NEG_U, length), 2.4, '#FFFFFF', a))
    return bg, fg


def retro():
    bg = [poly(FULL, lin([(0, '#F7E9C6'), (1, '#EED7A1')]))]
    for i in range(0, 24, 2):
        a0 = i * math.pi / 12
        bg.append(poly([(54, 54), (54 + 90 * math.cos(a0), 54 + 90 * math.sin(a0)),
                        (54 + 90 * math.cos(a0 + math.pi / 12), 54 + 90 * math.sin(a0 + math.pi / 12))], '#E8C47E', .45))
    back, front = back_outline(), front_outline()
    fg = [poly(translate(back, 2.4, 2.4), '#E63946'), poly(translate(front, 2.4, 2.4), '#E63946'),
          poly(back, '#2A9D8F'), poly(front, '#1D3557')]
    return bg, fg


def pixel():
    bg = [poly(FULL, lin([(0, '#2A1450'), (1, '#120A26')]))]
    stars = [(25, 27), (82, 24), (86, 66), (22, 80), (62, 87), (47, 21)]
    bg.append(rects([(x, y, 2.2, 2.2) for (x, y) in stars], '#FFFFFF', .55))
    front = front_outline()
    c = 4.0
    xs = [28 + c * i for i in range(14)]
    back_cells, front_cells = [], []
    for x in xs:
        for y in xs:
            ctr = (x + c / 2, y + c / 2)
            if inside(ctr, front):
                front_cells.append((x, y, c, c))
            else:
                # distance to the back segment
                px, py = ctr
                t = max(0, min(1, ((px - B0[0]) * (B1[0] - B0[0]) + (py - B0[1]) * (B1[1] - B0[1])) / ((B1[0] - B0[0]) ** 2 + (B1[1] - B0[1]) ** 2)))
                qx, qy = B0[0] + t * (B1[0] - B0[0]), B0[1] + t * (B1[1] - B0[1])
                if math.hypot(px - qx, py - qy) <= H + .3:
                    back_cells.append((x, y, c, c))
    allc = back_cells + front_cells
    fg = [rects([(x + 2, y + 2, w, h) for (x, y, w, h) in allc], '#000000', .35),
          rects(back_cells, '#4DD0E1'), rects(front_cells, '#FFFFFF')]
    return bg, fg


def origami():
    bg = [poly(FULL, lin([(0, '#5ED3F7'), (1, '#2F6FED')]))]
    lit, shade = front_halves()
    fg = [line(B0, B1, W, '#FFFFFF', .72),
          poly(translate(front_outline(), 0, 1.8), '#000000', .18),
          poly(shade, '#A8C6F0'), poly(lit, '#FFFFFF')]
    return bg, fg


def stardust():
    bg = [poly(FULL, lin([(0, '#6B93FF'), (.5, '#976FFF'), (1, '#E46ACE')]))]
    front = front_outline()
    fg = [line(B0, B1, W, '#FFFFFF', .7),
          poly(translate(front, 0, 1.6), '#000000', .16),
          poly(front, '#FFFFFF'),
          poly(sparkle((53, 29.5), 7.2), '#FFFFFF', .95),
          poly(sparkle((79, 55), 4.2), '#FFFFFF', .85),
          poly(sparkle((29.5, 54), 3.2), '#FFFFFF', .7)]
    return bg, fg


STYLES = [
    ('neon', 'Neon', neon), ('turbo', 'Turbo', turbo), ('retro', 'Retro', retro),
    ('pixel', 'Pixel', pixel), ('origami', 'Origami', origami), ('stardust', 'Stardust', stardust),
]

# ---------------------------------------------------------------- PIL renderer
S = 8


def rgba(h):
    h = h.lstrip('#')
    if len(h) == 8:
        return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16))
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def paint_image(paint, size):
    if isinstance(paint, str):
        return Image.new('RGBA', size, rgba(paint))
    _, stops, x0, y0, x1, y1 = paint
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w].astype(float) / S
    dx, dy = x1 - x0, y1 - y0
    t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / (dx * dx + dy * dy), 0, 1)
    out = np.zeros((h, w, 4))
    offs = [o for o, _ in stops]
    cols = [np.array(rgba(c), float) for _, c in stops]
    for ch in range(4):
        out[..., ch] = np.interp(t, offs, [c[ch] for c in cols])
    return Image.fromarray(out.astype(np.uint8))


def sp(p):
    return (p[0] * S, p[1] * S)


def mask_of(op, size):
    m = Image.new('L', size, 0)
    d = ImageDraw.Draw(m)
    k = op['k']
    if k == 'poly':
        d.polygon([sp(p) for p in op['pts']], fill=255)
    elif k == 'outline':
        pts = [sp(p) for p in op['pts']]
        w = op['w'] * S
        d.line(pts + [pts[0]], fill=255, width=int(round(w)), joint='curve')
        r = w / 2
        for (x, y) in pts:
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    elif k == 'line':
        a, b, w = op['a'], op['b'], op['w']
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        vx, vy = -uy, ux
        h = w / 2
        d.polygon([sp((a[0] + vx * h, a[1] + vy * h)), sp((b[0] + vx * h, b[1] + vy * h)), sp((b[0] - vx * h, b[1] - vy * h)), sp((a[0] - vx * h, a[1] - vy * h))], fill=255)
        for c in (a, b):
            d.ellipse([sp((c[0] - h, c[1] - h)), sp((c[0] + h, c[1] + h))], fill=255)
    elif k == 'circle':
        c, r = op['c'], op['r']
        d.ellipse([sp((c[0] - r, c[1] - r)), sp((c[0] + r, c[1] + r))], fill=255)
    elif k == 'rects':
        for (x, y, w, h) in op['rects']:
            d.rectangle([sp((x, y)), sp((x + w, y + h))], fill=255)
    return m


def render_layer(ops, img):
    size = img.size
    for op in ops:
        m = mask_of(op, size)
        if op.get('alpha') is not None:
            m = m.point(lambda p, a=op['alpha']: int(p * a))
        pi = paint_image(op['paint'], size)
        a = np.array(pi.split()[3], float) * (np.array(m, float) / 255)
        pi.putalpha(Image.fromarray(a.astype(np.uint8)))
        img = Image.alpha_composite(img, pi)
    return img


def render(fn, out=216, corner=.3):
    bg, fg = fn()
    size = (108 * S, 108 * S)
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    img = render_layer(bg, img)
    img = render_layer(fg, img)
    img = img.crop((18 * S, 18 * S, 90 * S, 90 * S)).resize((out, out), Image.LANCZOS)
    mask = Image.new('L', (out, out), 0)
    if corner is None:
        ImageDraw.Draw(mask).ellipse([0, 0, out - 1, out - 1], fill=255)
    else:
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, out - 1, out - 1], radius=int(out * corner * .8), fill=255)
    res = Image.new('RGBA', (out, out), (0, 0, 0, 0))
    res.paste(img, (0, 0), mask)
    return res


# ---------------------------------------------------------------- vector XML
def f(v):
    return ('%.3f' % v).rstrip('0').rstrip('.')


def argb(c, alpha=None):
    c = c.lstrip('#')
    if len(c) == 6:
        c = 'FF' + c
    return '#' + c.upper()


def gradient_attr(attr, paint):
    _, stops, x0, y0, x1, y1 = paint
    lines = ['', '    <aapt:attr name="%s">' % attr,
             '      <gradient android:type="linear" android:startX="%s" android:startY="%s" android:endX="%s" android:endY="%s">' % (f(x0), f(y0), f(x1), f(y1))]
    for o, c in stops:
        lines.append('        <item android:offset="%s" android:color="%s" />' % (f(o), argb(c)))
    lines += ['      </gradient>', '    </aapt:attr>']
    return NL.join(lines)


def path_data(op):
    k = op['k']
    if k in ('poly', 'outline'):
        pts = op['pts']
        return 'M' + ' L'.join('%s,%s' % (f(x), f(y)) for (x, y) in pts) + ' Z'
    if k == 'line':
        a, b = op['a'], op['b']
        return 'M%s,%s L%s,%s' % (f(a[0]), f(a[1]), f(b[0]), f(b[1]))
    if k == 'circle':
        (cx, cy), r = op['c'], op['r']
        return 'M%s,%sm-%s,0a%s,%s 0 1,0 %s,0a%s,%s 0 1,0 -%s,0' % (f(cx), f(cy), f(r), f(r), f(r), f(2 * r), f(r), f(r), f(2 * r))
    if k == 'rects':
        return ' '.join('M%s,%s h%s v%s h-%s z' % (f(x), f(y), f(w), f(h), f(w)) for (x, y, w, h) in op['rects'])
    raise ValueError(k)


def xml_op(op):
    d = path_data(op)
    stroke = op['k'] in ('outline', 'line')
    attrs = ' android:pathData="%s"' % d
    if stroke:
        attrs += ' android:strokeWidth="%s" android:strokeLineCap="round" android:strokeLineJoin="round"' % f(op['w'])
        if op.get('alpha') is not None:
            attrs += ' android:strokeAlpha="%s"' % f(op['alpha'])
        key = 'android:strokeColor'
    else:
        if op.get('alpha') is not None:
            attrs += ' android:fillAlpha="%s"' % f(op['alpha'])
        key = 'android:fillColor'
    paint = op['paint']
    if isinstance(paint, str):
        return '  <path%s %s="%s" />%s' % (attrs, key, argb(paint), NL)
    return '  <path%s>%s%s  </path>%s' % (attrs, gradient_attr(key, paint), NL, NL)


def vector(comment, ops):
    out = NL.join(['<?xml version="1.0" encoding="utf-8"?>', '<!-- %s -->' % comment,
                   '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
                   '  xmlns:aapt="http://schemas.android.com/aapt"',
                   '  android:width="108dp"', '  android:height="108dp"',
                   '  android:viewportWidth="108"', '  android:viewportHeight="108">', ''])
    for op in ops:
        out += xml_op(op)
    return out + '</vector>' + NL


def adaptive(key):
    return NL.join(['<?xml version="1.0" encoding="utf-8"?>',
                    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
                    '  <background android:drawable="@drawable/xgram_ic_bg_%s" />' % key,
                    '  <foreground android:drawable="@drawable/xgram_ic_fg_%s" />' % key,
                    '  <monochrome android:drawable="@drawable/xgram_ic_monochrome" />',
                    '</adaptive-icon>', ''])


def write(path, text):
    with open(path, 'w', encoding='utf-8', newline=NL) as fh:
        fh.write(text)


def write_resources():
    for key, name, fn in STYLES:
        bg, fg = fn()
        write(os.path.join(RES, 'drawable', 'xgram_ic_bg_%s.xml' % key), vector('XGram icon style "%s": background' % name, bg))
        write(os.path.join(RES, 'drawable', 'xgram_ic_fg_%s.xml' % key), vector('XGram icon style "%s": the woven X' % name, fg))
        write(os.path.join(RES, 'mipmap-anydpi-v26', 'xgram_icon_%s.xml' % key), adaptive(key))
        render(fn, 192, corner=.3).save(os.path.join(RES, 'mipmap-xxxhdpi', 'xgram_icon_%s.png' % key))
    print('wrote', len(STYLES), 'styles')


def sheet(path):
    cols = len(STYLES)
    im = Image.new('RGBA', (cols * 240, 260 + 130), (28, 28, 30, 255))
    d = ImageDraw.Draw(im)
    for i, (key, name, fn) in enumerate(STYLES):
        r = render(fn)
        im.paste(r, (i * 240 + 12, 12), r)
        d.text((i * 240 + 100, 236), name, fill=(230, 230, 230, 255))
        small = render(fn, 96, corner=None)
        im.paste(small, (i * 240 + 72, 270), small)
    im.save(path)
    print('sheet', path)


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'write':
        write_resources()
    else:
        sheet(sys.argv[1] if len(sys.argv) > 1 else 'styles_sheet.png')
