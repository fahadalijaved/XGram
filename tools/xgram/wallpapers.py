"""XGram chat wallpapers for the six style icon themes (Neon, Turbo, Retro, Pixel, Origami, Stardust).

Each one redraws its icon's motif from styles.py as a full-screen picture, so the chat looks like the icon:
  neon      synthwave night: the icon's woven X as two huge neon tubes, a perspective grid, violet glow rings
  turbo     motion: speed streaks along the plane's 45 degree flight line and the icon's trailing ghost planes
  retro     70s print: a sunburst, misregistered rainbow arcs in red/teal/navy, halftone dots, paper grain
  pixel     8-bit night sky: dithered bands, pixel stars, moon, clouds, a skyline and a pixel paper plane
  origami   folded paper: a low-poly field lit like the icon's two halves, small paper planes with dashed paths
  stardust  pastel nebula: soft blobs, a milky band, 4-point sparkles and fine specks

The centre of every picture (where bubbles sit) stays in one lightness band, between or away from the theme's
bubble colours, and the strong accents go to the edges and corners.

  python tools/xgram/wallpapers.py            -> app/src/main/assets/xgram_wallpapers/<key>.jpg and the preview
  python tools/xgram/wallpapers.py preview    -> only docs/xgram/wallpapers/preview.png (and <key>_chat.png)
"""
import math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import styles as st  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
OUT = os.path.join(ASSETS, 'xgram_wallpapers')
THEMES = os.path.join(ASSETS, 'xgram_themes')
DOCS = os.path.join(ROOT, 'docs', 'xgram', 'wallpapers')

W, H = 720, 1480  # TGX decodes a custom wallpaper at most 1480 px on its long side and centre-crops it
SS = 3            # supersampling for hard-edged shapes


# ---------------------------------------------------------------- helpers
def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def gradient(stops, p0, p1):
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    t = np.clip(((xx - p0[0]) * dx + (yy - p0[1]) * dy) / (dx * dx + dy * dy), 0, 1)
    out = np.zeros((H, W, 3))
    offs = [o for o, _ in stops]
    cols = [rgb(c) for _, c in stops]
    for ch in range(3):
        out[..., ch] = np.interp(t, offs, [c[ch] for c in cols])
    return out


def radial(cx, cy, sx, sy=None):
    sy = sx if sy is None else sy
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    return np.exp(-(((xx - cx) / sx) ** 2 + ((yy - cy) / sy) ** 2) / 2)


def mask(fn, blur=0, ss=SS):
    """fn(draw, s) draws in white with coordinates scaled by s; returns a float mask 0..1 at W x H."""
    m = Image.new('L', (W * ss, H * ss), 0)
    fn(ImageDraw.Draw(m), ss)
    if ss != 1:
        m = m.resize((W, H), Image.BOX)
    if blur:
        m = m.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(m, float) / 255


def sc(pts, s):
    return [(x * s, y * s) for (x, y) in pts]


def d_poly(d, s, pts):
    d.polygon(sc(pts, s), fill=255)


def d_stroke(d, s, pts, w, closed=True):
    p = sc(pts, s)
    if closed:
        p = p + [p[0]]
    d.line(p, fill=255, width=max(1, int(round(w * s))), joint='curve')
    r = w * s / 2
    for (x, y) in (p if not closed else []):
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)


def d_circle(d, s, c, r):
    d.ellipse([(c[0] - r) * s, (c[1] - r) * s, (c[0] + r) * s, (c[1] + r) * s], fill=255)


def d_ring(d, s, c, r, w):
    d.ellipse([(c[0] - r) * s, (c[1] - r) * s, (c[0] + r) * s, (c[1] + r) * s], outline=255, width=max(1, int(w * s)))


def glyph(pts, k, cx, cy):
    """Icon geometry (108 viewport, centre 54,54) scaled by k around (cx, cy)."""
    return [(cx + (x - 54) * k, cy + (y - 54) * k) for (x, y) in pts]


def over(a, color, m, alpha=1.0):
    c = rgb(color) if isinstance(color, str) else color
    mm = np.clip(m * alpha, 0, 1)[..., None]
    return a * (1 - mm) + c * mm


def add(a, color, m, alpha=1.0):
    c = rgb(color) if isinstance(color, str) else color
    return a + c * (m * alpha)[..., None]


def fbm(seed, scales=((5, 1.0), (10, .5), (20, .25), (40, .12))):
    """Smooth fractal noise in -1..1, a few octaves of upscaled random grids."""
    r = np.random.default_rng(seed)
    out = np.zeros((H, W))
    for cells, amp in scales:
        g = r.uniform(0, 255, (max(2, int(cells * H / W)), cells)).astype(np.uint8)
        out += amp * (np.asarray(Image.fromarray(g).resize((W, H), Image.BICUBIC), float) / 127.5 - 1)
    out = np.asarray(Image.fromarray(np.uint8(np.clip((out / np.abs(out).max() + 1) * 127.5, 0, 255))).filter(ImageFilter.GaussianBlur(8)), float)
    return out / 127.5 - 1


