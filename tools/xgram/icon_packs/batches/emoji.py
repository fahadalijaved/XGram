# XGram Line batch "emoji": emoji keyboard sections, chat folder icons, inline emoji of chat previews and a few tools.
#
# Left out on purpose: giveaway_particle (giveaway_particle_1/_2). They are not icons but confetti pieces that
# GiftParticlesDrawable scatters (10 x 9.5 dp and 4 x 12.5 dp, not square); a square 24-grid drawable would squash them.
# Notes: all six dice faces (dice_1.._6) share one 'dice' icon, a tilted die; 'clock' is the small sending-status clock
# drawn at the size of the check marks it sits with; the outline/filled pairs of the emoji sections are the same line
# shape (the selected one is tinted), except the lamp, whose 'filled' twin is a lit bulb.
import math
import re

from dsl import *
from dsl import core

_TOK = re.compile(r'[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?')
_ARGS = dict(M=2, L=2, H=1, V=1, C=6, S=4, Q=4, T=2, A=7, Z=0)


def tf(d, s=1.0, dx=0.0, dy=0.0, rot=0.0, cx=12.0, cy=12.0):
    """Path data scaled by s and rotated by rot degrees around (cx, cy), then moved by (dx, dy); output is absolute."""
    ca, sa = math.cos(math.radians(rot)), math.sin(math.radians(rot))

    def T(x, y):
        x, y = x - cx, y - cy
        return cx + s * (x * ca - y * sa) + dx, cy + s * (x * sa + y * ca) + dy

    toks = _TOK.findall(d)
    out, i, cmd = [], 0, None
    x = y = sx = sy = 0.0
    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
            if cmd in 'Zz':
                out.append('Z')
                x, y = sx, sy
                continue
        c, rel = cmd.upper(), cmd.islower()
        v = [float(t) for t in toks[i:i + _ARGS[c]]]
        i += _ARGS[c]
        if c == 'H':
            v, c = ([v[0], 0.0] if rel else [v[0], y]), 'L'
        elif c == 'V':
            v, c = ([0.0, v[0]] if rel else [x, v[0]]), 'L'
        if c == 'A':
            ex, ey = (v[5] + x, v[6] + y) if rel else (v[5], v[6])
            px, py = T(ex, ey)
            out.append('A%s %s %s %d %d %s %s' % (f(v[0] * s), f(v[1] * s), f(v[2] + rot), v[3], v[4], f(px), f(py)))
            x, y = ex, ey
            continue
        pts = []
        for k in range(0, len(v), 2):
            px, py = (v[k] + x, v[k + 1] + y) if rel else (v[k], v[k + 1])
            pts.append((px, py))
        out.append(c + ' '.join('%s %s' % tuple(f(q) for q in T(*p)) for p in pts))
        x, y = pts[-1]
        if c == 'M':
            sx, sy = x, y
            cmd = 'l' if rel else 'L'
    return ''.join(out)


def tfe(elements, s=1.0, dx=0.0, dy=0.0, rot=0.0, cx=12.0, cy=12.0):
    """A whole element list through tf (circles and dots stay circles)."""
    ca, sa = math.cos(math.radians(rot)), math.sin(math.radians(rot))

    def T(x, y):
        x, y = x - cx, y - cy
        return cx + s * (x * ca - y * sa) + dx, cy + s * (x * sa + y * ca) + dy
    out = []
    for kind, v in elements:
        if kind == 'c':
            out.append(C(*T(v[0], v[1]), v[2] * s))
        elif kind == 'dot':
            out.append(DOT(*T(v[0], v[1]), v[2] * s))
        elif kind == 'rr':
            out.append(P(tf(rr_d(*v), s, dx, dy, rot, cx, cy)))
        else:
            out.append((kind, tf(v, s, dx, dy, rot, cx, cy)))
    return out


