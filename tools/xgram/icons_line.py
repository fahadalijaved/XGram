"""XGram icon packs: XGram Line, Bold, Duotone and Sharp, drawn from one set of shapes on a 24 grid.

Writes app/src/main/res/drawable/xgl_*.xml (Android vector drawables), the Java table that maps Telegram X's icons
to them (com/fahadali/xgram/appearance/XgramIconPackTable.java) and docs/xgram/icons/preview.html, which shows
every XGram Line icon next to the Telegram X icon it replaces.

  python tools/xgram/icons_line.py
"""
import math
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable')
JAVA = os.path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'fahadali', 'xgram', 'appearance', 'XgramIconPackTable.java')
PREVIEW = os.path.join(ROOT, 'docs', 'xgram', 'icons', 'preview.html')

STROKE = 2.0


def f(v):
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


# Element helpers: each returns ('s', d) for a stroked path or ('f', d) for a filled one.

def P(d):
    return ('s', d)


def F(d):
    return ('f', d)


def L(x1, y1, x2, y2):
    return ('s', 'M%s %sL%s %s' % (f(x1), f(y1), f(x2), f(y2)))


def PL(*pts, close=False):
    d = 'M%s %s' % (f(pts[0][0]), f(pts[0][1]))
    for x, y in pts[1:]:
        d += 'L%s %s' % (f(x), f(y))
    if close:
        d += 'Z'
    return ('s', d)


def circle_d(cx, cy, r):
    return 'M%s %sa%s %s 0 1 0 %s 0a%s %s 0 1 0 %s 0' % (f(cx - r), f(cy), f(r), f(r), f(2 * r), f(r), f(r), f(-2 * r))


def C(cx, cy, r):
    return ('c', (cx, cy, r))


def DOT(cx, cy, r=1.25):
    return ('dot', (cx, cy, r))