def finish(a, noise=1.5, seed=0):
    if noise:
        r = np.random.default_rng(seed)
        a = a + r.uniform(-noise, noise, (H, W, 1))
    return Image.fromarray(np.clip(np.round(a), 0, 255).astype(np.uint8))


U, V = st.U, st.V  # the plane's flight direction (up-right) and its normal, in icon space
NEG_U = st.NEG_U


# ---------------------------------------------------------------- Neon: synthwave night
def neon():
    a = gradient([(0, '#22165A'), (.5, '#191248'), (.8, '#100C34'), (1, '#06051A')], (W * .2, 0), (W * .8, H))
    a = add(a, '#7B2CFF', radial(W * .1, H * .12, 300), .10)
    a = add(a, '#FF2BD6', radial(W * .5, H * .79, 520, 90), .07)  # horizon haze
    rnd = random.Random(7)

    # faint stars in the sky
    stars = [(rnd.uniform(0, W), rnd.uniform(0, H * .74), rnd.choice((1, 1, 1.4, 2))) for _ in range(140)]
    m = mask(lambda d, s: [d_circle(d, s, (x, y), r) for (x, y, r) in stars])
    a = add(a, '#CFC3FF', m, .35)

    # perspective grid in the bottom quarter
    yh = H * .79
    def grid(d, s):
        for i in range(-14, 15):
            d_stroke(d, s, [(W / 2 + i * 12, yh), (W / 2 + i * 150, H + 40)], 1.6, closed=False)
        n = 9
        for j in range(1, n + 1):
            y = yh + (H + 20 - yh) * (j / n) ** 1.9
            d_stroke(d, s, [(-10, y), (W + 10, y)], 1.6, closed=False)
    g = mask(grid)
    fade = np.clip((np.mgrid[0:H, 0:W][0] - yh) / (H - yh), 0, 1) ** .8
    a = add(a, '#FF2BD6', mask(grid, blur=4, ss=1) * fade, .25)
    a = add(a, '#FF5FE0', g * fade, .30)
    a = add(a, '#FF2BD6', mask(lambda d, s: d_stroke(d, s, [(-10, yh), (W + 10, yh)], 2, False), blur=6, ss=1), .45)

    # violet glow rings at the edges
    for (c, r) in (((-20, 230), 150), ((W + 30, 1010), 190), ((W - 40, 120), 60), ((40, 1090), 46)):
        ring = mask(lambda d, s, c=c, r=r: d_ring(d, s, c, r, 3))
        a = add(a, '#7B2CFF', mask(lambda d, s, c=c, r=r: d_ring(d, s, c, r, 10), blur=12, ss=1), .55)
        a = add(a, '#A77BFF', ring, .45)

    # the icon's woven X as two huge tubes: cyan stadium (back) and the magenta paper plane (front)
    k, cx, cy = 13.5, W / 2, H * .43
    back = glyph(st.back_outline(), k, cx, cy)
    front = glyph(st.front_outline(), k, cx, cy)
    hide = mask(lambda d, s: d_poly(d, s, front), blur=3)
    T = .62
    for pts, col, core, knock in ((back, '#00E5FF', '#E6FFFF', hide), (front, '#FF2BD6', '#FFE6F9', None)):
        layers = [(mask(lambda d, s, p=pts: d_stroke(d, s, p, 34), blur=34, ss=1), col, .30),
                  (mask(lambda d, s, p=pts: d_stroke(d, s, p, 14), blur=10, ss=1), col, .55),
                  (mask(lambda d, s, p=pts: d_stroke(d, s, p, 5)), col, .95),
                  (mask(lambda d, s, p=pts: d_stroke(d, s, p, 1.8)), core, .8)]
        for m, c, al in layers:
            if knock is not None:  # the front tube covers the back one where they cross, as in the icon
                m = m * (1 - knock)
            a = add(a, c, m, al * T)
    return finish(a, seed=1)


