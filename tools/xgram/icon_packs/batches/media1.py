# XGram Line batch "media1": media, playback, camera and photo-editor icons.
from dsl import *

import math


def rpoly(pts, r):
    """Closed polygon with corners rounded by quadratic curves of reach r."""
    n = len(pts)
    ins, outs = [], []
    for i in range(n):
        (px, py), (ax, ay), (bx, by) = pts[i], pts[i - 1], pts[(i + 1) % n]
        la = math.hypot(ax - px, ay - py)
        lb = math.hypot(bx - px, by - py)
        ra, rb = min(r, la / 2), min(r, lb / 2)
        ins.append((px + (ax - px) * ra / la, py + (ay - py) * ra / la))
        outs.append((px + (bx - px) * rb / lb, py + (by - py) * rb / lb))
    d = 'M%s %s' % (f(ins[0][0]), f(ins[0][1]))
    for i in range(n):
        if i:
            d += 'L%s %s' % (f(ins[i][0]), f(ins[i][1]))
        d += 'Q%s %s %s %s' % (f(pts[i][0]), f(pts[i][1]), f(outs[i][0]), f(outs[i][1]))
    return P(d + 'Z')


def arc_arrow(cx, cy, r, a0, a1, head=2.3, spread=42):
    """An arc of a circle from angle a0 to a1 (degrees, y down) with an arrowhead at the a1 end."""
    t0, t1 = math.radians(a0), math.radians(a1)
    p0 = (cx + r * math.cos(t0), cy + r * math.sin(t0))
    p1 = (cx + r * math.cos(t1), cy + r * math.sin(t1))
    s = 1 if a1 > a0 else -1
    bx, by = s * math.sin(t1), -s * math.cos(t1)  # pointing back along the arc
    arms = []
    for k in (-1, 1):
        a = math.radians(spread * k)
        arms.append((p1[0] + head * (bx * math.cos(a) - by * math.sin(a)), p1[1] + head * (bx * math.sin(a) + by * math.cos(a))))
    arc = P('M%s %sA%s %s 0 %d %d %s %s' % (f(p0[0]), f(p0[1]), f(r), f(r), 1 if abs(a1 - a0) > 180 else 0, 1 if s > 0 else 0, f(p1[0]), f(p1[1])))
    return [arc, PL(arms[0], p1, arms[1])]


def camera_body(x, y, w, h, r=2.5, bump=4.6):
    """The core camera's body (top edge at y, viewfinder bump 2.2 above it)."""
    side = (w - 2 * r - 3 - bump) / 2
    return P('M%s %sA%s %s 0 0 1 %s %sh%sl1.5-2.2h%sl1.5 2.2h%sA%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 -%s %sh-%sa%s %s 0 0 1 -%s -%sZ' % (
        f(x), f(y + r), f(r), f(r), f(x + r), f(y), f(side), f(bump), f(side), f(r), f(r), f(x + w), f(y + r),
        f(h - 2 * r), f(r), f(r), f(r), f(r), f(w - 2 * r), f(r), f(r), f(r), f(r)))


# GIF: the letters on the centre lines of a 2-unit pen, cap height 6 (y 9..15)
G0, G1, IX, F0, F1, FM = 5.9, 9.5, 12.4, 15.2, 18.2, 17.4


def gif_letters():
    return [P('M%s 9H%sA1.7 1.7 0 0 0 %s 10.7v2.6A1.7 1.7 0 0 0 %s 15H%sV12.3H%s' % (
        f(G1), f(G0 + 1.7), f(G0), f(G0 + 1.7), f(G1), f(G1 - 1.3))),
        L(IX, 9, IX, 15), P('M%s 15V9H%s' % (f(F0), f(F1))), L(F0, 12, FM, 12)]