def rr_d(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    if r <= 0:
        return 'M%s %sh%sv%sh%sZ' % (f(x), f(y), f(w), f(h), f(-w))
    return 'M%s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sZ' % (
        f(x + r), f(y), f(w - 2 * r), f(r), f(r), f(r), f(r), f(h - 2 * r), f(r), f(r), f(-r), f(r),
        f(-(w - 2 * r)), f(r), f(r), f(-r), f(-r), f(-(h - 2 * r)), f(r), f(r), f(r), f(-r))


def RR(x, y, w, h, r):
    return ('rr', (x, y, w, h, r))


def SLASH():
    return L(3.5, 3.5, 20.5, 20.5)


def gear(cx=12, cy=12, teeth=8, r_out=9.6, r_in=7.4, hole=3):
    pts = []
    step = 2 * math.pi / teeth
    for i in range(teeth):
        a = i * step - math.pi / 2
        # a tooth: flat top on the outer radius, sloped sides down to the inner radius
        for da, r in ((-0.30, r_in), (-0.17, r_out), (0.17, r_out), (0.30, r_in)):
            pts.append((cx + r * math.cos(a + da * step / 0.62), cy + r * math.sin(a + da * step / 0.62)))
    d = 'M%s %s' % (f(pts[0][0]), f(pts[0][1]))
    for x, y in pts[1:]:
        d += 'L%s %s' % (f(x), f(y))
    d += 'Z'
    return [('s', d), C(cx, cy, hole)]


def star_d(cx=12, cy=12.4, r1=9.2, r2=4.3):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = r1 if i % 2 == 0 else r2
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d = 'M%s %s' % (f(pts[0][0]), f(pts[0][1]))
    for x, y in pts[1:]:
        d += 'L%s %s' % (f(x), f(y))
    return d + 'Z'


BELL = 'M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15L6 16.5Z'
EYE = 'M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7S2.5 12 2.5 12Z'
PIN = 'M8.5 3.5h7M10 3.5v6l-3.5 4.5h11L14 9.5v-6M12 14v6.5'
ARCHIVE_TOP = RR(3, 4, 18, 4.5, 1.5)
ARCHIVE_BODY = P('M4.8 8.5V18.5a2 2 0 0 0 2 2h10.4a2 2 0 0 0 2-2V8.5')

ICONS = {
    # navigation and header
    'search': [C(10.5, 10.5, 6.5), L(15.3, 15.3, 20.5, 20.5)],
    'more_vert': [DOT(12, 5.5, 1.6), DOT(12, 12, 1.6), DOT(12, 18.5, 1.6)],
    'more_horiz': [DOT(5.5, 12, 1.6), DOT(12, 12, 1.6), DOT(18.5, 12, 1.6)],
    'close': [L(6, 6, 18, 18), L(18, 6, 6, 18)],
    'check': [PL((4.5, 12.5), (9.5, 17.5), (19.5, 7))],
    'add': [L(12, 5, 12, 19), L(5, 12, 19, 12)],
    'arrow_forward': [L(4.5, 12, 19, 12), PL((13, 6), (19, 12), (13, 18))],
    'arrow_upward': [L(12, 19.5, 12, 5), PL((6, 11), (12, 5), (18, 11))],
    'arrow_down': [L(12, 4.5, 12, 19), PL((6, 13), (12, 19), (18, 13))],
    'edit': [P('M15.6 4.4a2.1 2.1 0 0 1 3 0l1 1a2.1 2.1 0 0 1 0 3L8.5 19.5 3.8 20.2l.7-4.7L15.6 4.4Z'), L(13.8, 6.2, 17.8, 10.2)],
    'delete': [L(3.8, 6.5, 20.2, 6.5), P('M9.2 6.5V4.8a1.3 1.3 0 0 1 1.3-1.3h3a1.3 1.3 0 0 1 1.3 1.3v1.7'), P('M5.8 6.5l.9 12.3a2 2 0 0 0 2 1.7h6.6a2 2 0 0 0 2-1.7l.9-12.3'), L(10, 10.5, 10, 16.5), L(14, 10.5, 14, 16.5)],
    'copy': [RR(8.5, 8.5, 12, 12, 2.5), P('M15.5 8.5V6a2.5 2.5 0 0 0-2.5-2.5H6A2.5 2.5 0 0 0 3.5 6v7A2.5 2.5 0 0 0 6 15.5h2.5')],
    'reply': [PL((9.5, 14.5), (4, 9), (9.5, 3.5)), P('M4 9h9.5a6.5 6.5 0 0 1 6.5 6.5V20')],
    'forward': [PL((14.5, 14.5), (20, 9), (14.5, 3.5)), P('M20 9h-9.5A6.5 6.5 0 0 0 4 15.5V20')],
    'share': [L(12, 14.5, 12, 3.5), PL((7.5, 8), (12, 3.5), (16.5, 8)), P('M5 12.5v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6')],
    'pin': [P(PIN)],
    'unpin': [P(PIN), SLASH()],
    'info': [C(12, 12, 9), L(12, 11, 12, 16.5), DOT(12, 7.6, 1.2)],
    'warning': [P('M10.3 4.6a2 2 0 0 1 3.4 0l7.4 12.9a2 2 0 0 1-1.7 3H4.6a2 2 0 0 1-1.7-3l7.4-12.9Z'), L(12, 9.5, 12, 13.5), DOT(12, 16.8, 1.2)],
    'error': [C(12, 12, 9), L(12, 7.5, 12, 12.8), DOT(12, 16.3, 1.2)],
    'cancel': [C(12, 12, 9), L(9, 9, 15, 15), L(15, 9, 9, 15)],
    'remove_circle': [C(12, 12, 9), L(8, 12, 16, 12)],
    'check_circle': [C(12, 12, 9), PL((8, 12.3), (11, 15.3), (16.3, 9.5))],
    'block': [C(12, 12, 9), L(5.7, 5.7, 18.3, 18.3)],
    'help': [C(12, 12, 9), P('M9.4 9.3a2.7 2.7 0 0 1 5.3.7c0 1.8-2.7 2.3-2.7 4'), DOT(12, 17, 1.2)],
    'link': [P('M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1.1 1.1'), P('M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1.1-1.1')],
    'visibility': [P(EYE), C(12, 12, 3)],
    'eye_off': [P(EYE), C(12, 12, 3), SLASH()],
    'open_in_browser': [P('M14 3.5h6.5V10'), L(20.5, 3.5, 11, 13), P('M18 14v4.5a2 2 0 0 1-2 2H5.5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2H10')],
    # people and chats
    'person': [C(12, 8, 4), P('M4.5 20.5c0-3.9 3.4-6.5 7.5-6.5s7.5 2.6 7.5 6.5')],
    'person_add': [C(10, 8, 4), P('M2.8 20.5c0-3.9 3.2-6.5 7.2-6.5 1.6 0 3 .4 4.2 1.1'), L(18.5, 13, 18.5, 20), L(15, 16.5, 22, 16.5)],
    'group': [C(9, 8.3, 3.6), P('M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6'), P('M15.2 4.9a3.4 3.4 0 0 1 0 6.6'), P('M17.4 14.3c2.4.6 4.1 2.6 4.1 5.7')],
    'contacts': [RR(4, 3, 16, 18, 2.5), C(12, 10, 2.8), P('M7.5 17c.7-2 2.4-3.2 4.5-3.2s3.8 1.2 4.5 3.2')],
    'chat_bubble': [P('M5.5 4h13A2.5 2.5 0 0 1 21 6.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 3.5V17A2.5 2.5 0 0 1 3 14.5v-8A2.5 2.5 0 0 1 5.5 4Z')],
    'chat_unread': [P('M13.5 4h-8A2.5 2.5 0 0 0 3 6.5v8A2.5 2.5 0 0 0 5.5 17v3.5L10 17h8.5a2.5 2.5 0 0 0 2.5-2.5V11'), C(18.5, 5.5, 2.5)],
    'forum': [P('M4.8 3.5h9.4a1.8 1.8 0 0 1 1.8 1.8v6.4a1.8 1.8 0 0 1-1.8 1.8H9l-3.5 3V13.5h-.7A1.8 1.8 0 0 1 3 11.7V5.3a1.8 1.8 0 0 1 1.8-1.8Z'), P('M19 8.5h.2A1.8 1.8 0 0 1 21 10.3v6.4a1.8 1.8 0 0 1-1.8 1.8h-.7v3l-3.5-3h-3.5a1.8 1.8 0 0 1-1.8-1.8v-.2')],
    'bullhorn': [P('M3.5 10.3v3.4a1.3 1.3 0 0 0 1.3 1.3H7l7.5 4.5v-15L7 9H4.8a1.3 1.3 0 0 0-1.3 1.3Z'), P('M18 9a4 4 0 0 1 0 6'), P('M7 15l1.4 4.6a1.3 1.3 0 0 0 1.2.9h1.6l-1-5')],
    'bots': [RR(4.5, 8, 15, 11.5, 3.5), L(12, 8, 12, 5.2), DOT(12, 4.2, 1.3), DOT(9.4, 13.5, 1.3), DOT(14.6, 13.5, 1.3), L(2.5, 12.5, 2.5, 15), L(21.5, 12.5, 21.5, 15)],
    'phone': [P('M5.2 3.5h2.9a1 1 0 0 1 .9.7l1.2 3.4a1 1 0 0 1-.3 1.1L8.3 10a11.5 11.5 0 0 0 5.7 5.7l1.3-1.6a1 1 0 0 1 1.1-.3l3.4 1.2a1 1 0 0 1 .7.9v2.9a1.8 1.8 0 0 1-1.9 1.8A16.6 16.6 0 0 1 3.4 5.4a1.8 1.8 0 0 1 1.8-1.9Z')],
    'videocam': [RR(3, 6.5, 12.5, 11, 2.5), P('M15.5 10.5l4.3-2.6a.8.8 0 0 1 1.2.7v6.8a.8.8 0 0 1-1.2.7l-4.3-2.6')],
    'lock': [RR(5, 10.5, 14, 10, 2.5), P('M8 10.5V7.5a4 4 0 0 1 8 0v3'), L(12, 14.5, 12, 16.5)],
    'security': [P('M12 3l7.5 3v5.5c0 4.6-3.2 8.2-7.5 9.5-4.3-1.3-7.5-4.9-7.5-9.5V6L12 3Z'), PL((9, 12), (11, 14), (15, 10))],
    'ghost': [P('M5 20.5V11a7 7 0 0 1 14 0v9.5l-2.3-1.6-2.4 1.6-2.3-1.6-2.3 1.6-2.4-1.6L5 20.5Z'), DOT(9.5, 11, 1.3), DOT(14.5, 11, 1.3)],
    'bookmark': [P('M6.5 4.8A1.8 1.8 0 0 1 8.3 3h7.4a1.8 1.8 0 0 1 1.8 1.8V21L12 17.2 6.5 21V4.8Z')],
    'star': [P(star_d())],
    'stars': [C(12, 12, 9.2), P(star_d(12, 12.4, 5.2, 2.4))],
    'favorite': [P('M12 20.3S3.5 15.4 3.5 9.4A4.6 4.6 0 0 1 12 6.9a4.6 4.6 0 0 1 8.5 2.5c0 6-8.5 10.9-8.5 10.9Z')],
    'whatshot': [P('M12 21c-3.9 0-7-2.9-7-6.7 0-3 1.8-5 3.5-6.9.4 1.6 1.3 2.8 2.6 3.3C11 7.1 12.6 4.6 15 3c-.3 2.6.8 4.6 2.1 6.1 1.2 1.4 1.9 3 1.9 5.1C19 18.1 15.9 21 12 21Z')],
    # notifications and time
    'notifications': [P(BELL), P('M10 20.5a2 2 0 0 0 4 0')],
    'notifications_off': [P(BELL), P('M10 20.5a2 2 0 0 0 4 0'), SLASH()],
    'notifications_active': [P(BELL), P('M10 20.5a2 2 0 0 0 4 0'), P('M3 8.5a8 8 0 0 1 2.5-4.5'), P('M21 8.5A8 8 0 0 0 18.5 4')],
    'time': [C(12, 12, 9), PL((12, 7), (12, 12), (15.3, 14))],
    'history': [P('M4.1 13.5A8 8 0 1 0 6.3 6.3L4 8.5'), PL((4, 4.5), (4, 8.5), (8, 8.5)), PL((12, 8), (12, 12.3), (15, 14.1))],
    'calendar': [RR(3.5, 5, 17, 15.5, 2.5), L(3.5, 10, 20.5, 10), L(8, 3, 8, 7), L(16, 3, 16, 7)],
    'night': [P('M20 14.6A8.5 8.5 0 1 1 9.4 4a7 7 0 0 0 10.6 10.6Z')],
    'location': [P('M12 21s-7-6.1-7-11.5a7 7 0 0 1 14 0C19 14.9 12 21 12 21Z'), C(12, 9.5, 2.5)],
    # files and media
    'image': [RR(3, 4, 18, 16, 2.5), C(8.5, 9.5, 1.8), P('M21 15.5l-4.6-4.6a1 1 0 0 0-1.4 0L6 20')],
    'camera': [P('M4 8.5A2.5 2.5 0 0 1 6.5 6h1.7l1.5-2.2h4.6L15.8 6h1.7A2.5 2.5 0 0 1 20 8.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5v-9Z'), C(12, 13, 3.5)],
    'file': [P('M14 3H7.5A2.5 2.5 0 0 0 5 5.5v13A2.5 2.5 0 0 0 7.5 21h9a2.5 2.5 0 0 0 2.5-2.5V8l-5-5Z'), P('M14 3v5h5')],
    'folder': [P('M3 7.5A2.5 2.5 0 0 1 5.5 5h3.6l2.2 2.5h7.2A2.5 2.5 0 0 1 21 10v7.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-10Z')],
    'archive': [ARCHIVE_TOP, ARCHIVE_BODY, L(10, 12.5, 14, 12.5)],
    'unarchive': [ARCHIVE_TOP, ARCHIVE_BODY, L(12, 17.5, 12, 11.5), PL((9.5, 14), (12, 11.5), (14.5, 14))],
    'download': [L(12, 4, 12, 15), PL((7.5, 10.5), (12, 15), (16.5, 10.5)), L(5, 20, 19, 20)],
    'attach': [P('M20 11.3l-7.8 7.8a5.3 5.3 0 0 1-7.5-7.5l8-8a3.5 3.5 0 0 1 5 5l-8 8a1.8 1.8 0 0 1-2.5-2.5l7.4-7.4')],
    'mic': [RR(9, 3, 6, 11.5, 3), P('M5.5 11a6.5 6.5 0 0 0 13 0'), L(12, 17.5, 12, 21)],
    'music_note': [P('M9 18V5.5l11-2.3v12.3'), C(6.5, 18, 2.5), C(17.5, 15.5, 2.5)],
    'volume_up': [P('M4 9.5h3l5-4v13l-5-4H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1Z'), P('M15.5 9a4 4 0 0 1 0 6'), P('M18.2 6.3a8 8 0 0 1 0 11.4')],
    'stickers': [P('M20.5 12.5V6A2.5 2.5 0 0 0 18 3.5H6A2.5 2.5 0 0 0 3.5 6v12A2.5 2.5 0 0 0 6 20.5h6.5'), P('M20.5 12.5l-8 8'), P('M20.5 12.5H16a3.5 3.5 0 0 0-3.5 3.5v4.5')],
    'emoticon': [C(12, 12, 9), P('M8 14.2a5 5 0 0 0 8 0'), DOT(9, 9.8, 1.3), DOT(15, 9.8, 1.3)],
    'send': [P('M20.6 3.4L3.6 10.2a.6.6 0 0 0 0 1.1l6.6 2.5 2.5 6.6a.6.6 0 0 0 1.1 0L20.6 3.4Z'), L(10.2, 13.8, 15.5, 8.5)],
    'keyboard': [RR(2.5, 6, 19, 12, 2.5), DOT(6.5, 10, 1), DOT(10, 10, 1), DOT(13.5, 10, 1), DOT(17.5, 10, 1), L(8, 14.3, 16, 14.3)],
    'backspace': [P('M8.4 5h10.1A2.5 2.5 0 0 1 21 7.5v9a2.5 2.5 0 0 1-2.5 2.5H8.4a1.5 1.5 0 0 1-1.1-.5L2.5 12l4.8-6.5a1.5 1.5 0 0 1 1.1-.5Z'), L(11, 9.5, 16, 14.5), L(16, 9.5, 11, 14.5)],
    # settings
    'settings': gear(),
    'palette': [P('M12 3a9 9 0 0 0 0 18c1.1 0 1.8-.8 1.8-1.7 0-.4-.2-.8-.5-1.2a1.8 1.8 0 0 1 1.4-3H16a5 5 0 0 0 5-5C21 6 17 3 12 3Z'), DOT(7.5, 11.5, 1.3), DOT(9.5, 7.5, 1.3), DOT(14.5, 7.5, 1.3), DOT(17, 11, 1.3)],
    'language': [C(12, 12, 9), L(3, 12, 21, 12), P('M12 3c2.4 2.4 3.6 5.4 3.6 9s-1.2 6.6-3.6 9c-2.4-2.4-3.6-5.4-3.6-9S9.6 5.4 12 3Z')],
    'translate': [L(3.5, 5.5, 13.5, 5.5), L(8.5, 3, 8.5, 5.5), P('M11.5 5.5c-1 4-3.3 7.1-7 9'), P('M6.5 9c1 2.1 2.8 3.8 5 4.6'), PL((13, 21), (17, 12), (21, 21)), L(14.4, 18, 19.6, 18)],
    'code': [PL((8, 7), (3, 12), (8, 17)), PL((16, 7), (21, 12), (16, 17))],
    'alternate_email': [C(12, 12, 4), P('M16 8v5.3a2.5 2.5 0 0 0 5 0V12a9 9 0 1 0-3.5 7.1')],
    'tune': [L(4, 7, 12, 7), L(17, 7, 20, 7), C(14.5, 7, 2.3), L(4, 17, 7, 17), L(12, 17, 20, 17), C(9.5, 17, 2.3)],
    'import_export': [L(8, 3.5, 8, 17), PL((4.5, 13.5), (8, 17), (11.5, 13.5)), L(16, 20.5, 16, 7), PL((12.5, 10.5), (16, 7), (19.5, 10.5))],
    'report': [L(5, 21, 5, 3.8), P('M5 4.5h12.2a.8.8 0 0 1 .7 1.2l-2.2 3.8 2.2 3.8a.8.8 0 0 1-.7 1.2H5')],
    'poll': [L(6, 20, 6, 13), L(12, 20, 12, 4.5), L(18, 20, 18, 9.5)],
    'repeat': [PL((17, 2.5), (20, 5.5), (17, 8.5)), P('M4 11.5V10a4.5 4.5 0 0 1 4.5-4.5H20'), PL((7, 21.5), (4, 18.5), (7, 15.5)), P('M20 12.5V14a4.5 4.5 0 0 1-4.5 4.5H4')],
    'sync': [P('M20 12a8 8 0 0 1-14.3 4.9'), P('M4 12a8 8 0 0 1 14.3-4.9'), PL((18.5, 3), (18.5, 7.2), (14.3, 7.2)), PL((5.5, 21), (5.5, 16.8), (9.7, 16.8))],
    'done_all': [PL((2, 12.5), (6.5, 17), (15.5, 7.5)), PL((11.5, 16), (12.5, 17), (21.5, 7.5))],
    'wallet': [P('M19 7.5V6a2.5 2.5 0 0 0-2.5-2.5h-11A2.5 2.5 0 0 0 3 6v12a2.5 2.5 0 0 0 2.5 2.5h13A2.5 2.5 0 0 0 21 18v-8a2.5 2.5 0 0 0-2.5-2.5H5'), DOT(16.5, 14, 1.3)],
    'data_usage': [P('M13 3.1a9 9 0 1 1-10 10'), P('M3.1 11A9 9 0 0 1 11 3.1V11H3.1Z')],
    'devices': [RR(2.5, 5, 13.5, 10, 2), L(6, 19, 12.5, 19), L(9.3, 15, 9.3, 19), RR(17.5, 9, 4.5, 10, 1.5)],
}

# Telegram X drawable -> (XGram Line icon, size in dp)
MAP = {
    'baseline_search_24': 'search', 'baseline_more_vert_24': 'more_vert', 'baseline_more_horiz_24': 'more_horiz',
    'baseline_close_24': 'close', 'baseline_close_18': ('close', 18), 'baseline_check_24': 'check', 'baseline_add_24': 'add',
    'baseline_arrow_forward_24': 'arrow_forward', 'baseline_arrow_upward_24': 'arrow_upward', 'baseline_direction_arrow_down_24': 'arrow_down',
    'baseline_edit_24': 'edit', 'baseline_create_24': 'edit', 'baseline_delete_24': 'delete', 'baseline_delete_forever_24': 'delete',
    'baseline_delete_sweep_24': 'delete', 'baseline_content_copy_24': 'copy', 'baseline_reply_24': 'reply', 'baseline_forward_24': 'forward',
    'baseline_share_24': 'share', 'baseline_share_arrow_24': 'forward', 'deproko_baseline_pin_24': 'pin', 'deproko_baseline_pin_undo_24': 'unpin',
    'baseline_info_24': 'info', 'baseline_warning_24': 'warning', 'baseline_error_24': 'error', 'baseline_cancel_24': 'cancel',
    'baseline_remove_circle_24': 'remove_circle', 'baseline_check_circle_24': 'check_circle', 'baseline_block_24': 'block',
    'baseline_help_24': 'help', 'baseline_help_outline_24': 'help', 'baseline_link_24': 'link', 'baseline_visibility_24': 'visibility',
    'baseline_eye_off_24': 'eye_off', 'baseline_open_in_browser_24': 'open_in_browser',
    'baseline_person_24': 'person', 'baseline_person_add_24': 'person_add', 'baseline_group_24': 'group',
    'baseline_perm_contact_calendar_24': 'contacts', 'baseline_chat_bubble_24': 'chat_bubble', 'baseline_mark_chat_unread_24': 'chat_unread',
    'baseline_forum_24': 'forum', 'outline_forum_24': 'forum', 'baseline_bullhorn_24': 'bullhorn', 'deproko_baseline_bots_24': 'bots',
    'baseline_phone_24': 'phone', 'baseline_call_24': 'phone', 'baseline_videocam_24': 'videocam', 'baseline_lock_24': 'lock',
    'baseline_lock_16': ('lock', 16), 'baseline_security_24': 'security', 'baseline_ghost_24': 'ghost', 'baseline_bookmark_24': 'bookmark',
    'baseline_star_24': 'star', 'baseline_star_border_24': 'star', 'baseline_stars_24': 'stars', 'baseline_favorite_24': 'favorite',
    'baseline_whatshot_24': 'whatshot', 'baseline_notifications_24': 'notifications', 'baseline_notifications_off_24': 'notifications_off',
    'deproko_baseline_notifications_off_24': 'notifications_off', 'baseline_notifications_active_24': 'notifications_active',
    'baseline_access_time_24': 'time', 'baseline_schedule_24': 'time', 'baseline_watch_later_24': 'time', 'baseline_history_24': 'history',
    'baseline_date_range_24': 'calendar', 'baseline_brightness_2_24': 'night',
    'baseline_image_24': 'image', 'baseline_camera_alt_24': 'camera', 'deproko_baseline_camera_26': ('camera', 26),
    'baseline_insert_drive_file_24': 'file', 'baseline_folder_24': 'folder', 'baseline_archive_24': 'archive',
    'baseline_unarchive_24': 'unarchive', 'baseline_file_download_24': 'download', 'deproko_baseline_attach_26': ('attach', 26),
    'baseline_mic_24': 'mic', 'baseline_music_note_24': 'music_note', 'baseline_volume_up_24': 'volume_up',
    'deproko_baseline_stickers_24': 'stickers', 'deproko_baseline_insert_sticker_24': 'stickers', 'deproko_baseline_insert_sticker_26': ('stickers', 26),
    'baseline_emoticon_outline_24': 'emoticon', 'baseline_emoticon_24': 'emoticon', 'deproko_baseline_insert_emoticon_26': ('emoticon', 26),
    'deproko_baseline_send_24': 'send', 'baseline_send_24': 'send', 'baseline_keyboard_24': 'keyboard', 'baseline_backspace_24': 'backspace',
    'baseline_settings_24': 'settings', 'baseline_palette_24': 'palette', 'baseline_language_24': 'language', 'baseline_public_24': 'language',
    'baseline_translate_24': 'translate', 'baseline_code_24': 'code', 'baseline_alternate_email_24': 'alternate_email', 'baseline_tune_24': 'tune',
    'baseline_import_export_24': 'import_export', 'baseline_report_24': 'report', 'baseline_poll_24': 'poll', 'baseline_bar_chart_24': 'poll',
    'baseline_repeat_24': 'repeat', 'baseline_sync_24': 'sync', 'baseline_done_all_24': 'done_all',
    'baseline_account_balance_wallet_24': 'wallet', 'baseline_account_balance_wallet_20': ('wallet', 20),
    'baseline_location_on_24': 'location', 'baseline_location_on_20': ('location', 20), 'baseline_star_20': ('star', 20),
    'baseline_favorite_20': ('favorite', 20), 'baseline_data_usage_24': 'data_usage', 'baseline_devices_other_24': 'devices',
}


# Icons each pack's card shows in the picker, with the Telegram X icon they replace.
PICKER = ['search', 'chat_bubble', 'phone', 'settings', 'bookmark', 'edit', 'delete', 'send', 'notifications', 'person']
PREVIEW_TGX = ['baseline_search_24', 'baseline_chat_bubble_24', 'baseline_phone_24', 'baseline_settings_24', 'baseline_bookmark_24',
               'baseline_edit_24', 'baseline_delete_24', 'deproko_baseline_send_24', 'baseline_notifications_24', 'baseline_person_24']

# Styles: one set of shapes, four packs. Resource prefix, stroke width, caps and joins, rounded-rect radius factor,
# dot radius factor, and the fill alpha of closed shapes (duotone).
STYLES = [
    dict(key='line', table='LINE', prefix='xgl', title='XGram Line', width=2.0, cap='round', join='round', rr=1.0, dot=1.0, duo=0),
    dict(key='bold', table='BOLD', prefix='xgb', title='XGram Bold', width=2.7, cap='round', join='round', rr=1.0, dot=1.2, duo=0),
    dict(key='duo', table='DUO', prefix='xgd', title='XGram Duotone', width=2.0, cap='round', join='round', rr=1.0, dot=1.0, duo=0.3),
    dict(key='sharp', table='SHARP', prefix='xgs', title='XGram Sharp', width=2.0, cap='square', join='miter', rr=0.25, dot=1.0, duo=0),
]


def resolve(elements, st):
    """-> (stroke path data, fill path data, closed-shape path data)"""
    strokes, fills, closed = [], [], []
    for kind, v in elements:
        if kind == 'c':
            d = circle_d(*v)
            strokes.append(d)
            closed.append(d)
        elif kind == 'rr':
            x, y, w, h, r = v
            d = rr_d(x, y, w, h, r * st['rr'])
            strokes.append(d)
            closed.append(d)
        elif kind == 'dot':
            cx, cy, r = v
            fills.append(circle_d(cx, cy, r * st['dot']))
        elif kind == 'f':
            fills.append(v)
        else:
            strokes.append(v)
            if v.rstrip().endswith('Z'):
                closed.append(v)
    return ''.join(strokes), ''.join(fills), ''.join(closed)


def vector_xml(elements, size, st):
    strokes, fills, closed = resolve(elements, st)
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- %s icon, generated by tools/xgram/icons_line.py -->' % st['title'],
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '  android:width="%ddp"' % size, '  android:height="%ddp"' % size,
             '  android:viewportWidth="24"', '  android:viewportHeight="24">']
    if st['duo'] and closed:
        lines += ['  <path', '    android:pathData="%s"' % closed, '    android:fillColor="#FF000000"',
                  '    android:fillAlpha="%s"' % f(st['duo']), '    android:fillType="evenOdd" />']
    if strokes:
        lines += ['  <path', '    android:pathData="%s"' % strokes, '    android:fillColor="#00000000"',
                  '    android:strokeColor="#FF000000"', '    android:strokeWidth="%s"' % f(st['width']),
                  '    android:strokeLineCap="%s"' % st['cap'], '    android:strokeLineJoin="%s"' % st['join'],
                  '    android:strokeMiterLimit="4" />']
    if fills:
        lines += ['  <path', '    android:pathData="%s"' % fills, '    android:fillColor="#FF000000" />']
    lines.append('</vector>')
    return '\n'.join(lines) + '\n'