def _meet(c1, r1, c2, r2):
    """Intersections of two circles."""
    dx, dy = c2[0] - c1[0], c2[1] - c1[1]
    d = math.hypot(dx, dy)
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(0.0, r1 * r1 - a * a))
    mx, my = c1[0] + a * dx / d, c1[1] + a * dy / d
    return (mx + h * dy / d, my - h * dx / d), (mx - h * dy / d, my + h * dx / d)


def _arc(c, p0, p1):
    """clockwise arc of circle c from p0 to p1"""
    x, y, r = c
    a0 = math.atan2(p0[1] - y, p0[0] - x)
    a1 = math.atan2(p1[1] - y, p1[0] - x)
    span = (a1 - a0) % (2 * math.pi)
    return 'A%s %s 0 %d 1 %s %s' % (f(r), f(r), 1 if span > math.pi else 0, f(p1[0]), f(p1[1]))


def _cloud(left=(7, 14.5, 4.5), main=(12.5, 10.5, 5.5), right=(17.5, 15, 4)):
    """the cloud of the media2 batch: three circles standing on one flat bottom line"""
    a = min(_meet(left[:2], left[2], main[:2], main[2]), key=lambda p: p[1])
    b = min(_meet(main[:2], main[2], right[:2], right[2]), key=lambda p: p[1])
    p0 = (left[0], left[1] + left[2])
    p3 = (right[0], right[1] + right[2])
    return 'M%s %s%s%s%sZ' % (f(p0[0]), f(p0[1]), _arc(left, p0, a), _arc(main, a, b), _arc(right, b, p3))


def _bear(eyes):
    """the bear of the chat batch's animals_filled_blink (keep the two in step: they swap while the bear blinks)"""
    hc, hr = (12.0, 13.2), 8.0
    ec, er = (5.6, 6.2), 3.0
    a1, a2 = _meet(hc, hr, ec, er)
    lo, hi = (a1, a2) if a1[1] > a2[1] else (a2, a1)
    rlo, rhi = (24 - lo[0], lo[1]), (24 - hi[0], hi[1])
    d = 'M%s %sA%s %s 0 1 1 %s %sA%s %s 0 0 1 %s %sA%s %s 0 1 1 %s %sA%s %s 0 1 1 %s %sZ' % (
        f(lo[0]), f(lo[1]), f(er), f(er), f(hi[0]), f(hi[1]), f(hr), f(hr), f(rhi[0]), f(rhi[1]),
        f(er), f(er), f(rlo[0]), f(rlo[1]), f(hr), f(hr), f(lo[0]), f(lo[1]))
    return [P(d)] + eyes + [P('M10.6 16.6h2.8L12 18Z')]


def _poly(pts, close=True):
    d = 'M%s %s' % (f(pts[0][0]), f(pts[0][1])) + ''.join('L%s %s' % (f(x), f(y)) for x, y in pts[1:])
    return d + ('Z' if close else '')


def _wave(x0, x1, y, amp, halves):
    """a smooth wave from (x0, y) to (x1, y), starting downwards"""
    w = (x1 - x0) / halves
    d = 'M%s %s' % (f(x0), f(y))
    for k in range(halves):
        a = amp if k % 2 == 0 else -amp
        d += 'c%s %s %s %s %s 0' % (f(w * 0.36), f(a * 1.33), f(w * 0.64), f(a * 1.33), f(w))
    return d


def _flower(cx, cy, n, d, r):
    """n round petals (circles of radius r at distance d) as one closed outline"""
    cs = [(cx + d * math.cos(-math.pi / 2 + 2 * math.pi * k / n), cy + d * math.sin(-math.pi / 2 + 2 * math.pi * k / n)) for k in range(n)]
    notches = []
    for k in range(n):
        p, q = cs[k], cs[(k + 1) % n]
        notches.append(max(_meet(p, r, q, r), key=lambda t: math.hypot(t[0] - cx, t[1] - cy)))
    out = 'M%s %s' % (f(notches[-1][0]), f(notches[-1][1]))
    for k in range(n):
        out += _arc((cs[k][0], cs[k][1], r), notches[k - 1], notches[k])
    return out + 'Z'