def gif_knockout():
    """A solid badge with the same letters cut out (letters wound the other way, so the non-zero fill leaves them empty)."""
    h = 0.95
    badge = 'M5 4.1h14A3.4 3.4 0 0 1 22.4 7.5v9A3.4 3.4 0 0 1 19 19.9H5A3.4 3.4 0 0 1 1.6 16.5v-9A3.4 3.4 0 0 1 5 4.1Z'
    g = ('M%s %sH%sA%s %s 0 0 0 %s %sV%sA%s %s 0 0 0 %s %sH%sV%sH%sV%sH%sV%sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sH%sZ' % (
        f(G1 + h), f(9 - h), f(G0 + 1.7), f(1.7 + h), f(1.7 + h), f(G0 - h), f(10.7), f(13.3), f(1.7 + h), f(1.7 + h), f(G0 + 1.7), f(15 + h),
        f(G1 + h), f(12.3 - h), f(G1 - 1.3 - h), f(12.3 + h), f(G1 - h), f(15 - h), f(G0 + 1.7), f(1.7 - h), f(1.7 - h), f(G0 + h), f(13.3),
        f(10.7), f(1.7 - h), f(1.7 - h), f(G0 + 1.7), f(9 + h), f(G1 + h)))
    i = 'M%s %sH%sV%sH%sZ' % (f(IX + h), f(9 - h), f(IX - h), f(15 + h), f(IX + h))
    fl = 'M%s %sH%sV%sH%sV%sH%sV%sH%sV%sH%sZ' % (
        f(F1 + h), f(9 - h), f(F0 - h), f(15 + h), f(F0 + h), f(12 + h), f(FM + h), f(12 - h), f(F0 + h), f(9 + h), f(F1 + h))
    return [F(badge + g + i + fl)]


BELL = 'M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15L6 16.5Z'
# point-symmetric bolt; its waist corners stay clear of the 'off' slash
BOLT = PL((14.4, 2.5), (4.8, 14), (10.4, 14), (9.6, 21.5), (19.2, 10), (13.6, 10), close=True)
GIFT = [RR(3, 7.5, 18, 4.5, 1.5), P('M4.8 12h14.4v6.5a2 2 0 0 1-2 2H6.8a2 2 0 0 1-2-2V12Z'), L(12, 7.5, 12, 20.5),
        P('M12 7.5C11.2 5 9.8 3.5 8.2 3.5a2 2 0 0 0 0 4'), P('M12 7.5c.8-2.5 2.2-4 3.8-4a2 2 0 0 1 0 4')]
# a stack of cards: the back one as an open corner, the front one a 14 x 14 card
STACK_BACK = P('M3.5 7v11a2.5 2.5 0 0 0 2.5 2.5h11')
STACK_CARD = RR(7, 3, 14, 14, 2.5)
# list lines with a round badge in the bottom right corner
LIST_LINES = [L(3, 4.5, 20.5, 4.5), L(3, 9.5, 13.5, 9.5), L(3, 14.5, 8.5, 14.5)]
LIST_BADGE = C(16.8, 15.8, 4.4)

