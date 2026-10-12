# XGram Line batch "people": accounts, groups, channels and bots, alone or in a round badge.
#
# The badge is the core round badge C(12, 12, 9). The "accept" icons (set as profile, group, channel or bot photo) bite a
# round notch out of its bottom right for a check mark; whatever inside the badge runs into the notch ends on its rim,
# and a closed shape that is cut keeps the rim as its edge, so in Duotone it stays a hole in the badge like the uncut one.
import math

from dsl import *

BX, BY, BR = 19.0, 19.0, 5.3  # the notch
CHECK = PL((16.5, 19.1), (18.2, 20.8), (21, 18))


def _pt(p):
    return '%s %s' % (f(p[0]), f(p[1]))


def _in_notch(p):
    return math.hypot(p[0] - BX, p[1] - BY) < BR


def _notch_arc(p):
    """Arc along the notch rim, from its upper right towards its lower left, ending at p."""
    return 'A%s %s 0 0 0 %s' % (f(BR), f(BR), _pt(p))


def _notch_x(y):
    """Point on the notch rim at height y, on the side facing the badge centre."""
    return BX - math.sqrt(BR * BR - (y - BY) ** 2), y


def _notch_y(x):
    return x, BY - math.sqrt(BR * BR - (x - BX) ** 2)


def _cubic(c, t):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = c
    m = 1 - t
    return (m ** 3 * x0 + 3 * m * m * t * x1 + 3 * m * t * t * x2 + t ** 3 * x3,
            m ** 3 * y0 + 3 * m * m * t * y1 + 3 * m * t * t * y2 + t ** 3 * y3)