# shapes shared with other batches (media1: gift and flash, media2: image_replace)
GIFT = [RR(3, 7.5, 18, 4.5, 1.5), P('M4.8 12h14.4v6.5a2 2 0 0 1-2 2H6.8a2 2 0 0 1-2-2V12Z'), L(12, 7.5, 12, 20.5),
        P('M12 7.5C11.2 5 9.8 3.5 8.2 3.5a2 2 0 0 0 0 4'), P('M12 7.5c.8-2.5 2.2-4 3.8-4a2 2 0 0 1 0 4')]
BOLT = PL((14.4, 2.5), (4.8, 14), (10.4, 14), (9.6, 21.5), (19.2, 10), (13.6, 10), close=True)
SWAP = [P('M5.5 14a3 3 0 0 0 3 3H10M8.5 15.5l1.5 1.5-1.5 1.5'), P('M18.5 10a3 3 0 0 0-3-3H14M15.5 5.5L14 7l1.5 1.5')]
PICTURE_BR = [RR(13, 13, 8.5, 8.5, 2), P('M21.5 18.5l-2.1-2.1a.8.8 0 0 0-1.1 0L14 20.7')]


def _doc(x, y):
    """a small document (7 x 8.5) with a folded corner, top left at (x, y)"""
    return [P('M%s %sH%sa1.8 1.8 0 0 0-1.8 1.8v4.9a1.8 1.8 0 0 0 1.8 1.8h3.4a1.8 1.8 0 0 0 1.8-1.8V%sZ' % (
        f(x + 4.5), f(y), f(x + 1.8), f(y + 2.5))), P('M%s %sv1.6a.9.9 0 0 0 .9.9H%s' % (f(x + 4.5), f(y), f(x + 7)))]


def _bulb(cy, r, neck_y, neck_w, base, theta=40.0, k=1.2):
    """a light bulb: round glass (centre (12, cy), radius r) narrowing to a neck at neck_y, then the screw base lines
    (y, half width) below"""
    t = math.radians(theta)
    ex, ey = r * math.cos(t), cy + r * math.sin(t)  # where the glass leaves the circle
    tx, ty = math.sin(t), math.cos(t)  # the circle's tangent there, heading down and in
    d = 'M%s %sC%s %s %s %s %s %sA%s %s 0 1 1 %s %sC%s %s %s %s %s %sZ' % (
        f(12 - neck_w), f(neck_y), f(12 - neck_w), f(neck_y - k), f(12 - ex + k * tx), f(ey + k * ty), f(12 - ex), f(ey),
        f(r), f(r), f(12 + ex), f(ey), f(12 + ex - k * tx), f(ey + k * ty), f(12 + neck_w), f(neck_y - k), f(12 + neck_w), f(neck_y))
    return [P(d)] + [L(12 - w, y, 12 + w, y) for y, w in base]


def _rays(cx, cy, r0, r1, angles):
    out = []
    for a in angles:
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        out.append(L(cx + r0 * ca, cy + r0 * sa, cx + r1 * ca, cy + r1 * sa))
    return out


# a game controller with two grips
PAD = P('M7.8 6h8.4a4 4 0 0 1 3.9 3.1l1.3 6.4a2.6 2.6 0 0 1-4.5 2.2L15 15.5H9l-1.9 2.2a2.6 2.6 0 0 1-4.5-2.2l1.3-6.4A4 4 0 0 1 7.8 6Z')

BEAR = _bear([DOT(9.2, 12.8, 1.35), DOT(14.8, 12.8, 1.35)])
FLAG = [P('M5 4.8c2.3-1.3 4.7-1.3 7 0s4.7 1.3 7 0v9c-2.3 1.3-4.7 1.3-7 0s-4.7-1.3-7 0Z'), L(5, 3.5, 5, 21)]