ICONS = {
    'gif': [RR(2.5, 5, 19, 14, 2.5)] + gif_letters(),
    'gif_filled': gif_knockout(),
    'playlist_add': LIST_LINES + [LIST_BADGE, L(16.8, 13.8, 16.8, 17.8), L(14.8, 15.8, 18.8, 15.8)],
    'playlist_add_check': LIST_LINES + [LIST_BADGE, PL((14.8, 15.9), (16.3, 17.3), (18.9, 14.6))],
    'msg_video': [RR(3, 3, 18, 18, 5.5), C(12, 12, 4.5)],  # round video: in Duotone the circle is a lens hole on purpose
    'crop_3': [RR(3, 6, 18, 12, 2.5)],  # same corner as the other crop frames (media2 _frame)
    'crop_original': [RR(3.5, 3.5, 17, 17, 2.5), C(9, 9, 1.8), P('M20.5 15l-4.3-4.3a1 1 0 0 0-1.4 0L6 19.5')],
    'add_a_photo': [L(5, 2.5, 5, 7.5), L(2.5, 5, 7.5, 5), camera_body(7.6, 8.5, 13.6, 12, bump=4), C(14.4, 14.5, 3.2)],
    'notifications_paused': [P(BELL), P('M10 20.5a2 2 0 0 0 4 0'), PL((10, 9.8), (14, 9.8), (10, 14.2), (14, 14.2))],
    'stop': [RR(6, 6, 12, 12, 2.5)],
    'pause': [RR(5.5, 5, 4.5, 14, 1.8), RR(14, 5, 4.5, 14, 1.8)],
    'skip_next': [rpoly([(6, 6.5), (15, 12), (6, 17.5)], 1.3), L(18, 6.5, 18, 17.5)],
    'skip_previous': [rpoly([(18, 6.5), (9, 12), (18, 17.5)], 1.3), L(6, 6.5, 6, 17.5)],
    'repeat_one': [PL((17, 2.5), (20, 5.5), (17, 8.5)), P('M4 11.5V10a4.5 4.5 0 0 1 4.5-4.5H20'), PL((7, 21.5), (4, 18.5), (7, 15.5)),
                   P('M20 12.5V14a4.5 4.5 0 0 1-4.5 4.5H4'), P('M10.9 10.2l1.6-1.2v6')],
    'gift': GIFT,  # the 72 dp gift-message header and the 24 dp outline share one drawing
    'gift_outline': GIFT,
    'collections': [STACK_BACK, STACK_CARD, DOT(11.2, 7.2, 1.4), P('M21 15l-3.8-3.8a1 1 0 0 0-1.4 0L10 17')],
    'audio_collections': [STACK_BACK, STACK_CARD, DOT(12.6, 12.6, 1.9), L(14.4, 12.6, 14.4, 6), P('M14.4 6c1.6.2 3 1 3 2.8')],
    'file_collections': [STACK_BACK, P('M15.5 3H9.5A2.5 2.5 0 0 0 7 5.5v9A2.5 2.5 0 0 0 9.5 17h9A2.5 2.5 0 0 0 21 14.5V8.5L15.5 3Z'),
                         P('M15.5 3v5.5H21')],
    'video_collections': [STACK_BACK, STACK_CARD, rpoly([(12.3, 6.8), (17.3, 10), (12.3, 13.2)], 1)],
    'rotate_90_degrees_ccw': [RR(4.5, 9.5, 11, 11, 2.5)] + arc_arrow(10, 15, 9.5, 0, -90),
    # crop marks; the closed square traces their inner part so Duotone has a body
    'crop_rotate': [PL((8.5, 5.5), (8.5, 15.5), (18.5, 15.5)), PL((5.5, 8.5), (15.5, 8.5), (15.5, 18.5)), P('M8.5 8.5h7v7h-7Z')]
                   + arc_arrow(12, 12, 9, -10, -70) + arc_arrow(12, 12, 9, 170, 110),
    'flash_on': [BOLT],
    'flash_off': [BOLT, SLASH()],
    'zoom_in': [C(10.5, 10.5, 6.5), L(15.3, 15.3, 20.5, 20.5), L(10.5, 8, 10.5, 13), L(8, 10.5, 13, 10.5)],
    'mosaic_group': [RR(3.5, 3.5, 17, 17, 2.5), L(3.5, 12, 20.5, 12), L(12, 12, 12, 20.5)],
}

MAP = {
    'deproko_baseline_gif_24': 'gif', 'baseline_gif_96': 'gif',
    'deproko_baseline_gif_filled_24': 'gif_filled', 'deproko_baseline_gif_filled_16': 'gif_filled',
    'baseline_playlist_add_24': 'playlist_add', 'baseline_playlist_add_18': 'playlist_add',
    'baseline_playlist_add_check_24': 'playlist_add_check',
    'deproko_baseline_msg_video_24': 'msg_video', 'deproko_baseline_msg_video_16': 'msg_video', 'deproko_baseline_msg_video_96': 'msg_video',
    'baseline_crop_3_2_24': 'crop_3',
    'baseline_crop_original_24': 'crop_original',
    'baseline_add_a_photo_56': 'add_a_photo',
    'baseline_notifications_paused_24': 'notifications_paused',
    'baseline_stop_24_white': 'stop', 'baseline_stop_24': 'stop',
    'baseline_pause_24': 'pause', 'baseline_pause_36_white': 'pause',
    'baseline_skip_next_24_white': 'skip_next', 'round_skip_next_36': 'skip_next',
    'baseline_skip_previous_24_white': 'skip_previous', 'round_skip_previous_36': 'skip_previous',
    'round_repeat_one_24': 'repeat_one',
    'baseline_gift_72': 'gift',
    'baseline_gift_outline_24': 'gift_outline',
    'baseline_collections_16': 'collections',
    'ivanliana_baseline_audio_collections_16': 'audio_collections',
    'ivanliana_baseline_file_collections_16': 'file_collections',
    'ivanliana_baseline_video_collections_16': 'video_collections',
    'baseline_rotate_90_degrees_ccw_24': 'rotate_90_degrees_ccw',
    'baseline_crop_rotate_24': 'crop_rotate',
    'baseline_flash_on_24': 'flash_on',
    'baseline_flash_off_24': 'flash_off',
    'baseline_zoom_in_24': 'zoom_in',
    'deproko_baseline_mosaic_group_24': 'mosaic_group',
}