def _cubic_until_notch(c):
    """The part of a cubic Bezier before it enters the notch (de Casteljau split)."""
    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        if _in_notch(_cubic(c, mid)):
            hi = mid
        else:
            lo = mid
    t = lo
    p0, p1, p2, p3 = c
    lerp = lambda a, b: (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    a, b, cc = lerp(p0, p1), lerp(p1, p2), lerp(p2, p3)
    d, e = lerp(a, b), lerp(b, cc)
    return p0, a, d, lerp(d, e)


def _curve(*cubics):
    d = 'M' + _pt(cubics[0][0])
    for c in cubics:
        d += 'C%s %s %s' % (_pt(c[1]), _pt(c[2]), _pt(c[3]))
    return d


def _shoulders(x0, y0, cx, top):
    """A body (shoulder arc) from (x0, y0) over the neck at (cx, top) down to the mirror point: two cubics."""
    w = cx - x0
    left = ((x0, y0), (x0 + 0.217 * w, y0 - 0.64 * (y0 - top)), (cx - 0.433 * w, top), (cx, top))
    right = ((cx, top), (cx + 0.433 * w, top), (2 * cx - x0 - 0.217 * w, y0 - 0.64 * (y0 - top)), (2 * cx - x0, y0))
    return left, right


def _badge_notched():
    dx, dy = BX - 12, BY - 12
    d = math.hypot(dx, dy)
    a = (d * d + 81 - BR * BR) / (2 * d)
    h = math.sqrt(81 - a * a)
    ux, uy = dx / d, dy / d
    mx, my = 12 + a * ux, 12 + a * uy
    p1 = (mx + h * uy, my - h * ux)  # upper right end of the notch
    p2 = (mx - h * uy, my + h * ux)  # lower left end
    return P('M%sA9 9 0 1 0 %sA%s %s 0 0 1 %sZ' % (_pt(p1), _pt(p2), f(BR), f(BR), _pt(p1)))


NOTCHED = _badge_notched()

# account: head and shoulders, the shoulders run into the badge rim
_PB = _shoulders(6, 18.3, 12, 14.7)
PERSON_IN = [C(12, 9.4, 3), P(_curve(*_PB))]
PERSON_IN_CUT = [C(12, 9.4, 3), P(_curve(_PB[0], _cubic_until_notch(_PB[1])))]

# group: a person in front, a second one behind on the right
_GB = _shoulders(4.8, 17.4, 9.6, 14.3)
_G_BACK_HEAD = P('M14.4 7.4a2.4 2.4 0 0 1 0 4.6')
GROUP_IN = [C(9.6, 10.2, 2.4), P(_curve(*_GB)), _G_BACK_HEAD, P('M16.4 14.5c1.3.4 2.3 1.4 2.8 2.9')]
GROUP_IN_CUT = [C(9.6, 10.2, 2.4), P(_curve(_GB[0], _cubic_until_notch(_GB[1]))), _G_BACK_HEAD]


# channel: a bullhorn with a handle and a sound wave
def _horn(x0, xm, xf, top, bottom, cut=False):
    """Bullhorn: box from x0 to xm (y 10..14), cone widening to the front edge at xf (top..bottom)."""
    head = 'M%s 10H%sL%s %s' % (f(x0 + 0.9), f(xm), f(xf), f(top))
    tail = 'L%s 14H%sa.9.9 0 0 1-.9-.9v-2.2a.9.9 0 0 1 .9-.9Z' % (f(xm), f(x0 + 0.9))
    if not cut:
        return P(head + 'V%s' % f(bottom) + tail)
    # the front edge meets the notch, then the rim runs to the lower edge of the cone
    front = _notch_y(xf)
    ax, ay, bx_, by_ = xf, bottom, xm, 14.0
    lo, hi = 0.0, 1.0  # first point of the lower edge (from the front corner) outside the notch
    for _ in range(40):
        mid = (lo + hi) / 2
        if _in_notch((ax + (bx_ - ax) * mid, ay + (by_ - ay) * mid)):
            lo = mid
        else:
            hi = mid
    low = (ax + (bx_ - ax) * hi, ay + (by_ - ay) * hi)
    return P(head + 'V%s' % f(front[1]) + _notch_arc(low) + tail)


CHANNEL_IN = [_horn(6, 8.6, 14.2, 7.4, 16.6), P('M17 10.5a2 2 0 0 1 0 3'), L(8.9, 14.4, 9.7, 17)]
CHANNEL_IN_CUT = [_horn(7, 9.6, 16.2, 7.4, 16.6, cut=True), L(9.9, 14.4, 10.7, 17)]


# bot: rounded head with an antenna and two eyes
def _bot_head(x, y, w, h, r, cut=False):
    if not cut:
        return RR(x, y, w, h, r)
    right = _notch_y(x + w)
    low = _notch_x(y + h)
    return P('M%sH%sa%s %s 0 0 1-%s-%sV%sa%s %s 0 0 1 %s-%sH%sa%s %s 0 0 1 %s %sV%s%sZ' % (
        _pt(low), f(x + r), f(r), f(r), f(r), f(r), f(y + r), f(r), f(r), f(r), f(r), f(x + w - r), f(r), f(r), f(r), f(r),
        f(right[1]), _notch_arc(low)))


_BOT_FACE = [L(12, 10, 12, 8.2), DOT(12, 7, 1.15), DOT(10.4, 13, 1.1), DOT(13.6, 13, 1.1)]
BOT_IN = [_bot_head(7.5, 10, 9, 7, 2)] + _BOT_FACE
BOT_IN_CUT = [_bot_head(7.5, 10, 9, 7, 2, cut=True)] + _BOT_FACE

ICONS = {
    'identifier': [RR(3, 5.5, 18, 13, 2.5), L(8, 9, 8, 15), P('M11.5 9h1.5a3 3 0 0 1 0 6h-1.5V9')],
    'group_add': [C(9, 8.3, 3.6), P('M2.8 20c0-3.6 2.8-6 6.2-6 1.2 0 2.3.3 3.2.8'), P('M15.2 4.9a3.4 3.4 0 0 1 0 6.6'),
                  L(18, 13.8, 18, 20.2), L(14.8, 17, 21.2, 17)],
    'account_circle': [C(12, 12, 9)] + PERSON_IN,
    'crown': [P('M5.2 16L3.5 7l4.8 4L12 4.5 15.7 11l4.8-4-1.7 9H5.2Z'), L(5.5, 19.5, 18.5, 19.5)],
    'profile_accept': [NOTCHED] + PERSON_IN_CUT + [CHECK],
    'group_accept': [NOTCHED] + GROUP_IN_CUT + [CHECK],
    'channel_accept': [NOTCHED] + CHANNEL_IN_CUT + [CHECK],
    'bot_accept': [NOTCHED] + BOT_IN_CUT + [CHECK],
    'group_circle': [C(12, 12, 9)] + GROUP_IN,
    'channel_circle': [C(12, 12, 9)] + CHANNEL_IN,
    'bot_circle': [C(12, 12, 9)] + BOT_IN,
    'account_switch': [C(7.5, 5.8, 2.6), P('M3 13c0-2.6 2-4.4 4.5-4.4S12 10.4 12 13'), C(16.5, 5.8, 2.6), P('M12 13c0-2.6 2-4.4 4.5-4.4S21 10.4 21 13'),
                       L(4.5, 18.5, 19.5, 18.5), PL((7, 16.2), (4.5, 18.5), (7, 20.8)), PL((17, 16.2), (19.5, 18.5), (17, 20.8))],
}

MAP = {
    'baseline_identifier_24': 'identifier',
    'baseline_group_add_16': 'group_add',
    'baseline_group_add_24': 'group_add',
    'baseline_account_circle_16': 'account_circle',
    'baseline_account_circle_24': 'account_circle',
    'dot_baseline_account_circle_18': 'account_circle',
    'baseline_crown_24': 'crown',
    'dot_baseline_profile_accept_24': 'profile_accept',
    'dot_baseline_group_accept_24': 'group_accept',
    'dot_baseline_channel_accept_24': 'channel_accept',
    'dot_baseline_bot_accept_24': 'bot_accept',
    'dot_baseline_group_circle_18': 'group_circle',
    'dot_baseline_channel_circle_18': 'channel_circle',
    'dot_baseline_bot_circle_18': 'bot_circle',
    'templarian_baseline_account_switch_24': 'account_switch',
}
