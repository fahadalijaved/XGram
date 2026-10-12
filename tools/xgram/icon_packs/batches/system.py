# XGram Line / Duotone batch "system": debugging, storage, security, passcode, devices, networks and sessions.
#
# Left out (brand logos, they keep Telegram X's drawing): android, device_android, device_android_x (the Android
# robot), device_xbox (the Xbox logo), device_ubuntu (the Ubuntu logo), device_linux (Tux, the Linux mascot).
import math

from dsl import *
from dsl import core


def _pt(x, y):
    return '%s %s' % (f(x), f(y))


def rpoly(pts, r, closed=True):
    """Polygon with corners rounded by quadratic curves; r is one reach or one per corner. closed=False leaves the
    first and last points as open ends (no rounding there)."""
    n = len(pts)
    radii = r if isinstance(r, (list, tuple)) else [r] * n
    if not closed:
        d = 'M' + _pt(*pts[0])
        for i in range(1, n - 1):
            (px, py), (ax, ay), (bx, by) = pts[i], pts[i - 1], pts[i + 1]
            la, lb = math.hypot(ax - px, ay - py), math.hypot(bx - px, by - py)
            ra, rb = min(radii[i], la / 2), min(radii[i], lb / 2)
            d += 'L' + _pt(px + (ax - px) * ra / la, py + (ay - py) * ra / la)
            d += 'Q' + _pt(px, py) + ' ' + _pt(px + (bx - px) * rb / lb, py + (by - py) * rb / lb)
        return d + 'L' + _pt(*pts[-1])
    ins, outs = [], []
    for i in range(n):
        (px, py), (ax, ay), (bx, by) = pts[i], pts[i - 1], pts[(i + 1) % n]
        la, lb = math.hypot(ax - px, ay - py), math.hypot(bx - px, by - py)
        ra, rb = min(radii[i], la / 2), min(radii[i], lb / 2)
        ins.append((px + (ax - px) * ra / la, py + (ay - py) * ra / la))
        outs.append((px + (bx - px) * rb / lb, py + (by - py) * rb / lb))
    d = 'M' + _pt(*ins[0])
    for i in range(n):
        if i:
            d += 'L' + _pt(*ins[i])
        d += 'Q' + _pt(*pts[i]) + ' ' + _pt(*outs[i])
    return d + 'Z'


def arc(cx, cy, r, a0, a1):
    """Open arc from angle a0 to a1 (degrees, 0 = right, 90 = up)."""
    t0, t1 = math.radians(a0), math.radians(a1)
    x0, y0 = cx + r * math.cos(t0), cy - r * math.sin(t0)
    x1, y1 = cx + r * math.cos(t1), cy - r * math.sin(t1)
    return 'M%sA%s %s 0 %d %d %s' % (_pt(x0, y0), f(r), f(r), 1 if abs(a1 - a0) > 180 else 0, 1 if a1 < a0 else 0, _pt(x1, y1))


def ridge(cx, cy, r, a0, a1, e0=0.0, e1=0.0):
    """A fingerprint ridge: an arc over the top from a0 clockwise to a1, each end run on along its tangent by e0 / e1."""
    t0, t1 = math.radians(a0), math.radians(a1)
    x0, y0 = cx + r * math.cos(t0), cy - r * math.sin(t0)
    x1, y1 = cx + r * math.cos(t1), cy - r * math.sin(t1)
    d = 'M' + _pt(x0 - e0 * math.sin(t0), y0 - e0 * math.cos(t0))
    if e0:
        d += 'L' + _pt(x0, y0)
    d += 'A%s %s 0 %d 1 %s' % (f(r), f(r), 1 if a0 - a1 > 180 else 0, _pt(x1, y1))
    if e1:
        d += 'L' + _pt(x1 + e1 * math.sin(t1), y1 + e1 * math.cos(t1))
    return P(d)


