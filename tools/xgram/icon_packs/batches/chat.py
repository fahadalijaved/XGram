# XGram Line batch "chat": message status, calls, links, bots, incognito, privacy and chat badges.
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


def wavy(cx, cy, r, amp, lobes, n_per=6):
    """A closed scalloped badge (verified seal): radius r +- amp, smooth (Catmull-Rom through samples)."""
    n = lobes * n_per
    pts = []
    for k in range(n):
        a = -math.pi / 2 + 2 * math.pi * k / n
        rr = r + amp * math.cos(lobes * (a + math.pi / 2))
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d = 'M%s %s' % (f(pts[0][0]), f(pts[0][1]))
    for k in range(n):
        p0, p1, p2, p3 = pts[k - 1], pts[k], pts[(k + 1) % n], pts[(k + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += 'C%s %s %s %s %s %s' % (f(c1[0]), f(c1[1]), f(c2[0]), f(c2[1]), f(p2[0]), f(p2[1]))
    return d + 'Z'


def _meet(c1, r1, c2, r2):
    """Intersections of two circles, ordered by angle around c1."""
    dx, dy = c2[0] - c1[0], c2[1] - c1[1]
    d = math.hypot(dx, dy)
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(0.0, r1 * r1 - a * a))
    mx, my = c1[0] + a * dx / d, c1[1] + a * dy / d
    return (mx + h * dy / d, my - h * dx / d), (mx - h * dy / d, my + h * dx / d)


def _uv(u, v):
    """Link axis frame: u runs bottom-left to top-right through the centre, v across it."""
    k = math.sqrt(0.5)
    return 12 + (u + v) * k, 12 + (v - u) * k


def _half_link(sign, u0=3.0, uc=5.94, r=4.5):
    p1, p2 = _uv(sign * u0, r), _uv(sign * uc, r)
    p3, p4 = _uv(sign * uc, -r), _uv(sign * u0, -r)
    pm = _uv(sign * (uc + r), 0)
    sweep = 1 if (pm[0] - p2[0]) * (p3[1] - pm[1]) - (pm[1] - p2[1]) * (p3[0] - pm[0]) > 0 else 0
    return P('M%s %sL%s %sA%s %s 0 0 %d %s %sL%s %s' % (f(p1[0]), f(p1[1]), f(p2[0]), f(p2[1]), f(r), f(r), sweep,
                                                        f(p3[0]), f(p3[1]), f(p4[0]), f(p4[1])))


CORE = core.ICONS
PHONE = CORE['phone'][0][1]
SEND = CORE['send']
LINK = CORE['link']
BUBBLE = CORE['chat_bubble'][0][1]
BELL = core.BELL

# message ticks: the single tick sits where the first tick of the double one is (Telegram X draws them in one place)
TICK1 = PL((4.3, 12.2), (8.2, 16), (15.6, 8))
TICK2 = PL((12.5, 15.7), (12.8, 16), (20.2, 8))

CMD_BOX = RR(3.5, 3.5, 17, 17, 2.5)
INCOGNITO = [L(3, 10.5, 21, 10.5), P('M6.6 10.5l1.9-5.2a1.3 1.3 0 0 1 1.7-.8L12 5.2l1.8-.7a1.3 1.3 0 0 1 1.7.8l1.9 5.2Z'),
             C(7.2, 16.3, 3.2), C(16.8, 16.3, 3.2), P('M10.4 15.6a2.4 2.4 0 0 1 3.2 0')]
TIMER = [C(12, 13.5, 7.5), L(10, 3, 14, 3), L(18.2, 7.3, 19.4, 6.1)]
ENVELOPE_FLAP = PL((6.5, 9.5), (12, 13.2), (17.5, 9.5))
CLOCK_X, CLOCK_R = 16.7, 4.4
CLOCK_BADGE = [C(CLOCK_X, CLOCK_X, CLOCK_R), PL((CLOCK_X, CLOCK_X - 2.1), (CLOCK_X, CLOCK_X), (CLOCK_X + 1.4, CLOCK_X + 0.9))]


def _auto_delete():
    # trash can with a notch for the clock badge
    cx = cy = CLOCK_X
    rad = CLOCK_R + 2.4
    bx = cx - math.sqrt(rad * rad - (20 - cy) ** 2)
    # right edge of the can: from (14.4, 5.5) leaning in to (13.6, 18.1)
    y = 5.5
    while True:
        xx = 14.4 - 0.8 * (y - 5.5) / 12.6
        if (xx - cx) ** 2 + (y - cy) ** 2 <= rad * rad:
            break
        y += 0.01
    body = 'M4.6 5.5L5.4 18.1a2 2 0 0 0 2 1.9H%sA%s %s 0 0 1 %s %sL14.4 5.5Z' % (f(bx), f(rad), f(rad), f(xx), f(y))
    return [L(3, 5.5, 16, 5.5), P('M7.8 5.5V4.3a1 1 0 0 1 1-1h2.4a1 1 0 0 1 1 1v1.2'), P(body)] + CLOCK_BADGE


def _unread_mail():
    # envelope with an unread dot at the bottom right (where Telegram X has it), the body notched around it
    bx, by, br, gap = 18.5, 18.5, 2.5, 5.0
    x_bot = bx - math.sqrt(gap * gap - (19 - by) ** 2)
    y_right = by - math.sqrt(gap * gap - (21 - bx) ** 2)
    body = 'M5.5 5h13A2.5 2.5 0 0 1 21 7.5V%sA%s %s 0 0 0 %s 19H5.5A2.5 2.5 0 0 1 3 16.5v-9A2.5 2.5 0 0 1 5.5 5Z' % (
        f(y_right), f(gap), f(gap), f(x_bot))
    return [P(body), ENVELOPE_FLAP, C(bx, by, br)]


def _bear(eyes):
    hc, hr = (12.0, 13.0), 8.0
    ec, er = (5.6, 6.0), 3.0
    a1, a2 = _meet(hc, hr, ec, er)
    # start at the lower meeting point of the left ear, round the ear, along the head to the right ear, round it, back
    lo, hi = (a1, a2) if a1[1] > a2[1] else (a2, a1)
    rlo, rhi = (24 - lo[0], lo[1]), (24 - hi[0], hi[1])
    d = 'M%s %sA%s %s 0 1 1 %s %sA%s %s 0 0 1 %s %sA%s %s 0 1 1 %s %sA%s %s 0 1 1 %s %sZ' % (
        f(lo[0]), f(lo[1]), f(er), f(er), f(hi[0]), f(hi[1]), f(hr), f(hr), f(rhi[0]), f(rhi[1]),
        f(er), f(er), f(rlo[0]), f(rlo[1]), f(hr), f(hr), f(lo[0]), f(lo[1]))
    return [P(d)] + eyes + [P('M10.6 16.4h2.8L12 17.8Z')]


ICONS = {
    'add_link': tfe(LINK, 0.82, -1.3, -1.3) + [L(18, 14.8, 18, 21), L(14.9, 17.9, 21.1, 17.9)],
    'link_off': [_half_link(1), _half_link(-1), SLASH()],
    'check_single': [TICK1],
    'check_double': [TICK1, TICK2],
    'call_missed': [PL((4, 8), (12, 16), (20, 8)), PL((4, 14), (4, 8), (10, 8))],
    'call_made': [L(6, 18, 18, 6), PL((10, 6), (18, 6), (18, 14))],
    'call_received': [L(18, 6, 6, 18), PL((6, 10), (6, 18), (14, 18))],
    'call_end': [P(tf(PHONE, 0.81, 0, 0.8, 135))],
    'phone_in_talk': [P(PHONE), P('M14 3.5a6.5 6.5 0 0 1 6.5 6.5'), P('M14 7a3 3 0 0 1 3 3')],
    'bots_command': [CMD_BOX, L(14, 7.5, 10, 16.5)],
    'command': [CMD_BOX, L(14, 7.5, 10, 16.5)],
    'bots_keyboard': [CMD_BOX] + [F(rr_d(x, y, 4.4, 4.4, 1.2)) for x in (6.9, 12.7) for y in (6.9, 12.7)],
    'incognito': INCOGNITO,
    'incognito_off': INCOGNITO + [SLASH()],
    # the hat and glasses a little larger than they fit at first, so the lenses keep an open middle at 18 dp
    'incognito_circle': [C(12, 12, 9.2)] + tfe([L(6.6, 11, 17.4, 11), P('M8.7 11l1.1-3.1a.9.9 0 0 1 1.2-.5l1 .4 1-.4a.9.9 0 0 1 1.2.5l1.1 3.1Z'),
                                                C(9.4, 15, 1.9), C(14.6, 15, 1.9)], 1.12, 0, -0.1),
    'translate_off': CORE['translate'] + [SLASH()],
    'auto_delete': _auto_delete(),
    'hot_once': [CORE['whatshot'][0], PL((10.6, 14.6), (12.4, 13.2), (12.4, 18))],
    'delete_alert': [L(3.8, 6.5, 20.2, 6.5), P('M9.2 6.5V4.8a1.3 1.3 0 0 1 1.3-1.3h3a1.3 1.3 0 0 1 1.3 1.3v1.7'),
                     P('M5.8 6.5l.9 12.3a2 2 0 0 0 2 1.7h6.6a2 2 0 0 0 2-1.7l.9-12.3Z'), L(12, 10.2, 12, 14), DOT(12, 17, 1.2)],
    'at': CORE['alternate_email'],
    'pending': [C(12, 12, 9), DOT(7.6, 12, 1.3), DOT(12, 12, 1.3), DOT(16.4, 12, 1.3)],
    'masks': [P('M4.5 5.2c4.9-1.6 10.1-1.6 15 0 .3 6.9-2.2 14.3-7.5 14.8C6.7 19.5 4.2 12.1 4.5 5.2Z'),
              P('M6.9 10.8c1.1-1.4 3.1-1.4 4.2 0'), P('M12.9 10.8c1.1-1.4 3.1-1.4 4.2 0'),
              P('M8.6 14.3c1.9 2 4.9 2 6.8 0')],
    'link_arrow': [L(8, 16, 16, 8), PL((9.5, 8), (16, 8), (16, 14.5))],
    'verify_chat': [P(wavy(12, 12, 6.3, 0.55, 8)), PL((9.5, 12.1), (11.2, 13.8), (14.5, 10.4))],
    'verify': [P(wavy(12, 12, 8, 0.7, 8)), PL((8.6, 12.2), (11, 14.6), (15.4, 10))],
    # contact card with the person and a small handset (it shares a phone number, not just a card)
    'contact_phone': [RR(2.5, 4.5, 19, 15, 2.5), C(8, 10.4, 2.1), P('M4.9 15.9c.5-1.6 1.6-2.5 3.1-2.5s2.6.9 3.1 2.5'),
                      P(tf(PHONE, 0.34, 4.3, 0))],
    'notification_important': [P(BELL), P('M10 20.5a2 2 0 0 0 4 0'), L(12, 8.6, 12, 12.2), DOT(12, 15, 1.1)],
    'message_check': [P(BUBBLE), PL((8.4, 10.6), (11, 13.2), (15.8, 8.4))],
    'unsubscribe': tfe(_unread_mail(), dy=-0.8),  # raised so the envelope and its corner ring sit optically centred
    'timer': TIMER + [L(12, 13.5, 12, 9.5)],
    'timer_off': TIMER + [SLASH()],
    # sits next to the button label, so it keeps Telegram X's smaller inline size (and differs from the camera's flash_on)
    'instantview': [P(tf('M14 3L5.5 13.5h6L10 21l8.5-10.5h-6L14 3Z', 0.78))],
    'send_schedule': tfe(SEND, 0.72, -2.9, -2.9) + CLOCK_BADGE,
    'visibility_message': [P(BUBBLE), P('M6.5 10.5S8.7 7 12 7s5.5 3.5 5.5 3.5S15.3 14 12 14s-5.5-3.5-5.5-3.5Z'), DOT(12, 10.5, 1.5)],
    'mail': [RR(2.5, 5, 19, 14, 2.5), ENVELOPE_FLAP],
    'animals_filled_blink': _bear([P('M7.6 12.4a2 2 0 0 0 3.2 0'), P('M13.2 12.4a2 2 0 0 0 3.2 0')]),
}

MAP = {
    'baseline_add_link_24': 'add_link',
    'baseline_call_missed_18': 'call_missed',
    'baseline_link_off_24': 'link_off',
    'deproko_baseline_check_double_24': 'check_double',
    'deproko_baseline_bots_command_26': 'bots_command',
    'infanf_baseline_incognito_24': 'incognito',
    'infanf_baseline_incognito_20': 'incognito',
    'infanf_baseline_incognito_11': 'incognito',
    'infanf_baseline_incognito_14': 'incognito',
    'baseline_translate_off_24': 'translate_off',
    'baseline_auto_delete_24': 'auto_delete',
    'baseline_call_made_18': 'call_made',
    'baseline_call_received_18': 'call_received',
    'baseline_hot_once_24': 'hot_once',
    'baseline_delete_alert_24': 'delete_alert',
    'deproko_baseline_check_single_24': 'check_single',
    'deproko_baseline_bots_keyboard_26': 'bots_keyboard',
    'round_call_end_24_white': 'call_end',
    'baseline_call_end_16': 'call_end',
    'baseline_call_end_24': 'call_end',
    'baseline_at_16': 'at',
    'baseline_pending_24': 'pending',
    'baseline_phone_in_talk_24': 'phone_in_talk',
    'deproko_baseline_masks_24': 'masks',
    'deproko_baseline_link_arrow_20': 'link_arrow',
    'deproko_baseline_verify_chat_24': 'verify_chat',
    'baseline_contact_phone_24': 'contact_phone',
    'nilsfast_baseline_incognito_off_24': 'incognito_off',
    'baseline_notification_important_14': 'notification_important',
    'baseline_notification_important_24': 'notification_important',
    'andrejsharapov_baseline_message_check_16': 'message_check',
    'andrejsharapov_baseline_message_check_24': 'message_check',
    'baseline_unsubscribe_24': 'unsubscribe',
    'baseline_timer_16': 'timer',
    'baseline_timer_off_16': 'timer_off',
    'baseline_incognito_circle_18': 'incognito_circle',
    'deproko_baseline_instantview_24': 'instantview',
    'deproko_baseline_verify_24': 'verify',
    'dotvhs_baseline_send_schedule_24': 'send_schedule',
    'dotvhs_baseline_visibility_message_24': 'visibility_message',
    'dotvhs_baseline_command_24': 'command',
    'baseline_mail_96': 'mail',
    'deproko_baseline_animals_filled_blink_24': 'animals_filled_blink',
}