def _soccer(pr=4.2):
    """the ball with its centre patch (a hole in the Duotone body, like a white patch) and the seams to the rim"""
    pent = [(12 + pr * math.cos(-math.pi / 2 + 2 * math.pi * k / 5), 12 + pr * math.sin(-math.pi / 2 + 2 * math.pi * k / 5)) for k in range(5)]
    out = [C(12, 12, 9), P(_poly(pent))]
    for x, y in pent:
        ux, uy = (x - 12) / pr, (y - 12) / pr
        out.append(L(x + ux * 1.6, y + uy * 1.6, 12 + ux * 8.2, 12 + uy * 8.2))
    return out


ICONS = {
    'broom': tfe([L(12, 1.8, 12, 8.6), P('M9.8 9.4h4.4a1 1 0 0 1 1 .8L17 19H7l1.8-8.8a1 1 0 0 1 1-.8Z'), L(10.4, 15, 10.1, 19), L(13.6, 15, 13.9, 19)],
                 rot=45, dx=0.6, dy=-0.6),
    'brush': tfe([L(12, 1.6, 12, 8.3), RR(9.6, 8.3, 4.8, 3.7, 1.1),
                  P('M9.4 12h5.2c.7 1.5.9 3 .5 4.6-.5 1.9-1.7 3.5-3.1 5.1-1.4-1.6-2.6-3.2-3.1-5.1-.4-1.6-.2-3.1.5-4.6Z')], rot=45, dx=-0.2, dy=0.2),
    'dice': tfe([RR(4.5, 4.5, 15, 15, 3), DOT(8.6, 8.6, 1.35), DOT(12, 12, 1.35), DOT(15.4, 15.4, 1.35)], rot=-14),
    'restaurant_menu': tfe([P('M12 2.2a2.8 3.8 0 1 1 0 7.6a2.8 3.8 0 1 1 0-7.6Z'), L(12, 9.8, 12, 21.5)], rot=45, dx=0.3, dy=0.6) +
                       tfe([P('M9.5 2.5v4a2.5 2.5 0 0 0 5 0v-4'), L(12, 2.5, 12, 21.5)], rot=-45, dx=0.3, dy=0.6),
    'cake_variant': [P('M4 13.5A2.5 2.5 0 0 1 6.5 11h11a2.5 2.5 0 0 1 2.5 2.5v7H4v-7Z'), P(_wave(4, 20, 15.3, 0.9, 6)),
                     L(12, 8.4, 12, 11), F('M12 2.4c1.1 1.2 1.8 2.2 1.8 3.1a1.8 1.8 0 0 1-3.6 0c0-.9.7-1.9 1.8-3.1Z')],
    'cloud_download': [P(_cloud()), L(12, 10.3, 12, 16.2), PL((9.6, 13.8), (12, 16.2), (14.4, 13.8))],
    'lamp': _bulb(9.1, 5.8, 15.4, 2.8, [(18.1, 2.5), (20.7, 1.4)]),
    # the 'filled' lamp marks the selected emoji section and the active poll hint: a lit bulb
    'lamp_filled': _bulb(10.8, 4.6, 15.9, 2.3, [(18.5, 2.3), (20.9, 1.2)], k=1.0) + _rays(12, 10.8, 6.4, 7.9, (-90, -150, -30)),
    'party_popper': [P('M3.5 20.5L8.3 9.7l6 6L3.5 20.5Z'), P('M11.5 8c.4-1.4-.5-2.4.4-3.9'), P('M16 12.5c1.4-.4 2.4.5 3.9-.4'),
                     L(14, 10, 17.5, 6.5), DOT(15.5, 3.8, 1.2), DOT(20.2, 8.5, 1.2)],
    'clock': [C(12, 12, 5.6), PL((12, 9.2), (12, 12), (14, 13.2))],
    'casino': [RR(3.5, 3.5, 17, 17, 3), DOT(8.2, 8.2, 1.4), DOT(15.8, 8.2, 1.4), DOT(12, 12, 1.4), DOT(8.2, 15.8, 1.4), DOT(15.8, 15.8, 1.4)],
    'animals_outline': BEAR,
    'animals': BEAR,
    'flag_outline': FLAG,
    'flag_filled': FLAG,
    'file_replace': _doc(3.25, 2.5) + _doc(13.75, 13) + SWAP,
    'file_media_replace': _doc(3.25, 2.5) + PICTURE_BR + SWAP,
    'videogame_asset': [RR(2.5, 6.5, 19, 11, 3.5), L(7.5, 9.8, 7.5, 14.2), L(5.3, 12, 9.7, 12), DOT(15.6, 13.3, 1.3), DOT(18, 10.8, 1.3)],
    'redeem': GIFT,
    'bolt': [BOLT],
    # moved up 0.6 so the face sits on the optical centre (the ear tips are light)
    'cat': tfe([P('M4.5 4.5l4 3.2a9 9 0 0 1 7 0l4-3.2v7.3c.6 1 1 2.2 1 3.4 0 3.6-3.8 5.8-8.5 5.8s-8.5-2.2-8.5-5.8c0-1.2.4-2.4 1-3.4V4.5Z'),
                DOT(9, 13, 1.3), DOT(15, 13, 1.3), P('M10.8 16.2h2.4L12 17.4Z')], dy=-0.6),
    'local_florist': [P(_flower(12, 8.2, 5, 2.8, 2.5)), C(12, 8.2, 1.5), L(12, 13.4, 12, 21),
                      P('M12 21c0-3-2.4-5.3-6.5-5.3 0 3 2.4 5.3 6.5 5.3Z'), P('M12 21c0-3 2.4-5.3 6.5-5.3 0 3-2.4 5.3-6.5 5.3Z')],
    'sports_esports': [PAD, L(8, 9.1, 8, 12.3), L(6.4, 10.7, 9.6, 10.7), DOT(15.2, 11.6, 1.2), DOT(17.2, 9.6, 1.2)],
    'home': [P('M4.5 10.2L12 3.8l7.5 6.4v8.3a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-8.3Z'), P('M9.8 20.5v-4.8a1 1 0 0 1 1-1h2.4a1 1 0 0 1 1 1v4.8')],
    'sports_soccer': _soccer(),
    'school': [P('M12 4.5L2.5 9.5l9.5 5 9.5-5L12 4.5Z'), P('M6.5 11.6v4.6c0 1.6 2.5 3.3 5.5 3.3s5.5-1.7 5.5-3.3v-4.6L12 14.5 6.5 11.6Z'), L(21.5, 9.5, 21.5, 15)],
    'work': [RR(3, 7, 18, 13, 2.5), P('M9 7V5.3A1.3 1.3 0 0 1 10.3 4h3.4A1.3 1.3 0 0 1 15 5.3V7'), L(3, 12.5, 21, 12.5)],
    'book': [P('M5 18.5V5.5A2.5 2.5 0 0 1 7.5 3H19v13.5H7A2 2 0 0 0 5 18.5Z'), P('M5 18.5A2 2 0 0 0 7 20.5h12v-4'), P('M10 3v6l2-1.5 2 1.5V3')],
    'thumb_up': [RR(3, 10, 3.8, 10.5, 1.3), P('M6.8 10.5l3.4-6.9a2.2 2.2 0 0 1 2.8 2v4.1h5.4a2 2 0 0 1 2 2.4l-1.3 6.6a2 2 0 0 1-2 1.8H6.8v-10Z')],
    'airplane': tfe([P('M12 2.5c.8 0 1.3 1 1.3 2.2V9l7.7 4.5v2l-7.7-2.3v5l2.5 1.8v1.5L12 20.5l-3.8 1V20l2.5-1.8v-5L3 15.5v-2L10.7 9V4.7c0-1.2.5-2.2 1.3-2.2Z')],
                    0.92, 1.1, -1.1, rot=45),
    'finance': [RR(3.5, 15, 4.4, 5.5, 1), RR(9.8, 12.5, 4.4, 8, 1), RR(16.1, 10, 4.4, 10.5, 1), PL((3.5, 10.5), (8.5, 6), (12.5, 8.5), (19.5, 3.5)),
                PL((15.5, 3.5), (19.5, 3.5), (19.5, 7.5))],
    'bubble_chart': [C(15.6, 8.2, 4.6), C(7.2, 15.2, 3.4), C(16.8, 18.2, 2.2)],
    'gamepad_variant': [PAD, L(8, 9.1, 8, 12.3), L(6.4, 10.7, 9.6, 10.7), DOT(15.2, 11.6, 1.2), DOT(17.2, 9.6, 1.2)],
    'stickers_filled': [P('M20.5 12.5V6A2.5 2.5 0 0 0 18 3.5H6A2.5 2.5 0 0 0 3.5 6v12A2.5 2.5 0 0 0 6 20.5h6.5l8-8Z'),
                        P('M20.5 12.5H16a3.5 3.5 0 0 0-3.5 3.5v4.5'), DOT(8.3, 8.7, 1.25), DOT(13.3, 8.7, 1.25), P('M7.6 12.4a4.6 4.6 0 0 0 4 2.4')],
    'live_help': [core.ICONS['chat_bubble'][0], P('M10 8.6a2.1 2.1 0 0 1 4.1.6c0 1.4-2.1 1.7-2.1 3.1'), DOT(12, 14.6, 1.1)],
}

