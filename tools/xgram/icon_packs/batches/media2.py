# XGram Line batch "media2": playback, camera, crop and photo editor icons.
import math

from dsl import *


def _speaker(dx=0.0):
    # the speaker of the core volume_up icon
    return P('M%s 9.5h3l5-4v13l-5-4H%sa1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1Z' % (f(4 + dx), f(4 + dx)))


def _upper_hit(c1, c2):
    """the upper intersection of two circles (x, y, r)"""
    (x1, y1, r1), (x2, y2, r2) = c1, c2
    dx, dy = x2 - x1, y2 - y1
    d = math.hypot(dx, dy)
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(0.0, r1 * r1 - a * a))
    mx, my = x1 + a * dx / d, y1 + a * dy / d
    pts = [(mx + h * dy / d, my - h * dx / d), (mx - h * dy / d, my + h * dx / d)]
    return min(pts, key=lambda p: p[1])


def _arc(c, p0, p1):
    """clockwise arc of circle c from p0 to p1"""
    x, y, r = c
    a0 = math.atan2(p0[1] - y, p0[0] - x)
    a1 = math.atan2(p1[1] - y, p1[0] - x)
    span = (a1 - a0) % (2 * math.pi)
    return 'A%s %s 0 %d 1 %s %s' % (f(r), f(r), 1 if span > math.pi else 0, f(p1[0]), f(p1[1]))


def _cloud(left=(7, 14.5, 4.5), main=(12.5, 10.5, 5.5), right=(17.5, 15, 4)):
    """a cloud from three circles standing on one flat bottom line"""
    a = _upper_hit(left, main)
    b = _upper_hit(main, right)
    p0 = (left[0], left[1] + left[2])
    p3 = (right[0], right[1] + right[2])
    return 'M%s %s%s%s%sZ' % (f(p0[0]), f(p0[1]), _arc(left, p0, a), _arc(main, a, b), _arc(right, b, p3))


def _triangle(pts, close=True):
    d = 'M%s %s' % (f(pts[0][0]), f(pts[0][1]))
    for x, y in pts[1:]:
        d += 'L%s %s' % (f(x), f(y))
    return P(d + ('Z' if close else 'L%s %s' % (f(pts[0][0]), f(pts[0][1]))))


def _frame(w, h):
    """a centred rounded frame of the given aspect"""
    return RR(12 - w / 2, 12 - h / 2, w, h, 2.5)


PHONE = RR(6.5, 2.5, 11, 13.5, 2.5)
SWITCH = P('M4 20h6.5M8.5 18l2 2-2 2M14 20h6')
CAMERA = 'M4 8.5A2.5 2.5 0 0 1 6.5 6h1.7l1.5-2.2h4.6L15.8 6h1.7A2.5 2.5 0 0 1 20 8.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5v-9Z'