def svg(elements, st, px=40):
    strokes, fills, closed = resolve(elements, st)
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="%d" height="%d">' % (px, px)]
    if st['duo'] and closed:
        out.append('<path d="%s" fill="currentColor" fill-opacity="%s" fill-rule="evenodd"/>' % (closed, f(st['duo'])))
    if strokes:
        out.append('<path d="%s" fill="none" stroke="currentColor" stroke-width="%s" stroke-linecap="%s" stroke-linejoin="%s" stroke-miterlimit="4"/>' % (
            strokes, f(st['width']), st['cap'], st['join']))
    if fills:
        out.append('<path d="%s" fill="currentColor"/>' % fills)
    out.append('</svg>')
    return ''.join(out)


def original_svg(name, px=40):
    p = os.path.join(RES, name + '.xml')
    if not os.path.exists(p):
        return '<span class="missing">?</span>'
    s = open(p, encoding='utf-8').read()
    vw = re.search(r'viewportWidth="([\d.]+)"', s)
    vh = re.search(r'viewportHeight="([\d.]+)"', s)
    paths = re.findall(r'pathData="([^"]+)"', s)
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" width="%d" height="%d">%s</svg>' % (
        vw.group(1) if vw else 24, vh.group(1) if vh else 24, px, px, ''.join('<path d="%s" fill="currentColor"/>' % d for d in paths))


