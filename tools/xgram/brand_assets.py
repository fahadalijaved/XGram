"""XGram brand assets: profile photos for the XGram group / community and the CI channel, and an emoji set (topic
icons such as General, Updates, Support) with sticker versions, all in the style of the app icons.

Run: python brand_assets.py           (writes docs/xgram/brand/)
     python brand_assets.py sheet.png (also a preview sheet)

Emoji packs need 100x100 images (static, PNG or WEBP), stickers 512 px on one side; both are created with
@Stickers in Telegram (/newemojipack, /newpack).
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'docs', 'xgram', 'brand'))
S = 8  # supersampling


def rgba(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def gradient(size, c0, c1, angle=45):
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    a = math.radians(angle)
    t = (xx * math.cos(a) + yy * math.sin(a))
    t = (t - t.min()) / (t.max() - t.min())
    c0, c1 = np.array(rgba(c0), float), np.array(rgba(c1), float)
    return Image.fromarray((c0 * (1 - t[..., None]) + c1 * t[..., None]).astype(np.uint8))


class Canvas:
    """Draws in a 100-unit space on a supersampled mask."""

    def __init__(self, px):
        self.px = px
        self.k = px * S / 100.0
        self.mask = Image.new('L', (px * S, px * S), 0)
        self.cut = Image.new('L', (px * S, px * S), 0)  # holes, in the background color
        self.d = ImageDraw.Draw(self.mask)
        self.dc = ImageDraw.Draw(self.cut)

    def p(self, x, y):
        return (x * self.k, y * self.k)

    def line(self, pts, w, cut=False):
        d = self.dc if cut else self.d
        pp = [self.p(*q) for q in pts]
        d.line(pp, fill=255, width=int(w * self.k), joint='curve')
        r = w * self.k / 2
        for (x, y) in (pp[0], pp[-1]):
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)

    def poly(self, pts, cut=False):
        (self.dc if cut else self.d).polygon([self.p(*q) for q in pts], fill=255)

    def circle(self, cx, cy, r, cut=False):
        (self.dc if cut else self.d).ellipse([*self.p(cx - r, cy - r), *self.p(cx + r, cy + r)], fill=255)

    def ring(self, cx, cy, r, w, cut=False):
        (self.dc if cut else self.d).ellipse([*self.p(cx - r, cy - r), *self.p(cx + r, cy + r)], outline=255, width=int(w * self.k))

    def rect(self, x0, y0, x1, y1, radius=0, cut=False):
        (self.dc if cut else self.d).rounded_rectangle([*self.p(x0, y0), *self.p(x1, y1)], radius=radius * self.k, fill=255)

    def arc(self, cx, cy, r, a0, a1, w, cut=False):
        (self.dc if cut else self.d).arc([*self.p(cx - r, cy - r), *self.p(cx + r, cy + r)], a0, a1, fill=255, width=int(w * self.k))

    def result(self):
        m = np.array(self.mask, float) * (1 - np.array(self.cut, float) / 255)
        return Image.fromarray(m.astype(np.uint8))


# ---------------------------------------------------------------- glyphs (white, on a 100 unit tile)

def g_xgram(c):
    # The woven X of the app icons, 108-unit geometry mapped onto 100.
    k = 100 / 108.0 * 1.15
    t = lambda x, y: (50 + (x - 54) * k, 50 + (y - 54) * k)
    W = 11 * k
    c.line([t(36.5, 36.5), t(71.5, 71.5)], W)
    x0, y0, tip = (36.5, 71.5), (36.5, 71.5), (76.5, 31.5)
    L = math.hypot(tip[0] - x0[0], tip[1] - x0[1])
    ux, uy = (tip[0] - x0[0]) / L, (tip[1] - x0[1]) / L
    vx, vy = -uy, ux
    mx, my = x0[0] + ux * 26, x0[1] + uy * 26
    h = 5.5
    c.poly([t(x0[0] + vx * h, x0[1] + vy * h), t(mx + vx * h, my + vy * h), t(*tip), t(mx - vx * h, my - vy * h), t(x0[0] - vx * h, x0[1] - vy * h)])
    c.circle(*t(*x0), h * k)


def g_hash(c):
    for x in (42, 58):
        c.line([(x + 3, 26), (x - 3, 74)], 7)
    for y in (41, 59):
        c.line([(28, y), (72, y)], 7)


def g_megaphone(c):
    c.poly([(30, 42), (60, 28), (60, 72), (30, 58)])
    c.rect(24, 41, 34, 59, 3)
    c.poly([(36, 57), (44, 60), (40, 74), (33, 72)])
    c.arc(60, 50, 12, -45, 45, 5)
    c.arc(60, 50, 20, -40, 40, 5)


def g_lifebuoy(c):
    c.ring(50, 50, 23, 12)
    for a in (45, 135, 225, 315):
        r0, r1 = 11.5, 23.5
        x0, y0 = 50 + r0 * math.cos(math.radians(a)), 50 + r0 * math.sin(math.radians(a))
        x1, y1 = 50 + r1 * math.cos(math.radians(a)), 50 + r1 * math.sin(math.radians(a))
        c.line([(x0, y0), (x1, y1)], 6, cut=True)


def g_download(c):
    c.line([(50, 24), (50, 56)], 8)
    c.poly([(36, 48), (64, 48), (50, 64)])
    c.line([(28, 64), (28, 74), (72, 74), (72, 64)], 7)


def g_gear(c, hole=8):
    for i in range(8):
        a = math.radians(i * 45)
        cx, cy = 50 + 20 * math.cos(a), 50 + 20 * math.sin(a)
        ux, uy = math.cos(a), math.sin(a)
        vx, vy = -uy, ux
        w, hh = 5.5, 6
        c.poly([(cx + vx * w - ux * hh, cy + vy * w - uy * hh), (cx + vx * w * .8 + ux * hh, cy + vy * w * .8 + uy * hh),
                (cx - vx * w * .8 + ux * hh, cy - vy * w * .8 + uy * hh), (cx - vx * w - ux * hh, cy - vy * w - uy * hh)])
    c.circle(50, 50, 19)
    c.circle(50, 50, hole, cut=True)


def g_bug(c):
    c.circle(50, 56, 15)
    c.rect(35, 46, 65, 70, 15)
    c.circle(50, 37, 9)
    for y, dy in ((50, -4), (58, 0), (66, 4)):
        c.line([(36, y), (25, y + dy)], 4.5)
        c.line([(64, y), (75, y + dy)], 4.5)
    c.line([(46, 30), (40, 22)], 4)
    c.line([(54, 30), (60, 22)], 4)
    c.line([(50, 47), (50, 70)], 3, cut=True)


def g_bulb(c):
    c.circle(50, 43, 17)
    c.poly([(40, 52), (60, 52), (57, 64), (43, 64)])
    c.rect(42, 64, 58, 69, 2)
    c.rect(44, 71, 56, 76, 2.5)
    c.arc(50, 43, 9, 200, 280, 3.5, cut=True)


def g_palette(c):
    c.circle(50, 50, 25)
    c.circle(62, 64, 7, cut=True)
    for (x, y) in ((38, 40), (50, 33), (62, 40), (37, 55)):
        c.circle(x, y, 4.6, cut=True)


def g_grid(c):
    for x in (27, 52):
        for y in (27, 52):
            c.rect(x, y, x + 21, y + 21, 6)


def g_bell(c):
    c.poly([(30, 64), (70, 64), (64, 54), (64, 40), (36, 40), (36, 54)])
    c.circle(50, 41, 14)
    c.rect(26, 62, 74, 68, 3)
    c.circle(50, 72, 5.5)
    c.circle(50, 26, 3.5)


def g_flask(c):
    c.rect(43, 22, 57, 27, 2)
    c.poly([(45, 26), (55, 26), (55, 44), (72, 72), (28, 72), (45, 44)])
    c.poly([(36, 60), (64, 60), (66, 66), (34, 66)], cut=True)
    c.circle(46, 52, 2.5, cut=True)
    c.circle(54, 47, 1.8, cut=True)


def g_chat(c):
    c.rect(24, 28, 76, 64, 14)
    c.poly([(34, 60), (46, 62), (30, 76)])
    for x in (37, 50, 63):
        c.circle(x, 46, 4.5, cut=True)


def g_code(c):
    c.line([(38, 34), (24, 50), (38, 66)], 7)
    c.line([(62, 34), (76, 50), (62, 66)], 7)
    c.line([(55, 28), (45, 72)], 6)


def g_coffee(c):
    c.rect(28, 44, 62, 74, 8)
    c.rect(28, 44, 62, 52, 0)
    c.ring(64, 56, 8, 5)
    for x in (37, 46, 55):
        c.line([(x, 38), (x + 2, 32), (x, 26)], 3.5)


def g_shield(c):
    c.poly([(50, 22), (74, 30), (72, 52), (50, 78), (28, 52), (26, 30)])
    c.line([(38, 50), (47, 59), (63, 41)], 6, cut=True)


def g_ghost(c):
    c.circle(50, 42, 20)
    c.rect(30, 42, 70, 70, 0)
    for i, x in enumerate((30, 43.3, 56.6)):
        c.poly([(x, 70), (x + 13.3, 70), (x + 6.6, 78)])
    c.circle(42, 42, 4.2, cut=True)
    c.circle(58, 42, 4.2, cut=True)


def g_bot(c):
    # robot head: ears, antenna, eyes and smile cut out
    c.rect(26, 36, 74, 74, 14)
    c.rect(19, 48, 27, 64, 3.5)
    c.rect(73, 48, 81, 64, 3.5)
    c.line([(50, 36), (50, 25)], 5)
    c.circle(50, 22, 5.5)
    c.circle(40, 52, 6, cut=True)
    c.circle(60, 52, 6, cut=True)
    c.arc(50, 58, 9, 30, 150, 4.5, cut=True)


def g_star(c):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 26 if i % 2 == 0 else 11
        pts.append((50 + r * math.cos(a), 52 + r * math.sin(a)))
    c.poly(pts)


EMOJI = [
    # key, emoji to attach in @Stickers, gradient, glyph
    ('xgram', '✖️', ('#2AABEE', '#6C5CE7'), g_xgram),
    ('general', '#️⃣', ('#78909C', '#455A64'), g_hash),
    ('updates', '\U0001F4E3', ('#FF9500', '#FF2D55'), g_megaphone),
    ('support', '\U0001F6DF', ('#00C9A7', '#0091EA'), g_lifebuoy),
    ('releases', '\U0001F4E6', ('#11998E', '#38EF7D'), g_download),
    ('ci', '⚙️', ('#1F2C56', '#0B1026'), g_gear),
    ('bugs', '\U0001F41E', ('#F857A6', '#FF5858'), g_bug),
    ('ideas', '\U0001F4A1', ('#FFB347', '#FF8C00'), g_bulb),
    ('themes', '\U0001F3A8', ('#8E2DE2', '#4A00E0'), g_palette),
    ('icons', '\U0001F4F1', ('#5B86E5', '#9D50BB'), g_grid),
    ('announcements', '\U0001F514', ('#F7971E', '#FFB300'), g_bell),
    ('beta', '\U0001F9EA', ('#7F00FF', '#E100FF'), g_flask),
    ('chat', '\U0001F4AC', ('#36B4F0', '#1E88E5'), g_chat),
    ('dev', '\U0001F4BB', ('#3A3F44', '#1C1F22'), g_code),
    ('offtopic', '☕', ('#A1887F', '#5D4037'), g_coffee),
    ('rules', '\U0001F6E1️', ('#1D976C', '#2BC48A'), g_shield),
    ('ghost', '\U0001F47B', ('#4B4B5A', '#15151C'), g_ghost),
    ('featured', '⭐', ('#FFC107', '#FF6F00'), g_star),
    ('bot', '\U0001F916', ('#2AABEE', '#6C5CE7'), g_bot),
]


def tile(px, colors, glyph, shape='squircle', glyph_scale=1.0, accent=None):
    """A gradient tile (squircle, or a full square for profile photos) with the white glyph and a soft shadow."""
    big = px * S
    bg = gradient((big, big), *colors)
    c = Canvas(px)
    glyph(c)
    m = c.result()
    if glyph_scale != 1.0:
        g = m.resize((int(big * glyph_scale), int(big * glyph_scale)), Image.LANCZOS)
        m = Image.new('L', (big, big), 0)
        m.paste(g, ((big - g.width) // 2, (big - g.height) // 2))
    shadow = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    shadow.putalpha(m.filter(ImageFilter.GaussianBlur(big * .012)).point(lambda v: int(v * .28)))
    img = bg.copy()
    img.alpha_composite(shadow, (0, int(big * .012)))
    white = Image.new('RGBA', (big, big), rgba(accent or '#FFFFFF'))
    white.putalpha(m)
    img = Image.alpha_composite(img, white)
    if shape == 'squircle':
        mask = Image.new('L', (big, big), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * .3), fill=255)
        out = Image.new('RGBA', (big, big), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        img = out
    return img.resize((px, px), Image.LANCZOS)


def avatar_ci(px):
    """XGram CI: the X inside a gear on the Midnight colors, a green check for passing builds."""
    big = px * S
    img = gradient((big, big), '#1F2C56', '#0B1026')
    c = Canvas(px)
    g_gear(c, hole=14.5)  # room for the X inside
    gear = c.result().resize((int(big * .92), int(big * .92)), Image.LANCZOS)
    m = Image.new('L', (big, big), 0)
    m.paste(gear, ((big - gear.width) // 2, (big - gear.height) // 2))
    cyan = Image.new('RGBA', (big, big), rgba('#00E5FF'))
    cyan.putalpha(m.point(lambda v: int(v * .9)))
    glow = Image.new('RGBA', (big, big), rgba('#00E5FF'))
    glow.putalpha(m.filter(ImageFilter.GaussianBlur(big * .03)).point(lambda v: int(v * .5)))
    img = Image.alpha_composite(Image.alpha_composite(img, glow), cyan)
    c2 = Canvas(px)
    g_xgram(c2)
    xm = c2.result().resize((int(big * .36), int(big * .36)), Image.LANCZOS)
    m2 = Image.new('L', (big, big), 0)
    m2.paste(xm, ((big - xm.width) // 2, (big - xm.height) // 2))
    white = Image.new('RGBA', (big, big), rgba('#FFFFFF'))
    white.putalpha(m2)
    img = Image.alpha_composite(img, white)
    # check badge, inside the circle Telegram crops to
    d = ImageDraw.Draw(img)
    cx, cy, r = big * .7, big * .7, big * .11
    d.ellipse([cx - r - big * .012, cy - r - big * .012, cx + r + big * .012, cy + r + big * .012], fill=rgba('#0B1026'))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba('#2BD46B'))
    d.line([(cx - r * .45, cy + r * .02), (cx - r * .1, cy + r * .38), (cx + r * .5, cy - r * .35)], fill=(255, 255, 255, 255), width=int(r * .28), joint='curve')
    return img.resize((px, px), Image.LANCZOS)


def avatar_community(px):
    """XGram Community: the app icon as a white tile, with two more tiles stacked behind it (its chats)."""
    big = px * S
    img = gradient((big, big), '#6C5CE7', '#2AABEE', angle=60)
    size = big * .46
    top = (big - size) / 2
    left = (big - size) / 2 + big * .06
    for i, alpha in ((2, 85), (1, 150)):
        shift, inset = big * .07 * i, big * .035 * i
        h = size - 2 * inset
        tile = Image.new('RGBA', (big, big), (0, 0, 0, 0))
        ImageDraw.Draw(tile).rounded_rectangle([left - shift, top + inset, left - shift + h, top + inset + h], radius=h * .3, fill=(255, 255, 255, alpha))
        img = Image.alpha_composite(img, tile)
    shadow = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([left, top + big * .015, left + size, top + size + big * .015], radius=size * .3, fill=(0, 0, 0, 70))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(big * .02)))
    ImageDraw.Draw(img).rounded_rectangle([left, top, left + size, top + size], radius=size * .3, fill=(255, 255, 255, 255))
    c = Canvas(px)
    g_xgram(c)
    xm = c.result().resize((int(size * .98), int(size * .98)), Image.LANCZOS)
    m = Image.new('L', (big, big), 0)
    m.paste(xm, (int(left + (size - xm.width) / 2), int(top + (size - xm.height) / 2)))
    fill = gradient((big, big), '#2AABEE', '#6C5CE7')
    fill.putalpha(m)
    img = Image.alpha_composite(img, fill)
    return img.resize((px, px), Image.LANCZOS)


def avatar_bot(px):
    """XGram Build Bot: a robot head whose visor shows the X in the CI channel's cyan, with a download badge."""
    big = px * S
    img = gradient((big, big), '#2AABEE', '#6C5CE7')
    u = big / 100.0

    def layer(draw_fn, color, blur=0, alpha=1.0):
        m = Image.new('L', (big, big), 0)
        draw_fn(ImageDraw.Draw(m))
        if blur:
            m = m.filter(ImageFilter.GaussianBlur(blur))
        out = Image.new('RGBA', (big, big), rgba(color))
        out.putalpha(m.point(lambda v: int(v * alpha)))
        return out

    head = lambda d: (d.rounded_rectangle([24 * u, 34 * u, 76 * u, 78 * u], radius=15 * u, fill=255),
                      d.rounded_rectangle([17 * u, 47 * u, 26 * u, 65 * u], radius=4 * u, fill=255),
                      d.rounded_rectangle([74 * u, 47 * u, 83 * u, 65 * u], radius=4 * u, fill=255),
                      d.line([(50 * u, 34 * u), (50 * u, 23 * u)], fill=255, width=int(5 * u)))
    img = Image.alpha_composite(img, layer(head, '#0B1026', blur=big * .02, alpha=.35).transform(
        (big, big), Image.AFFINE, (1, 0, 0, 0, 1, -big * .015)))  # soft shadow below
    img = Image.alpha_composite(img, layer(head, '#FFFFFF'))
    # antenna light with a glow
    tip = lambda d: d.ellipse([44.5 * u, 14.5 * u, 55.5 * u, 25.5 * u], fill=255)
    img = Image.alpha_composite(img, layer(tip, '#00E5FF', blur=big * .025, alpha=.8))
    img = Image.alpha_composite(img, layer(tip, '#00E5FF'))
    # visor with the X
    visor = lambda d: d.rounded_rectangle([31 * u, 42 * u, 69 * u, 70 * u], radius=10 * u, fill=255)
    img = Image.alpha_composite(img, layer(visor, '#1F2C56'))
    c = Canvas(px)
    g_xgram(c)
    xm = c.result().resize((int(big * .36), int(big * .36)), Image.LANCZOS)
    m = Image.new('L', (big, big), 0)
    m.paste(xm, ((big - xm.width) // 2, int(56 * u - xm.height / 2)))
    glow = Image.new('RGBA', (big, big), rgba('#00E5FF'))
    glow.putalpha(m.filter(ImageFilter.GaussianBlur(big * .02)).point(lambda v: int(v * .6)))
    cyan = Image.new('RGBA', (big, big), rgba('#00E5FF'))
    cyan.putalpha(m)
    img = Image.alpha_composite(Image.alpha_composite(img, glow), cyan)
    # download badge, inside the circle Telegram crops to (same place as the CI channel's check)
    d = ImageDraw.Draw(img)
    cx, cy, r = big * .71, big * .71, big * .11
    d.ellipse([cx - r - big * .012, cy - r - big * .012, cx + r + big * .012, cy + r + big * .012], fill=rgba('#5A6BE6'))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba('#2BD46B'))
    w = int(r * .26)
    d.line([(cx, cy - r * .5), (cx, cy + r * .3)], fill=(255, 255, 255, 255), width=w)
    d.line([(cx - r * .38, cy - r * .02), (cx, cy + r * .36), (cx + r * .38, cy - r * .02)], fill=(255, 255, 255, 255), width=w, joint='curve')
    return img.resize((px, px), Image.LANCZOS)


def main():
    os.makedirs(os.path.join(OUT, 'avatars'), exist_ok=True)
    os.makedirs(os.path.join(OUT, 'emoji'), exist_ok=True)
    os.makedirs(os.path.join(OUT, 'stickers'), exist_ok=True)
    tile(640, ('#2AABEE', '#6C5CE7'), g_xgram, shape='square', glyph_scale=.82).convert('RGB').save(os.path.join(OUT, 'avatars', 'xgram_group.png'))
    avatar_ci(640).convert('RGB').save(os.path.join(OUT, 'avatars', 'xgram_ci.png'))
    avatar_community(640).convert('RGB').save(os.path.join(OUT, 'avatars', 'xgram_community.png'))
    avatar_bot(640).convert('RGB').save(os.path.join(OUT, 'avatars', 'xgram_bot.png'))
    tile(640, ('#FF9500', '#FF2D55'), g_megaphone, shape='square', glyph_scale=.8).convert('RGB').save(os.path.join(OUT, 'avatars', 'xgram_updates.png'))
    lines = ['# XGram emoji and stickers', '', 'Made by `tools/xgram/brand_assets.py`. In Telegram, @Stickers:', '',
             '- Emoji pack (topic icons): `/newemojipack` → Static emoji → send each file from `emoji/` (100×100) with its emoji.',
             '- Sticker pack: `/newpack` → send each file from `stickers/` (512×512) with its emoji.',
             '- Topic icons can use any custom emoji with Telegram Premium.', '', '| File | Emoji |', '|---|---|']
    for key, emoji, colors, glyph in EMOJI:
        tile(100, colors, glyph).save(os.path.join(OUT, 'emoji', key + '.png'))
        tile(512, colors, glyph).save(os.path.join(OUT, 'stickers', key + '.png'))
        lines.append('| %s.png | %s |' % (key, emoji))
    with open(os.path.join(OUT, 'README.md'), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('\n'.join(lines) + '\n')
    print('wrote', OUT)
    if len(sys.argv) > 1:
        sheet = Image.new('RGBA', (max(10 * 120 + 20, 5 * 260 + 20), 2 * 130 + 280), (30, 30, 34, 255))
        for i, (key, emoji, colors, glyph) in enumerate(EMOJI):
            t = tile(100, colors, glyph)
            sheet.paste(t, (20 + (i % 10) * 120, 20 + (i // 10) * 130), t)
            ImageDraw.Draw(sheet).text((20 + (i % 10) * 120, 124 + (i // 10) * 130), key, fill=(220, 220, 220, 255))
        for j, name in enumerate(('xgram_community', 'xgram_group', 'xgram_ci', 'xgram_updates', 'xgram_bot')):
            a = Image.open(os.path.join(OUT, 'avatars', name + '.png')).resize((220, 220), Image.LANCZOS)
            mask = Image.new('L', (220, 220), 0)
            ImageDraw.Draw(mask).ellipse([0, 0, 219, 219], fill=255)
            sheet.paste(a, (20 + j * 260, 290), mask)
        sheet.save(sys.argv[1])
        print('sheet', sys.argv[1])


if __name__ == '__main__':
    main()