ICONS = {
    # two pictures trading places
    'image_replace': [RR(2.5, 2.5, 8.5, 8.5, 2), P('M11 8l-2.1-2.1a.8.8 0 0 0-1.1 0L3.5 10.2'),
                      RR(13, 13, 8.5, 8.5, 2), P('M21.5 18.5l-2.1-2.1a.8.8 0 0 0-1.1 0L14 20.7'),
                      P('M5.5 14a3 3 0 0 0 3 3H10M8.5 15.5l1.5 1.5-1.5 1.5'), P('M18.5 10a3 3 0 0 0-3-3H14M15.5 5.5L14 7l1.5 1.5')],
    'play_circle_filled': [C(12, 12, 9), P('M9.8 8.6v6.8a.7.7 0 0 0 1.1.6l5.2-3.4a.7.7 0 0 0 0-1.2l-5.2-3.4a.7.7 0 0 0-1.1.6Z')],
    'sound_muted': [_speaker(0.5), L(16, 9.5, 21, 14.5), L(21, 9.5, 16, 14.5)],
    'adjust': [C(12, 12, 9), DOT(12, 12, 3)],
    'crop_square': [_frame(16, 16)],
    'crop_16': [_frame(18.5, 10.4)],
    # the corners of a frame with no fixed aspect (an inner shape would read as 'square' next to crop_square)
    'crop_free': [P('M3.5 8.5V6A2.5 2.5 0 0 1 6 3.5h2.5'), P('M15.5 3.5H18A2.5 2.5 0 0 1 20.5 6v2.5'),
                  P('M20.5 15.5V18a2.5 2.5 0 0 1-2.5 2.5h-2.5'), P('M8.5 20.5H6A2.5 2.5 0 0 1 3.5 18v-2.5')],
    'play_arrow': [P('M8 5.6v12.8a1 1 0 0 0 1.5.9l9.9-6.4a1 1 0 0 0 0-1.7L9.5 4.7A1 1 0 0 0 8 5.6Z')],
    'shuffle': [P('M3.5 17.5h2.1c1.6 0 3-.8 3.9-2.1l5-6.8c.9-1.3 2.3-2.1 3.9-2.1h2.1'), PL((17.5, 3.5), (20.5, 6.5), (17.5, 9.5)),
                P('M3.5 6.5h2.1c1.6 0 3 .8 3.9 2.1l.8 1.1'), P('M13.7 14.3l.8 1.1c.9 1.3 2.3 2.1 3.9 2.1h2.1'),
                PL((17.5, 14.5), (20.5, 17.5), (17.5, 20.5))],
    'queue_music': [L(3, 6, 13, 6), L(3, 10.5, 13, 10.5), L(3, 15, 9, 15), C(15.5, 17.5, 2.5), P('M18 17.5V4.5c.3 1.6 1.4 2.6 3 3')],
    'cloud_pause': [P(_cloud()), L(10.3, 11.6, 10.3, 15.6), L(13.7, 11.6, 13.7, 15.6)],
    'msg_voice': [RR(9, 3, 6, 11.5, 3), P('M5.5 11a6.5 6.5 0 0 0 13 0'), L(12, 17.5, 12, 21)],
    'image_aspect_ratio': [RR(3, 4.5, 18, 15, 2.5), PL((6.5, 11), (6.5, 8), (9.5, 8)), PL((17.5, 13), (17.5, 16), (14.5, 16))],
    'flip_horizontal': [_triangle([(9.5, 6), (9.5, 18.5), (3.5, 18.5)]), _triangle([(14.5, 6), (14.5, 18.5), (20.5, 18.5)], close=False),
                        L(12, 2.5, 12, 5), L(12, 10.75, 12, 13.25), L(12, 19, 12, 21.5)],
    'zoom_out_map': [P('M3.5 8.5v-5h5M3.5 3.5l5 5'), P('M15.5 3.5h5v5M20.5 3.5l-5 5'), P('M20.5 15.5v5h-5M20.5 20.5l-5-5'),
                     P('M8.5 20.5h-5v-5M3.5 20.5l5-5')],
    'crop_5': [_frame(17.5, 14)],
    'fast_forward': [_triangle([(3, 6.5), (11.5, 12), (3, 17.5)]), _triangle([(12, 6.5), (20.5, 12), (12, 17.5)])],
    'volume_off': [_speaker(), P('M15.5 9a4 4 0 0 1 1.2 3.9'), P('M18.2 6.3a8 8 0 0 1 2.2 8.2'), SLASH()],
    'video_chat': [P('M5.5 4h13A2.5 2.5 0 0 1 21 6.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 3.5V17A2.5 2.5 0 0 1 3 14.5v-8A2.5 2.5 0 0 1 5.5 4Z'),
                   P('M8 8h4.5a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1'), P('M13.5 10l3-1.8v4.6l-3-1.8')],
    'camera_enhance': [P(CAMERA), P('M12 9c.4 2.3 1.7 3.6 4 4-2.3.4-3.6 1.7-4 4-.4-2.3-1.7-3.6-4-4 2.3-.4 3.6-1.7 4-4Z')],
    'camera_rear': [PHONE, C(12, 6.8, 1.8), SWITCH],
    'camera_front': [PHONE, C(12, 7, 2), P('M8.5 13.5c.6-1.5 2-2.3 3.5-2.3s2.9.8 3.5 2.3'), SWITCH],
    'flash_auto': [P('M7 2.5h5.5L10 9h3.5L6.5 21.5l1.5-8H4l3-11Z'), PL((15, 11), (17.75, 3.5), (20.5, 11)), L(15.9, 8.5, 19.6, 8.5)],
    'video_library': [P('M3.5 7v11a2.5 2.5 0 0 0 2.5 2.5h11'), RR(7, 3.5, 14, 13.5, 2.5),
                      P('M12.3 7.7v5.6a.6.6 0 0 0 .9.5l4.3-2.8a.6.6 0 0 0 0-1l-4.3-2.8a.6.6 0 0 0-.9.5Z')],
    'replay': [P('M5 13a7 7 0 1 0 7-7H8.5'), PL((11, 3.5), (8.5, 6), (11, 8.5))],
}

MAP = {
    'dot_baseline_image_replace_24': 'image_replace',
    'baseline_play_circle_filled_24_white': 'play_circle_filled',
    'deproko_baseline_sound_muted_24': 'sound_muted',
    'baseline_adjust_24': 'adjust',
    'baseline_crop_square_24': 'crop_square',
    'baseline_crop_16_9_24': 'crop_16',
    'baseline_crop_free_24': 'crop_free',
    'baseline_play_arrow_36_white': 'play_arrow',
    'round_shuffle_24': 'shuffle',
    'baseline_queue_music_24': 'queue_music',
    'baseline_cloud_pause_24': 'cloud_pause',
    'deproko_baseline_msg_voice_24': 'msg_voice',
    'baseline_image_aspect_ratio_24': 'image_aspect_ratio',
    'dot_baseline_flip_horizontal_24': 'flip_horizontal',
    'baseline_zoom_out_map_24': 'zoom_out_map',
    'baseline_crop_5_4_24': 'crop_5',
    'baseline_fast_forward_24': 'fast_forward',
    'baseline_volume_off_24_white': 'volume_off',
    'baseline_video_chat_24': 'video_chat',
    'baseline_camera_enhance_24': 'camera_enhance',
    'baseline_camera_rear_24': 'camera_rear',
    'baseline_camera_front_24': 'camera_front',
    'baseline_flash_auto_24': 'flash_auto',
    'baseline_video_library_96': 'video_library',
    'baseline_replay_24': 'replay',
}