def write(path, text):
    data = text.encode('utf-8')  # encode first: a failure must not leave an emptied file
    with open(path, 'wb') as fh:
        fh.write(data)


def main():
    written = set()
    tables = {}
    for st in STYLES:
        entries = []
        for tgx, target in sorted(MAP.items()):
            icon, size = (target, 24) if isinstance(target, str) else target
            assert icon in ICONS, icon
            assert os.path.exists(os.path.join(RES, tgx + '.xml')), 'no Telegram X drawable ' + tgx
            res = '%s_%s' % (st['prefix'], icon) if size == 24 else '%s_%s_%d' % (st['prefix'], icon, size)
            if res not in written:
                write(os.path.join(RES, res + '.xml'), vector_xml(ICONS[icon], size, st))
                written.add(res)
            entries.append((tgx, res))
        tables[st['table']] = entries
    prefixes = tuple(st['prefix'] + '_' for st in STYLES)
    for fn in os.listdir(RES):  # stale icons from earlier runs
        if fn.startswith(prefixes) and fn[:-4] not in written:
            os.remove(os.path.join(RES, fn))

    java = ['/*', ' * This file is a part of XGram, a fork of Telegram X', ' *',
            ' * Generated by tools/xgram/icons_line.py, do not edit.', ' */',
            'package com.fahadali.xgram.appearance;', '', 'import org.thunderdog.challegram.R;', '',
            '/** XGram icon packs: pairs of a Telegram X icon and the icon that replaces it. */',
            'final class XgramIconPackTable {', '  private XgramIconPackTable () { }']
    java += ['', '  /** Picker previews: Telegram X, then each pack in the order of {@code XgramIconPack.PACKS}. */',
             '  static final int[] PREVIEW_TGX = {%s};' % ', '.join('R.drawable.' + n for n in PREVIEW_TGX)]
    for st in STYLES:
        java.append('  static final int[] PREVIEW_%s = {%s};' % (st['table'], ', '.join('R.drawable.%s_%s' % (st['prefix'], n) for n in PICKER)))
    for st in STYLES:
        java += ['', '  /** %s */' % st['title'], '  static final int[] %s = {' % st['table']]
        for tgx, res in tables[st['table']]:
            java.append('    R.drawable.%s, R.drawable.%s,' % (tgx, res))
        java.append('  };')
    java += ['}', '']
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    write(JAVA, '\n'.join(java))

    def cells():
        out = []
        for name, elements in ICONS.items():
            replaces = [t for t, v in MAP.items() if (v if isinstance(v, str) else v[0]) == name]
            orig = original_svg(replaces[0]) if replaces else ''
            row = '<div class="old">%s</div>' % orig + ''.join('<div class="new">%s</div>' % svg(elements, st) for st in STYLES)
            out.append('<div class="cell"><div class="row">%s</div><div class="name">%s</div></div>' % (row, name))
        return ''.join(out)
    head = ''.join('<span>%s</span>' % t for t in ['Telegram X'] + [st['title'] for st in STYLES])
    html = '''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>XGram icon packs</title><style>
body{margin:0;padding:16px;font:13px system-ui,sans-serif;background:#fff;color:#1d2733}
h1{font-size:18px}.legend{display:flex;gap:18px;margin-bottom:10px;color:#56616b}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:8px}
.cell{border:1px solid #e3e7ec;border-radius:12px;padding:8px;text-align:center}
.row{display:flex;justify-content:space-around;align-items:center}.old{color:#9aa4ad}.new{color:#2b5278}
.name{margin-top:4px;color:#56616b}.dark{background:#17212b;color:#e8edf2}.dark .cell{border-color:#2b3947}
.dark .new{color:#8fc1ff}
</style></head><body><h1>XGram icon packs: %d icons each, %d Telegram X icons replaced</h1>
<div class="legend">%s</div><div class="grid">%s</div>
<div class="dark" style="margin-top:16px;padding:16px;border-radius:12px"><div class="grid">%s</div></div></body></html>''' % (
        len(ICONS), len(MAP), head, cells(), cells())
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    write(PREVIEW, html)
    print('styles', len(STYLES), 'drawables', len(written), 'mapped', len(MAP))


if __name__ == '__main__':
    main()