# ---------------------------------------------------------------- Turbo: speed streaks and ghost planes
def turbo():
    a = gradient([(0, '#FFD08E'), (.5, '#FFB487'), (1, '#FFA6B6')], (0, 0), (W, H))
    a = add(a, '#FFFFFF', radial(W * .95, H * .05, 260), .12)
    rnd = random.Random(3)
    u = (U[0], U[1])
    v = (V[0], V[1])
    # soft motion bands running along the flight line
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    pv = xx * v[0] + yy * v[1]
    wave = np.zeros((H, W))
    for per, amp in ((180, 1.0), (97, .6), (53, .35)):
        wave += amp * np.sin(pv / per * 2 * math.pi + rnd.uniform(0, 6.3))
    wave /= 1.95
    a = over(a, '#FFFFFF', np.clip(wave, 0, 1), .16)
    a = over(a, '#FF6A3A', np.clip(-wave, 0, 1), .07)

    def streak(d, s, head, length, w):
        tail = (head[0] - u[0] * length, head[1] - u[1] * length)
        d_poly(d, s, [(head[0] + v[0] * w / 2, head[1] + v[1] * w / 2), tail, (head[0] - v[0] * w / 2, head[1] - v[1] * w / 2)])
        d_circle(d, s, head, w / 2)

    # bundles of three streaks along the flight line, denser at the edges than in the middle
    bundles = [((660, 140), 520), ((150, 330), 380), ((720, 560), 460), ((300, 760), 300), ((90, 980), 420),
               ((700, 1140), 520), ((420, 1330), 360), ((200, 1500), 440), ((520, 40), 300), ((40, 640), 260)]
    for (hx, hy), L in bundles:
        al = rnd.uniform(.42, .58)
        def b(d, s, hx=hx, hy=hy, L=L):
            for off, kl, kw, lag in ((-26, .62, .7, 40), (0, 1.0, 1.0, 0), (26, .8, .8, 22)):
                h = (hx + v[0] * off - u[0] * lag, hy + v[1] * off - u[1] * lag)
                streak(d, s, h, L * kl, 11 * kw)
        a = over(a, '#FFFFFF', mask(b), al)

    # the icon's trailing ghost planes, big at the top right and small at the bottom left
    # (the copies trail as crisp outlines, so the plane reads as one shape moving fast)
    for (k, cx, cy, al, lw) in ((6.0, W - 150, 270, .9, 3.2), (3.4, 150, H - 330, .82, 2.4)):
        front = st.front_outline()
        body = mask(lambda d, s, p=glyph(front, k, cx, cy): d_poly(d, s, p), blur=1.5)
        for dd, aa in ((30, .35), (15, .6), (0, 1.0)):
            pts = glyph(st.translate(front, -U[0] * dd, -U[1] * dd), k, cx, cy)
            if dd == 0:
                sh = glyph(st.translate(front, 0, 1.6), k, cx, cy)
                a = over(a, '#B4402A', mask(lambda d, s, p=sh: d_poly(d, s, p), blur=6, ss=1), .12)
                a = over(a, '#FFFFFF', mask(lambda d, s, p=pts: d_poly(d, s, p)), al)
            else:
                a = over(a, '#FFFFFF', mask(lambda d, s, p=pts: d_stroke(d, s, p, lw)) * (1 - body), al * aa)
    # chevrons: little motion marks pointing along the flight line
    for (cx, cy, r) in ((560, 470, 26), (600, 430, 20), (200, 1180, 22), (236, 1144, 17), (90, 140, 18)):
        def chev(d, s, cx=cx, cy=cy, r=r):
            p0 = (cx - u[0] * r + v[0] * r, cy - u[1] * r + v[1] * r)
            p2 = (cx - u[0] * r - v[0] * r, cy - u[1] * r - v[1] * r)
            d_stroke(d, s, [p0, (cx, cy), p2], 7, closed=False)
        a = over(a, '#FFFFFF', mask(chev), .30)
    return finish(a, seed=2)


