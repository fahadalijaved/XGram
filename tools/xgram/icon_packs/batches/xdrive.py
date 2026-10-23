# XGram Line batch "xdrive": XDrive (Saved Messages as a drive): its cloud, the folder icons the organizer gives
# (by topic or by the AI's folder name) and Organize with AI's sparkles. Also the same cloud struck through, Offline in
# the connection pill.
#
# Reused shapes: flight is the emoji batch's airplane, receipt_long the lists batch's receipt, restaurant the emoji
# batch's restaurant_menu, lightbulb its lamp. The cloud is media2's (three circles on one flat line), on its own and
# centred.
import math

from dsl import *


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
    """the cloud of the media2 batch: three circles standing on one flat bottom line (keep the two in step)"""
    a = min(_meet(left[:2], left[2], main[:2], main[2]), key=lambda p: p[1])
    b = min(_meet(main[:2], main[2], right[:2], right[2]), key=lambda p: p[1])
    p0 = (left[0], left[1] + left[2])
    p3 = (right[0], right[1] + right[2])
    return 'M%s %s%s%s%sZ' % (f(p0[0]), f(p0[1]), _arc(left, p0, a), _arc(main, a, b), _arc(right, b, p3))


def _sparkle(cx, cy, r, k=0.55):
    """a four-pointed sparkle around (cx, cy), points r away, sides curving in"""
    q = r * (1 - k)
    return ('M%s %sC%s %s %s %s %s %sC%s %s %s %s %s %sC%s %s %s %s %s %sC%s %s %s %s %s %sZ' % (
        f(cx), f(cy - r),
        f(cx), f(cy - q), f(cx + q), f(cy), f(cx + r), f(cy),
        f(cx + q), f(cy), f(cx), f(cy + q), f(cx), f(cy + r),
        f(cx), f(cy + q), f(cx - q), f(cy), f(cx - r), f(cy),
        f(cx - q), f(cy), f(cx), f(cy - q), f(cx), f(cy - r)))


ICONS = {
    'cloud': [P(_cloud())],
    # the connection pill's Offline (also the chat list title and the chat header): the cloud struck through
    'cloud_off': [P(_cloud()), SLASH()],
    # a page with its corner folded and two lines of text
    'description': [P('M13.8 3H7.5A2.5 2.5 0 0 0 5 5.5v13A2.5 2.5 0 0 0 7.5 21h9a2.5 2.5 0 0 0 2.5-2.5V8.2Z'),
                    P('M13.8 3v3.7a1.5 1.5 0 0 0 1.5 1.5H19'), L(8.8, 12.8, 15.2, 12.8), L(8.8, 16.5, 13, 16.5)],
    # a page of text in a rounded square: headline and lines
    'article': [RR(3.5, 3.5, 17, 17, 3.5), L(7.8, 8.6, 16.2, 8.6), L(7.8, 12, 16.2, 12), L(7.8, 15.4, 12.8, 15.4)],
    'shopping_bag': [P('M5.3 8h13.4l-.9 11.3a2.4 2.4 0 0 1-2.4 2.2H8.6a2.4 2.4 0 0 1-2.4-2.2Z'), P('M9 10.5V7a3 3 0 0 1 6 0v3.5')],
    # a clapperboard: the board, its striped top band
    'movie': [RR(3.5, 4.5, 17, 15, 2.5), L(3.5, 9.5, 20.5, 9.5), L(8.6, 4.9, 10.6, 9.1), L(13.6, 4.9, 15.6, 9.1)],
    # a dumbbell
    'fitness_center': [L(8, 12, 16, 12), RR(5, 7, 3, 10, 1.4), RR(16, 7, 3, 10, 1.4), L(2.5, 10.2, 2.5, 13.8), L(21.5, 10.2, 21.5, 13.8)],
    # a banknote with its coin, a second note behind
    'payments': [RR(2.5, 5, 15.5, 10.5, 2.2), C(10.25, 10.25, 2.4), P('M21.5 8.5v7.8a2.7 2.7 0 0 1-2.7 2.7H6.5')],
    # list order: three lines, shorter down
    'sort': [L(4, 7, 20, 7), L(4, 12, 15.5, 12), L(4, 17, 11, 17)],
    # list view: two wide rows; grid view: four tiles
    'view_list': [RR(3, 4.5, 18, 6, 2), RR(3, 13.5, 18, 6, 2)],
    'grid_view': [RR(3.5, 3.5, 7, 7, 2), RR(13.5, 3.5, 7, 7, 2), RR(3.5, 13.5, 7, 7, 2), RR(13.5, 13.5, 7, 7, 2)],
    # Organize with AI: a big sparkle and a small one
    'auto_awesome': [P(_sparkle(9.8, 13.6, 6.9)), P(_sparkle(17.6, 6.4, 3.6))],
}

MAP = {
    'baseline_cloud_24': 'cloud',
    'xgram_cloud_off_24': 'cloud_off',
    'baseline_flight_24': 'airplane',
    'baseline_receipt_long_24': 'receipt',
    'baseline_restaurant_24': 'restaurant_menu',
    'baseline_lightbulb_24': 'lamp',
    'baseline_description_24': 'description',
    'baseline_article_24': 'article',
    'baseline_shopping_bag_24': 'shopping_bag',
    'baseline_movie_24': 'movie',
    'baseline_fitness_center_24': 'fitness_center',
    'baseline_payments_24': 'payments',
    'baseline_auto_awesome_24': 'auto_awesome',
    'baseline_sort_24': 'sort',
    'baseline_view_list_24': 'view_list',
    'baseline_grid_view_24': 'grid_view',
}