def _arc_to(c, r, a, b, m):
    """SVG arc command from a to b around c through m (all screen points)."""
    cr = (a[0] - c[0]) * (m[1] - c[1]) - (a[1] - c[1]) * (m[0] - c[0])
    ang = lambda p: math.atan2(p[1] - c[1], p[0] - c[0])
    span = (ang(b) - ang(a)) % (2 * math.pi)
    if cr < 0:
        span = 2 * math.pi - span
    return 'A%s %s 0 %d %d %s' % (f(r), f(r), 1 if span > math.pi else 0, 1 if cr > 0 else 0, _pt(*b))


def wrench(hx=15.2, hy=8.8, R=5.4, s=1.65, d0=0.6, hw=1.8, ex=5.4, ey=18.6):
    """A spanner: a round head with an open jaw facing away from the handle, the handle ending in a round cap."""
    L_ = math.hypot(ex - hx, ey - hy)
    ux, uy = (hx - ex) / L_, (hy - ey) / L_  # towards the jaw
    vx, vy = -uy, ux
    G = lambda t, n: (hx + t * ux + n * vx, hy + t * uy + n * vy)
    H = (hx, hy)
    ts, th = math.sqrt(R * R - s * s), math.sqrt(R * R - hw * hw)
    p1, p2, p3, p4 = G(ts, s), G(d0, s), G(d0, -s), G(ts, -s)
    p5, p6, p7, p8 = G(-th, -hw), G(-L_, -hw), G(-L_, hw), G(-th, hw)
    d = 'M' + _pt(*p1) + 'L' + _pt(*p2)
    d += _arc_to(G(d0, 0), s, p2, p3, G(d0 - s, 0))
    d += 'L' + _pt(*p4)
    d += _arc_to(H, R, p4, p5, G(0, -R))
    d += 'L' + _pt(*p6)
    d += _arc_to(G(-L_, 0), hw, p6, p7, G(-L_ - hw, 0))
    d += 'L' + _pt(*p8)
    d += _arc_to(H, R, p8, p1, G(0, R))
    return d + 'Z'


def rotrect(cx, cy, length, width, angle, r):
    a = math.radians(angle)
    ux, uy = math.cos(a) * length / 2, -math.sin(a) * length / 2
    wx, wy = math.sin(a) * width / 2, math.cos(a) * width / 2
    return rpoly([(cx + ux + wx, cy + uy + wy), (cx + ux - wx, cy + uy - wy), (cx - ux - wx, cy - uy - wy), (cx - ux + wx, cy - uy + wy)], r)