MAP = {
    'templarian_baseline_broom_24': 'broom',
    'baseline_brush_24': 'brush',
    'belledeboheme_baseline_dice_1_16': 'dice',
    'belledeboheme_baseline_dice_2_16': 'dice',
    'belledeboheme_baseline_dice_3_16': 'dice',
    'belledeboheme_baseline_dice_4_16': 'dice',
    'belledeboheme_baseline_dice_5_16': 'dice',
    'belledeboheme_baseline_dice_6_16': 'dice',
    'baseline_restaurant_menu_24': 'restaurant_menu',
    'baseline_cake_variant_24': 'cake_variant',
    'baseline_cake_variant_14': 'cake_variant',
    'baseline_cloud_download_24': 'cloud_download',
    'baseline_cloud_download_16': 'cloud_download',
    'deproko_baseline_lamp_filled_24': 'lamp_filled',
    'deproko_baseline_lamp_filled_22': 'lamp_filled',
    'baseline_party_popper_24': 'party_popper',
    'baseline_party_popper_16': 'party_popper',
    'baseline_party_popper_72': 'party_popper',
    'deproko_baseline_clock_24': 'clock',
    'baseline_casino_24': 'casino',
    'baseline_casino_16': 'casino',
    'deproko_baseline_lamp_24': 'lamp',
    'deproko_baseline_lamp_22': 'lamp',
    'deproko_baseline_animals_outline_24': 'animals_outline',
    'deproko_baseline_animals_24': 'animals',
    'deproko_baseline_flag_outline_24': 'flag_outline',
    'deproko_baseline_flag_filled_24': 'flag_filled',
    'dot_baseline_file_replace_24': 'file_replace',
    'dot_baseline_file_media_replace_24': 'file_media_replace',
    'baseline_videogame_asset_16': 'videogame_asset',
    'baseline_redeem_16': 'redeem',
    'baseline_bolt_16': 'bolt',
    'templarian_baseline_cat_24': 'cat',
    'baseline_local_florist_24': 'local_florist',
    'baseline_sports_esports_24': 'sports_esports',
    'baseline_home_24': 'home',
    'baseline_sports_soccer_24': 'sports_soccer',
    'baseline_school_24': 'school',
    'baseline_work_24': 'work',
    'baseline_book_24': 'book',
    'baseline_thumb_up_24': 'thumb_up',
    'baseline_airplane_24': 'airplane',
    'baseline_finance_24': 'finance',
    'baseline_bubble_chart_24': 'bubble_chart',
    'templarian_baseline_gamepad_variant_16': 'gamepad_variant',
    'deproko_baseline_stickers_filled_24': 'stickers_filled',
    'baseline_live_help_24': 'live_help',
}