# ---------------------------------------------------------------- Retro: 70s print
def retro():
    a = gradient([(0, '#EDD39A'), (1, '#DDB574')], (0, 0), (W * .3, H))
    # sunburst from below the bottom left corner, like the icon's rays
    c0 = (-140, H + 160)
    n = 40
    def rays(d, s):
        for i in range(0, n, 2):
            a0 = i * 2 * math.pi / n
            a1 = a0 + 2 * math.pi / n
            d_poly(d, s, [c0, (c0[0] + 3000 * math.cos(a0), c0[1] + 3000 * math.sin(a0)),
                          (c0[0] + 3000 * math.cos(a1), c0[1] + 3000 * math.sin(a1))])
    a = over(a, '#C99A55', mask(rays), .16)

    # rainbow arcs hugging two corners: teal and navy with a misregistered red print underneath
    def arcs(center, r0, sign):
        out = a
        bands = ((r0, r0 + 44, '#1D3557'), (r0 + 62, r0 + 106, '#2A9D8F'), (r0 + 124, r0 + 168, '#E8A33D'))
        off = 9 * sign
        for (ri, ro, col) in bands:
            def band(d, s, c=center, ri=ri, ro=ro, dx=0):
                d.ellipse([(c[0] + dx - ro) * s, (c[1] + dx - ro) * s, (c[0] + dx + ro) * s, (c[1] + dx + ro) * s], fill=255)
                d.ellipse([(c[0] + dx - ri) * s, (c[1] + dx - ri) * s, (c[0] + dx + ri) * s, (c[1] + dx + ri) * s], fill=0)
            red = mask(lambda d, s, f=band: f(d, s, dx=off))
            out = over(out, '#E63946', red, .9)
            out = over(out, col, mask(band), .95)
        return out
    a = arcs((-30, H + 30), 150, 1)
    a = arcs((W + 30, -30), 90, -1)
    # a red sun peeking above the bottom-left arcs
    a = over(a, '#E63946', mask(lambda d, s: d_circle(d, s, (-30 + 9, H + 30 + 9), 112)), .9)
    a = over(a, '#F2B33D', mask(lambda d, s: d_circle(d, s, (-30, H + 30), 112)), 1)

    # halftone dots in the other two corners
    def halftone(d, s, corner, color_sign):
        cx, cy = corner
        for gy in range(-2, 20):
            for gx in range(-2, 20):
                x = cx + gx * 22 * color_sign[0] + (11 if gy % 2 else 0) * color_sign[0]
                y = cy + gy * 22 * color_sign[1]
                dist = math.hypot(x - cx, y - cy)
                r = 8.5 * max(0, 1 - dist / 330)
                if r > .6:
                    d_circle(d, s, (x, y), r)
    a = over(a, '#1D3557', mask(lambda d, s: halftone(d, s, (0, 0), (1, 1))), .22)
    a = over(a, '#E63946', mask(lambda d, s: halftone(d, s, (W, H), (-1, -1))), .26)

    # paper grain: fine noise plus soft blotches
    r = np.random.default_rng(5)
    fine = r.normal(0, 2.0, (H, W))
    blot = np.asarray(Image.fromarray(np.uint8(np.clip(r.normal(128, 40, (H // 8, W // 8)), 0, 255))).resize((W, H), Image.BICUBIC)
                      .filter(ImageFilter.GaussianBlur(6)), float) - 128
    a = a + (fine + blot * .06)[..., None]
    return finish(a, noise=0)


# ---------------------------------------------------------------- Pixel: 8-bit night sky (8 px cells)
def pixel():
    C = 8
    gw, gh = W // C, H // C  # 90 x 185
    rnd = random.Random(11)
    bands = [rgb(c) for c in ('#17163F', '#1B1A47', '#1F1D4F', '#242057', '#2A2360', '#31276A')]
    bayer = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], float) / 16
    yy, xx = np.mgrid[0:gh, 0:gw].astype(float)
    t = np.clip(yy / (gh * .86) * .9 + xx / gw * .1, 0, 1) * (len(bands) - 1)
    idx = np.clip(np.floor(t + bayer[yy.astype(int) % 4, xx.astype(int) % 4]), 0, len(bands) - 1).astype(int)
    g = np.array(bands)[idx]

    def put(x, y, col, al=1.0):
        if 0 <= x < gw and 0 <= y < gh:
            g[y, x] = g[y, x] * (1 - al) + rgb(col) * al

    # moon with a dithered halo, top right
    mx, my, mr = 72, 21, 7
    for y in range(my - 14, my + 15):
        for x in range(mx - 14, mx + 15):
            dd = math.hypot(x - mx, y - my)
            if dd <= mr:
                put(x, y, '#ECE6FF')
            elif dd <= mr + 3 and (x + y) % 2 == 0:
                put(x, y, '#5A3C96', .5)
            elif dd <= mr + 6 and (x + y) % 4 == 0:
                put(x, y, '#4A2E86', .4)
    for (x, y) in ((70, 19), (71, 19), (74, 23), (75, 24), (74, 24), (69, 24), (73, 17)):
        put(x, y, '#C6BCEB')

    # pixel clouds near the edges
    def cloud(cx, cy, rows, col, hi):
        for r_i, (dx, w) in enumerate(rows):
            for x in range(cx + dx, cx + dx + w):
                put(x, cy + r_i, hi if r_i == 0 else col, .9)
    cloud(2, 44, [(4, 6), (2, 11), (0, 15), (1, 16)], '#3B2672', '#4C3388')
    cloud(66, 98, [(5, 5), (2, 12), (0, 17), (3, 14)], '#36226A', '#47307F')
    cloud(30, 8, [(3, 5), (0, 10), (2, 9)], '#2E1C5E', '#3D2875')

    # stars: single cells and plus shapes
    for _ in range(120):
        x, y = rnd.randrange(gw), rnd.randrange(int(gh * .78))
        if math.hypot(x - mx, y - my) < 10:
            continue
        put(x, y, '#FFFFFF' if rnd.random() < .75 else '#4DD0E1', rnd.choice((.35, .55, .55, .75)))
    for _ in range(16):
        x, y = rnd.choice((rnd.randrange(2, 16), rnd.randrange(74, 88))), rnd.randrange(4, int(gh * .74))
        col = rnd.choice(('#FFFFFF', '#4DD0E1'))
        put(x, y, col, .9)
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(x + dx, y + dy, col, .45)

    # the icon's pixel paper plane with a dashed cyan trail, upper left
    front = st.front_outline()
    px0, py0 = 5, 62
    cells = [(i, j) for j in range(14) for i in range(14) if st.inside((28 + 4 * i + 2, 28 + 4 * j + 2), front)]
    back = []
    for j in range(14):
        for i in range(14):
            cx_, cy_ = 28 + 4 * i + 2, 28 + 4 * j + 2
            t_ = max(0, min(1, ((cx_ - st.B0[0]) + (cy_ - st.B0[1])) / (2 * (st.B1[0] - st.B0[0]))))
            if (i, j) not in cells and math.hypot(cx_ - st.B0[0] - t_ * (st.B1[0] - st.B0[0]), cy_ - st.B0[1] - t_ * (st.B1[1] - st.B0[1])) <= st.H + .3:
                back.append((i, j))
    for (i, j) in cells + back:
        put(px0 + i + 1, py0 + j + 1, '#0B0619', .6)
    for (i, j) in back:
        put(px0 + i, py0 + j, '#4DD0E1', .9)
    for (i, j) in cells:
        put(px0 + i, py0 + j, '#FFFFFF', .95)
    for n in range(1, 7):
        x, y = px0 + 1 - n * 2, py0 + 13 + n * 2
        if n % 2 == 0:
            put(x, y, '#4DD0E1', .7)
            put(x + 1, y - 1, '#4DD0E1', .7)

    # mountains and a skyline at the bottom, darker than the incoming bubbles
    ridge = []
    h = 34
    for x in range(gw):
        h += rnd.choice((-1, 0, 0, 1)) + (1 if x % 23 < 6 else 0) - (1 if x % 23 > 17 else 0)
        h = max(22, min(44, h))
        ridge.append(h)
    for x in range(gw):
        for y in range(gh - ridge[x], gh):
            put(x, y, '#1A1240')
        put(x, gh - ridge[x], '#2A1E5C')
    x = 0
    while x < gw:
        bw = rnd.randint(4, 9)
        bh = rnd.randint(9, 22) if rnd.random() < .8 else rnd.randint(22, 28)
        for xx_ in range(x, min(gw, x + bw)):
            for y in range(gh - bh, gh):
                put(xx_, y, '#0B0619')
        for wy in range(gh - bh + 2, gh - 2, 3):
            for wx in range(x + 1, min(gw, x + bw) - 1, 2):
                if rnd.random() < .28:
                    put(wx, wy, rnd.choice(('#4DD0E1', '#F2EEFF', '#FFC86B')), .75)
        x += bw + rnd.choice((0, 0, 1))

    img = Image.fromarray(np.clip(np.round(g), 0, 255).astype(np.uint8))
    return img.resize((W, H), Image.NEAREST)


# ---------------------------------------------------------------- Origami: folded paper
def origami():
    rnd = random.Random(21)
    cols, rows = 7, 13
    cw, ch = W / (cols - 1), H / (rows - 1)
    P = [[(i * cw + (rnd.uniform(-.32, .32) * cw if 0 < i < cols - 1 else 0),
           j * ch + (rnd.uniform(-.32, .32) * ch if 0 < j < rows - 1 else 0),
           rnd.uniform(0, 1) * 150) for i in range(cols)] for j in range(rows)]
    L = np.array([-.55, -.75, 1.0])
    L /= np.linalg.norm(L)
    tris = []
    for j in range(rows - 1):
        for i in range(cols - 1):
            a0, a1, b0, b1 = P[j][i], P[j][i + 1], P[j + 1][i], P[j + 1][i + 1]
            if rnd.random() < .5:
                tris += [(a0, a1, b1), (a0, b1, b0)]
            else:
                tris += [(a0, a1, b0), (a1, b1, b0)]
    shades = []
    for t in tris:
        p = [np.array(q) for q in t]
        nrm = np.cross(p[1] - p[0], p[2] - p[0])
        nrm = nrm / np.linalg.norm(nrm)
        if nrm[2] < 0:
            nrm = -nrm
        shades.append(float(np.dot(nrm, L)))
    lo, hi = min(shades), max(shades)
    img = Image.new('RGB', (W * SS, H * SS))
    d = ImageDraw.Draw(img)
    for t, sh in zip(tris, shades):
        k = (sh - lo) / (hi - lo)
        cy = sum(q[1] for q in t) / 3 / H
        lit = rgb('#C2E6F8') * (1 - cy) + rgb('#BAD3F7') * cy
        dark = rgb('#8FC1EC') * (1 - cy) + rgb('#8AA9E8') * cy
        col = dark + (lit - dark) * k
        d.polygon([(q[0] * SS, q[1] * SS) for q in t], fill=tuple(int(c) for c in col))
    # soft crease highlights on the fold lines
    for t in tris:
        if rnd.random() < .35:
            q0, q1 = t[0], t[1]
            d.line([(q0[0] * SS, q0[1] * SS), (q1[0] * SS, q1[1] * SS)], fill=(236, 248, 255), width=2)
    a = np.asarray(img.resize((W, H), Image.BOX), float)

    # small folded paper planes near the edges, with dashed flight paths
    lit, shade = st.front_halves()
    for (k, cx, cy, path) in ((4.4, 130, 250, [(-60, 520), (10, 470), (60, 380), (95, 300)]),
                              (3.4, W - 92, 840, [(W - 300, 1120), (W - 220, 1030), (W - 170, 960), (W - 128, 880)]),
                              (3.0, 170, H - 200, [(-30, H - 30), (40, H - 80), (95, H - 130), (140, H - 170)])):
        def dashes(d, s, path=path):
            pts = []
            for n in range(len(path) - 1):
                (x0, y0), (x1, y1) = path[n], path[n + 1]
                steps = int(math.hypot(x1 - x0, y1 - y0) / 4)
                pts += [(x0 + (x1 - x0) * q / steps, y0 + (y1 - y0) * q / steps) for q in range(steps)]
            for q in range(0, len(pts) - 4, 8):
                d_stroke(d, s, pts[q:q + 4], 3.2, closed=False)
        a = over(a, '#2F6FED', mask(dashes), .30)
        sh = glyph(st.translate(st.front_outline(), .8, 2.2), k, cx, cy)
        a = over(a, '#1C3F80', mask(lambda d, s, p=sh: d_poly(d, s, p), blur=5, ss=1), .22)
        a = over(a, '#A8C6F0', mask(lambda d, s, p=glyph(shade, k, cx, cy): d_poly(d, s, p)), 1)
        a = over(a, '#FFFFFF', mask(lambda d, s, p=glyph(lit, k, cx, cy): d_poly(d, s, p)), 1)

    # paper fibre
    r = np.random.default_rng(9)
    fib = np.asarray(Image.fromarray(np.uint8(np.clip(r.normal(128, 50, (H, W // 4)), 0, 255))).resize((W, H), Image.BICUBIC), float) - 128
    a = a + (fib * .03)[..., None]
    return finish(a, seed=3)


# ---------------------------------------------------------------- Stardust: pastel nebula
def stardust():
    a = gradient([(0, '#BCC8FF'), (.5, '#CDB8FF'), (1, '#EDB6DF')], (0, 0), (W, H))
    for (cx, cy, sx, sy, col, al) in ((W * .1, H * .2, 300, 260, '#A9B9FF', .55), (W * .9, H * .45, 280, 320, '#C9A8FF', .45),
                                      (W * .2, H * .78, 320, 260, '#F2AEDB', .45), (W * .8, H * .95, 260, 200, '#E2A8F0', .4),
                                      (W * .55, H * .1, 260, 200, '#D2C2FF', .35)):
        a = over(a, col, radial(cx, cy, sx, sy), al)
    # nebula wisps, stronger toward the sides
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    edge = .45 + .55 * np.abs(xx - W / 2) / (W / 2)
    n1, n2, n3 = fbm(31), fbm(32), fbm(33)
    a = over(a, '#F2A6D6', np.clip(n1 * 1.6, 0, 1) * edge, .55)
    a = over(a, '#98ADFF', np.clip(n2 * 1.6, 0, 1) * edge, .50)
    a = over(a, '#B596FF', np.clip(n3 * 1.6, 0, 1) * edge, .40)
    a = over(a, '#FFFFFF', np.clip((n1 + n2) * 1.2 - .3, 0, 1) * edge, .30)
    # milky band from the bottom left to the top right, along the plane's flight line
    p0, p1 = np.array([-40, H * .80]), np.array([W + 40, H * .22])
    dvec = (p1 - p0) / np.linalg.norm(p1 - p0)
    dist = np.abs((xx - p0[0]) * dvec[1] - (yy - p0[1]) * dvec[0])
    band = np.exp(-(dist / 120) ** 2 / 2)
    a = over(a, '#F4EEFF', band * (.75 + .25 * n3), .40)

    rnd = random.Random(17)
    # fine stardust specks, denser in the band
    specks = []
    for _ in range(1500):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H)
        b = band[int(min(H - 1, y)), int(min(W - 1, x))]
        if rnd.random() < .18 + .82 * b:
            specks.append((x, y, rnd.choice((.6, .8, 1.0, 1.0, 1.3))))
    a = over(a, '#FFFFFF', mask(lambda d, s: [d_circle(d, s, (x, y), r) for (x, y, r) in specks]), .55)

    # 4-point sparkles, mostly at the edges
    sparks = []
    for _ in range(26):
        edge = rnd.random() < .82
        x = rnd.choice((rnd.uniform(8, 110), rnd.uniform(W - 110, W - 8))) if edge else rnd.uniform(140, W - 140)
        y = rnd.uniform(20, H - 20)
        r = rnd.uniform(10, 28) if edge else rnd.uniform(5, 9)
        sparks.append((x, y, r, rnd.uniform(.7, .95)))
    for (x, y, r, al) in sparks:
        a = over(a, '#FFFFFF', radial(x, y, r * .9), al * .35)
        a = over(a, '#FFFFFF', mask(lambda d, s, x=x, y=y, r=r: d_poly(d, s, st.sparkle((x, y), r))), al)
    return finish(a, seed=4)


WALLS = [('neon', 'Neon', neon), ('turbo', 'Turbo', turbo), ('retro', 'Retro', retro),
         ('pixel', 'Pixel', pixel), ('origami', 'Origami', origami), ('stardust', 'Stardust', stardust)]


# ---------------------------------------------------------------- theme colours and readability numbers
def theme_colors(key):
    out = {}
    with open(os.path.join(THEMES, 'icon_%s.tgx-theme' % key), encoding='utf-8') as fh:
        for line in fh:
            line = line.strip()
            if ': #' not in line:
                continue
            names, _, val = line.rpartition(': ')
            for n in names.split(','):
                out[n.strip()] = val.strip()
    return out


def is_dark(key):
    with open(os.path.join(THEMES, 'icon_%s.tgx-theme' % key), encoding='utf-8') as fh:
        return any(l.strip().startswith('dark') for l in fh)


def lum(c):
    c = np.asarray(c, float) / 255
    c = np.where(c <= .03928, c / 12.92, ((c + .055) / 1.055) ** 2.4)
    return c[..., 0] * .2126 + c[..., 1] * .7152 + c[..., 2] * .0722


def lstar(y):
    return np.where(y > 216 / 24389, 116 * np.cbrt(y) - 16, y * 24389 / 27)


def lab(c):
    c = np.asarray(c, float) / 255
    c = np.where(c <= .04045, c / 12.92, ((c + .055) / 1.055) ** 2.4)
    m = np.array([[.4124, .3576, .1805], [.2126, .7152, .0722], [.0193, .1192, .9505]])
    xyz = c @ m.T / np.array([.9505, 1.0, 1.089])
    f = np.where(xyz > 216 / 24389, np.cbrt(xyz), (xyz * 24389 / 27 + 16) / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)


def contrast(y1, y2):
    hi, lo = max(y1, y2), min(y1, y2)
    return (hi + .05) / (lo + .05)


def stats(img, cols):
    a = np.asarray(img, float)
    mid = a[:, int(W * .15):int(W * .85)]
    y = lum(mid)
    L = lstar(y)
    ym = float(y.mean())
    yi, yo = float(lum(rgb(cols['bubbleIn_background'][:7]))), float(lum(rgb(cols['bubbleOut_background'][:7])))
    lm = lab(mid).reshape(-1, 3).mean(0)
    din = float(np.linalg.norm(lm - lab(rgb(cols['bubbleIn_background'][:7]))))
    dout = float(np.linalg.norm(lm - lab(rgb(cols['bubbleOut_background'][:7]))))
    return dict(din=din, dout=dout, L=float(L.mean()), L5=float(np.percentile(L, 3)), L95=float(np.percentile(L, 97)),
                cin=contrast(ym, yi), cout=contrast(ym, yo),
                win=float((np.abs(L - float(lstar(yi))) < 5).mean()), wout=float((np.abs(L - float(lstar(yo))) < 5).mean()))


# ---------------------------------------------------------------- mock chat preview
def font(size, bold=False):
    for f in (('segoeuib.ttf' if bold else 'segoeui.ttf'), ('arialbd.ttf' if bold else 'arial.ttf')):
        try:
            return ImageFont.truetype(os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts', f), size)
        except OSError:
            pass
    return ImageFont.load_default(size)


CHAT = [('date', 'Today'),
        ('in', 'Hey! Did you try the new icon themes?', '10:41'),
        ('out', 'Just switched mine. The wallpaper matches the icon now', '10:42'),
        ('in', 'Can you still read everything easily?', '10:42'),
        ('out', 'Yes, both bubbles stand out', '10:43'),
        ('in', 'This one is a longer message that wraps over a few lines, to check how a tall bubble sits on the art', '10:44'),
        ('out', 'Looks clean', '10:44'),
        ('service', 'Alex joined the group'),
        ('in', 'Nice, the corners stay busy but the middle is calm', '10:46'),
        ('out', 'Exactly what I wanted', '10:47'),
        ('in', 'Sending you the link', '10:47'),
        ('out', 'Thanks!', '10:48')]


def wrap(text, f, maxw):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if f.getlength(t) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    return lines + [cur]


def chat(img, cols, dark):
    S2 = 2
    im = img.convert('RGBA').resize((W * S2, H * S2), Image.BICUBIC)
    ov = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    ft, ftime, fsm = font(30 * S2), font(21 * S2), font(25 * S2, True)
    y = 40 * S2
    pill = (0, 0, 0, 90) if not dark else (0, 0, 0, 110)
    for item in CHAT:
        kind = item[0]
        if kind in ('date', 'service'):
            tw = fsm.getlength(item[1])
            x0 = (W * S2 - tw) / 2 - 22 * S2
            d.rounded_rectangle([x0, y, x0 + tw + 44 * S2, y + 46 * S2], radius=23 * S2, fill=pill)
            d.text((x0 + 22 * S2, y + 6 * S2), item[1], font=fsm, fill=(255, 255, 255, 255))
            y += 46 * S2 + 22 * S2
            continue
        side = 'bubbleIn' if kind == 'in' else 'bubbleOut'
        bg = cols[side + '_background']
        tc = cols[side + '_text']
        tm = cols[side + '_time']
        lines = wrap(item[1], ft, 470 * S2)
        lh = 40 * S2
        tw = max(ft.getlength(l) for l in lines)
        timew = ftime.getlength(item[2])
        lastw = ft.getlength(lines[-1])
        extra = 0 if lastw + timew + 24 * S2 <= tw else lh
        bw = max(tw, lastw + timew + 24 * S2) + 40 * S2
        bh = len(lines) * lh + 24 * S2 + extra
        x0 = 16 * S2 if kind == 'in' else W * S2 - 16 * S2 - bw
        d.rounded_rectangle([x0, y, x0 + bw, y + bh], radius=34 * S2, fill=tuple(int(c) for c in rgb(bg[:7])) + (255,))
        for n, l in enumerate(lines):
            d.text((x0 + 20 * S2, y + 10 * S2 + n * lh), l, font=ft, fill=tuple(int(c) for c in rgb(tc[:7])) + (255,))
        d.text((x0 + bw - 20 * S2 - timew, y + bh - 34 * S2), item[2], font=ftime, fill=tuple(int(c) for c in rgb(tm[:7])) + (255,))
        y += bh + 14 * S2
    im = Image.alpha_composite(im, ov)
    return im.resize((W, H), Image.LANCZOS).convert('RGB')


def preview(images):
    os.makedirs(DOCS, exist_ok=True)
    cw, ch, gap, top = int(W * .45), int(H * .45), 16, 70
    sheet = Image.new('RGB', (len(images) * (cw + gap) + gap, top + 2 * ch + gap + 70), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    fl, fs = font(30, True), font(17)
    for n, (key, name, img) in enumerate(images):
        cols = theme_colors(key)
        dark = is_dark(key)
        c = chat(img, cols, dark)
        c.save(os.path.join(DOCS, '%s_chat.png' % key))
        x = gap + n * (cw + gap)
        sheet.paste(img.resize((cw, ch), Image.LANCZOS), (x, top))
        sheet.paste(c.resize((cw, ch), Image.LANCZOS), (x, top + ch + gap))
        d.text((x, 18), '%s  (%s)' % (name, 'dark' if dark else 'light'), font=fl, fill=(235, 235, 240))
        s = stats(img, cols)
        d.text((x, top + 2 * ch + gap + 10), 'L* mid %.0f (%.0f..%.0f)' % (s['L'], s['L5'], s['L95']), font=fs, fill=(200, 200, 210))
        d.text((x, top + 2 * ch + gap + 34), 'contrast in %.2f  out %.2f   dE %.0f / %.0f' % (s['cin'], s['cout'], s['din'], s['dout']), font=fs, fill=(200, 200, 210))
        print('%-9s dE in %4.1f out %4.1f  L*=%.0f [%.0f..%.0f]  in %.2f  out %.2f  same-as-in %.0f%%  same-as-out %.0f%%' % (
            key, s['din'], s['dout'], s['L'], s['L5'], s['L95'], s['cin'], s['cout'], s['win'] * 100, s['wout'] * 100))
    sheet.save(os.path.join(DOCS, 'preview.png'))
    print('preview', os.path.join(DOCS, 'preview.png'))


def main():
    only = sys.argv[2:] if len(sys.argv) > 2 else None
    write = not (len(sys.argv) > 1 and sys.argv[1] == 'preview')
    images = []
    total = 0
    if write:
        os.makedirs(OUT, exist_ok=True)
    for key, name, fn in WALLS:
        if only and key not in only:
            continue
        img = fn()
        images.append((key, name, img))
        if write:
            path = os.path.join(OUT, key + '.jpg')
            if key == 'pixel':  # flat 8 px cells line up with JPEG blocks; full chroma keeps the edges crisp
                img.save(path, 'JPEG', quality=90, subsampling=0, optimize=True)
            else:
                img.save(path, 'JPEG', quality=90, subsampling=2, optimize=True)
            sz = os.path.getsize(path)
            total += sz
            print('%-9s %4d KB' % (key, sz // 1024))
    if write:
        print('total %d KB' % (total // 1024))
    preview(images)


if __name__ == '__main__':
    main()