def octagon(cx=12, cy=12, half=9, r=1.6):
    R = half / math.cos(math.pi / 8)
    pts = [(cx + R * math.cos(math.radians(22.5 + 45 * k)), cy - R * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
    return rpoly(pts, r)


SHIELD = core.ICONS['security'][0]
BUBBLE = core.ICONS['chat_bubble'][0]
SYNC = core.ICONS['sync']


def card(x0=4.5, y0=2.5, x1=19.5, y1=21.5, cut=4.8):
    """A SIM / memory card: a rounded card with its top left corner cut off."""
    return P(rpoly([(x0 + cut, y0), (x1, y0), (x1, y1), (x0, y1), (x0, y0 + cut)], [1.2, 2.5, 2.5, 2.5, 1.2]))


def flask():
    neck0, neck1, top, shoulder, bottom = 9.8, 14.2, 3.5, 9.2, 20.5
    left, right = 4.4, 19.6
    outline = rpoly([(neck0, top), (neck0, shoulder), (left, bottom), (right, bottom), (neck1, shoulder), (neck1, top)],
                    [0, 1.2, 2.2, 2.2, 1.2, 0], closed=False)
    level = 14.6
    k = (neck0 - left) / (bottom - shoulder)
    xl, xr = neck0 - k * (level - shoulder), neck1 + k * (level - shoulder)
    liquid = rpoly([(xl, level), (xr, level), (right, bottom), (left, bottom)], [0.01, 0.01, 2.2, 2.2])
    return [P(outline), L(8.4, top, 15.6, top), P(liquid)]


def gapped_box(x0, y0, x1, y1, r, gap0, gap1):
    """A rounded box with an opening in its left side between gap0 and gap1."""
    return P('M%sV%sA%s %s 0 0 1 %sH%sA%s %s 0 0 1 %sV%sA%s %s 0 0 1 %sH%sA%s %s 0 0 1 %sV%s' % (
        _pt(x0, gap0), f(y0 + r), f(r), f(r), _pt(x0 + r, y0), f(x1 - r), f(r), f(r), _pt(x1, y0 + r), f(y1 - r),
        f(r), f(r), _pt(x1 - r, y1), f(x0 + r), f(r), f(r), _pt(x0, y1 - r), f(gap1)))


ICONS = {
    # debugging and tools
    'bug_report': [P('M12 19.9c-3 0-5.5-2.5-5.5-5.5v-3.5a3 3 0 0 1 3-3h5a3 3 0 0 1 3 3v3.5c0 3-2.5 5.5-5.5 5.5Z'),
                   P('M9.3 7.9V7.2a2.7 2.7 0 0 1 5.4 0v.7'), L(12, 11.4, 12, 16.4),
                   P('M6.5 12.4H3M6.6 9.6 4.2 7.8M6.9 16.6l-2.6 2'), P('M17.5 12.4H21M17.4 9.6l2.4-1.8M17.1 16.6l2.6 2')],
    'build': [P(wrench())],
    'flask': flask(),
    'calculator': [RR(5, 2.5, 14, 19, 2.5), RR(8, 5.5, 8, 3.6, 1), DOT(9, 13, 1.15), DOT(12, 13, 1.15), DOT(15, 13, 1.15),
                   DOT(9, 17, 1.15), DOT(12, 17, 1.15), DOT(15, 17, 1.15)],
    # updates and sync
    'system_update': [RR(6, 2.5, 12, 19, 2.5), L(12, 6.5, 12, 14), PL((9, 11), (12, 14), (15, 11)), L(10.5, 18, 13.5, 18)],
    'sync_problem': list(SYNC) + [L(12, 8.3, 12, 12.4), DOT(12, 15.5, 1.2)],
    'sync_white': list(SYNC),
    'feedback_white': [BUBBLE, L(12, 7.4, 12, 10.6), DOT(12, 13.6, 1.15)],
    # storage
    'sd_storage': [card(), L(10.5, 6, 10.5, 9), L(13.5, 6, 13.5, 9), L(16.5, 6, 16.5, 9)],
    'storage': [RR(3.5, 3.5, 17, 17, 2.5), L(3.5, 9.17, 20.5, 9.17), L(3.5, 14.83, 20.5, 14.83),
                DOT(7.2, 6.33, 1.1), DOT(7.2, 12, 1.1), DOT(7.2, 17.67, 1.1)],
    'sim_card': [card(), RR(8, 10.5, 8, 8, 1.8), L(12, 10.5, 12, 18.5), L(8, 14.5, 16, 14.5)],
    'sim_card_alert': [card(), L(12, 8.5, 12, 13), DOT(12, 16.5, 1.2)],
    # security and passcodes
    'policy': [SHIELD, C(11.3, 11.2, 2.8), L(13.3, 13.2, 15.4, 15.3)],
    'gpp_maybe': [SHIELD, L(12, 7.8, 12, 12.2), DOT(12, 15.6, 1.2)],
    'dangerous': [P(octagon()), L(9, 9, 15, 15), L(15, 9, 9, 15)],
    'lock_pattern': [C(6.5, 6.5, 2.8), C(6.5, 17.5, 2.8), C(17.5, 6.5, 2.8), DOT(17.5, 17.5, 1.5),
                     L(6.5, 9.3, 6.5, 14.7), L(8.48, 15.52, 15.52, 8.48)],
    'lock_pin': [DOT(x, y, 1.7) for y in (5.5, 12, 18.5) for x in (5.5, 12, 18.5)],
    'fingerprint': [P(arc(12, 13.8, 10, 135, 45)), ridge(12, 14.3, 6.8, 205, -25), ridge(12, 14.3, 3.8, 180, 0, 5.2, 2),
                    L(12, 14.3, 12, 19)],
    'vpn_key': [C(7.5, 12, 4.3), PL((11.8, 12), (20.5, 12), (20.5, 15.5)), L(17, 12, 17, 15)],
    'qrcode_scan': [P('M3 8V5.5A2.5 2.5 0 0 1 5.5 3H8M16 3h2.5A2.5 2.5 0 0 1 21 5.5V8M21 16v2.5a2.5 2.5 0 0 1-2.5 2.5H16M8 21H5.5A2.5 2.5 0 0 1 3 18.5V16'),
                    RR(7, 7, 3.6, 3.6, 1), RR(13.4, 7, 3.6, 3.6, 1), RR(7, 13.4, 3.6, 3.6, 1), DOT(14.4, 14.4, 1.1), DOT(16.6, 16.6, 1.1)],
    'gavel': [P(rotrect(10.1, 8.6, 10, 4.6, 45, 1.4)), L(11.7, 10.2, 20, 18.5), L(4, 20.3, 13, 20.3)],
    # devices and networks
    'device_other': list(core.ICONS['devices']),
    'device_ipad': [RR(4.5, 2.5, 15, 19, 2.5), L(10.5, 18, 13.5, 18)],
    'router': [RR(3, 13, 18, 7.5, 2.5), DOT(7, 16.75, 1.15), DOT(10.5, 16.75, 1.15), L(16, 13, 16, 10),
               P(arc(16, 9.5, 3, 135, 45)), P(arc(16, 9.5, 6, 135, 45))],
    'signal_cellular_alt': [L(5.5, 19.5, 5.5, 15), L(12, 19.5, 12, 10), L(18.5, 19.5, 18.5, 4.5)],
    'wifi': [P(arc(12, 18.8, 4.5, 135, 45)), P(arc(12, 18.8, 9, 135, 45)), P(arc(12, 18.8, 13.5, 132, 48)), DOT(12, 18.8, 1.3)],
    'bluetooth': [PL((6.5, 7.5), (17, 16.5), (12, 21), (12, 3), (17, 7.5), (6.5, 16.5))],
    'launch': list(core.ICONS['open_in_browser']),
    'exit_to_app': [gapped_box(6, 3.5, 21, 20.5, 2.5, 8.5, 15.5), L(3, 12, 15, 12), PL((11.5, 8.5), (15, 12), (11.5, 15.5))],
}

MAP = {
    'baseline_bug_report_24': 'bug_report',
    'baseline_build_24': 'build',
    'templarian_baseline_flask_24': 'flask',
    'templarian_baseline_calculator_18': 'calculator',
    'baseline_system_update_24': 'system_update',
    'baseline_sync_problem_24': 'sync_problem',
    'baseline_sync_white_24': 'sync_white',
    'baseline_feedback_white_24': 'feedback_white',
    'baseline_sd_storage_24': 'sd_storage',
    'baseline_storage_24': 'storage',
    'baseline_sim_card_24': 'sim_card',
    'baseline_sim_card_96': 'sim_card',
    'baseline_sim_card_alert_14': 'sim_card_alert',
    'baseline_sim_card_alert_24': 'sim_card_alert',
    'baseline_policy_24': 'policy',
    'baseline_gpp_maybe_14': 'gpp_maybe',
    'baseline_gpp_maybe_24': 'gpp_maybe',
    'baseline_dangerous_24': 'dangerous',
    'itsspelledhaley_baseline_lock_pattern_24': 'lock_pattern',
    'vkryl_baseline_lock_pin_24': 'lock_pin',
    'baseline_fingerprint_24': 'fingerprint',
    'baseline_vpn_key_24': 'vpn_key',
    'xt3000_baseline_qrcode_scan_24': 'qrcode_scan',
    'baseline_gavel_24': 'gavel',
    'baseline_device_other_24': 'device_other',
    'baseline_device_ipad_24': 'device_ipad',
    'baseline_router_24': 'router',
    'baseline_signal_cellular_alt_24': 'signal_cellular_alt',
    'baseline_wifi_24': 'wifi',
    'baseline_bluetooth_24': 'bluetooth',
    'baseline_launch_24': 'launch',
    'baseline_exit_to_app_24': 'exit_to_app',
}
