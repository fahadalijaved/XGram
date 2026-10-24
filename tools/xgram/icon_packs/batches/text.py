# XGram Line / Duotone batch "text": text formatting, editing and clipboard icons.
import math

from dsl import *


def rpoly(pts, r, closed=True):
    """Polygon with rounded corners: r is one radius (along each edge) or one per corner. closed=False leaves it open
    (a stroked outline only, no Duotone body)."""
    n = len(pts)
    radii = r if isinstance(r, (list, tuple)) else [r] * n
    d = ''
    start = None
    for i in range(n):
        x0, y0 = pts[i - 1]
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        l1 = math.hypot(x0 - x1, y0 - y1)
        l2 = math.hypot(x2 - x1, y2 - y1)
        ax, ay = x1 + (x0 - x1) * radii[i] / l1, y1 + (y0 - y1) * radii[i] / l1
        bx, by = x1 + (x2 - x1) * radii[i] / l2, y1 + (y2 - y1) * radii[i] / l2
        if start is None:
            start = (ax, ay)
        d += ('M' if i == 0 else 'L') + '%s %s' % (f(ax), f(ay))
        d += 'Q%s %s %s %s' % (f(x1), f(y1), f(bx), f(by))
    return d + ('Z' if closed else 'L%s %s' % (f(start[0]), f(start[1])))


def rotrect(cx, cy, length, width, angle, r):
    """Rounded rectangle centred on (cx, cy), its length along `angle` degrees."""
    a = math.radians(angle)
    ux, uy = math.cos(a) * length / 2, math.sin(a) * length / 2
    wx, wy = -math.sin(a) * width / 2, math.cos(a) * width / 2
    pts = [(cx + ux + wx, cy + uy + wy), (cx + ux - wx, cy + uy - wy), (cx - ux - wx, cy - uy - wy), (cx - ux + wx, cy - uy + wy)]
    return rpoly(pts, r)


# serif T used by the text icons
def tee(x0, x1, top, bottom, tick=2.2, foot=2.5):
    cx = (x0 + x1) / 2
    out = [PL((x0, top + tick), (x0, top), (x1, top), (x1, top + tick)), L(cx, top, cx, bottom)]
    if foot:
        out.append(L(cx - foot, bottom, cx + foot, bottom))
    return out


def quote_mark(x0, top=6, w=6.5, head=6.3, tip=17.8, tail=2.8):
    """A closing quotation mark: a square head with a wedge tail running down to the left."""
    return P(rpoly([(x0, top), (x0 + w, top), (x0 + w, top + head), (x0 + w - 2.2, tip), (x0 + w - 2.2 - tail, tip),
                    (x0 + w - 3.2, top + head), (x0, top + head)], 1.0))


def eraser(cx, cy, length, width, angle=-45, band=-1.5):
    """A tilted eraser and the band across it, `band` units from its centre towards the rubbing end."""
    a = math.radians(angle)
    bx, by = cx + math.cos(a) * band, cy + math.sin(a) * band
    wx, wy = -math.sin(a) * width / 2, math.cos(a) * width / 2
    return [P(rotrect(cx, cy, length, width, angle, 1.1)), L(bx - wx, by - wy, bx + wx, by + wy)]


# paint bucket: a tipped square, its handle and the paint inside
BUCKET = [(9.5, 5.5), (16, 12), (9.5, 18.5), (3, 12)]

DROP = 'M12 3.3C9.6 6 5.5 10 5.5 14.5a6.5 6.5 0 0 0 13 0C18.5 10 14.4 6 12 3.3Z'

ICONS = {
    'format_text': tee(5, 19, 4.5, 19.5),
    'format_bold': [P('M7 4.5h5a3.5 3.5 0 0 1 0 7H7V4.5Z'), P('M7 11.5h5.8a4 4 0 0 1 0 8H7v-8Z')],
    'format_italic': [L(10, 4.5, 18, 4.5), L(6, 19.5, 14, 19.5), L(14.6, 4.5, 9.4, 19.5)],
    'format_underlined': [P('M7 4v6.5a5 5 0 0 0 10 0V4'), L(5.5, 20, 18.5, 20)],
    'strikethrough_s': [P('M16.5 7.5c-.5-1.8-2.3-3-4.5-3-2.5 0-4.5 1.4-4.5 3.5 0 4.6 9 2.9 9 8 0 2.2-2 3.5-4.5 3.5-2.3 0-4.1-1.1-4.7-3'),
                        L(4, 12, 20, 12)],
    'format_clear': tee(3.5, 15.5, 5, 18.5, foot=2) + [L(15.5, 14.5, 20.5, 19.5), L(20.5, 14.5, 15.5, 19.5)],
    'clear_formatting': [PL((3.2, 14.8), (7.5, 3.6), (11.8, 14.8)), L(4.75, 10.8, 10.25, 10.8)] + eraser(16.9, 16.4, 8.2, 4.6),
    'format_quote_close': [quote_mark(3.8), quote_mark(13.7)],
    # smaller bullets on the more_vert rhythm (5.5 / 12 / 18.5) so they don't touch each other
    'format_list_bulleted_type': [RR(3.3, 3.8, 3.4, 3.4, 0.9), P(rpoly([(5, 10.1), (7.2, 13.9), (2.8, 13.9)], 0.6)), C(5, 18.5, 1.8),
                                  L(10.5, 5.5, 21, 5.5), L(10.5, 12, 21, 12), L(10.5, 18.5, 21, 18.5)],
    'toc': [L(4, 6.5, 15.5, 6.5), L(4, 12, 15.5, 12), L(4, 17.5, 15.5, 17.5),
            DOT(19.8, 6.5, 1.25), DOT(19.8, 12, 1.25), DOT(19.8, 17.5, 1.25)],
    'text_search_variant': [L(3.5, 7.2, 8, 7.2), L(3.5, 12.2, 8, 12.2), L(3.5, 18.2, 20.5, 18.2),
                            C(14.8, 9.5, 3.8), L(17.6, 12.3, 20.5, 15.2)],
    'text_add': [RR(3.5, 3.5, 17, 17, 2.5)] + tee(8, 16, 8, 16, tick=1.6, foot=0),
    'textbox_password': [RR(2.5, 6.5, 19, 11, 2.5), DOT(7, 12, 1.25), DOT(11, 12, 1.25), L(15.5, 9.5, 15.5, 14.5)],
    'content_paste': [P('M8.5 4.5H7A2.5 2.5 0 0 0 4.5 7v11.5A2.5 2.5 0 0 0 7 21h10a2.5 2.5 0 0 0 2.5-2.5V7A2.5 2.5 0 0 0 17 4.5h-1.5V4A1.5 1.5 0 0 0 14 2.5h-4A1.5 1.5 0 0 0 8.5 4v.5Z'),
                      P('M8.5 4.5v.5A1.5 1.5 0 0 0 10 6.5h4a1.5 1.5 0 0 0 1.5-1.5v-.5')],
    'content_cut': [C(7, 17.5, 3), C(17, 17.5, 3), L(9.2, 15.4, 17.5, 4), L(14.8, 15.4, 6.5, 4)],
    'undo': [P('M19.5 16.8A7.8 7.8 0 0 0 6 11.6L4.5 13.1'), PL((4.5, 7.3), (4.5, 13.1), (10.3, 13.1))],
    'redo': [P('M4.5 16.8A7.8 7.8 0 0 1 18 11.6l1.5 1.5'), PL((19.5, 7.3), (19.5, 13.1), (13.7, 13.1))],
    'file_caption': [P('M8.5 4.5H6A2.5 2.5 0 0 0 3.5 7v10A2.5 2.5 0 0 0 6 19.5h4.5A2.5 2.5 0 0 0 13 17V9l-4.5-4.5Z'), P('M8.5 4.5V9H13'),
                     L(16, 8, 20.5, 8), L(16, 12, 20.5, 12), L(16, 16, 20.5, 16)],
    'format_color_fill': [P(rpoly(BUCKET, 1.3, closed=False)), L(9.5, 5.5, 6.8, 2.8), P(rpoly([(3.8, 12.8), (15.2, 12.8), (9.5, 18.5)], [0, 0, 1.3])),
                          P('M18.7 14.5s-2.2 2.5-2.2 4a2.2 2.2 0 0 0 4.4 0c0-1.5-2.2-4-2.2-4Z')],
    'opacity': [P(DROP), F('M5.5 14.5a6.5 6.5 0 0 0 13 0Z')],
    'outinline': [RR(10, 4, 11, 8.5, 2.5), P('M7.5 8H5.5A2.5 2.5 0 0 0 3 10.5v7A2.5 2.5 0 0 0 5.5 20h10a2.5 2.5 0 0 0 2.5-2.5V15')],
    # Select text / Select all: Material's select_all, a dashed rounded frame (corners and a dash on each side) round
    # the selected block
    'select_all': [P('M3.5 7V6A2.5 2.5 0 0 1 6 3.5h1'), P('M17 3.5h1A2.5 2.5 0 0 1 20.5 6v1'),
                   P('M20.5 17v1a2.5 2.5 0 0 1-2.5 2.5h-1'), P('M7 20.5H6A2.5 2.5 0 0 1 3.5 18v-1'),
                   L(11, 3.5, 13, 3.5), L(20.5, 11, 20.5, 13), L(11, 20.5, 13, 20.5), L(3.5, 11, 3.5, 13),
                   RR(8, 8, 8, 8, 1.5)],
}

MAP = {
    'baseline_format_text_24': 'format_text',
    'baseline_format_quote_close_16': 'format_quote_close',
    'baseline_format_quote_close_18': 'format_quote_close',
    'baseline_format_quote_close_24': 'format_quote_close',
    'mrgrigri_baseline_textbox_password_24': 'textbox_password',
    'baseline_content_paste_24': 'content_paste',
    'baseline_content_paste_18': 'content_paste',
    'baseline_format_list_bulleted_type_24': 'format_list_bulleted_type',
    'baseline_format_bold_24': 'format_bold',
    'baseline_format_italic_24': 'format_italic',
    'baseline_format_underlined_24': 'format_underlined',
    'baseline_strikethrough_s_24': 'strikethrough_s',
    'deproko_baseline_text_add_24': 'text_add',
    'deproko_baseline_outinline_24': 'outinline',
    'baseline_format_clear_24': 'format_clear',
    'baseline_undo_24': 'undo',
    'baseline_undo_18': 'undo',
    'baseline_file_caption_24': 'file_caption',
    'baseline_format_color_fill_24': 'format_color_fill',
    'baseline_toc_24': 'toc',
    'baseline_text_search_variant_24': 'text_search_variant',
    'baseline_redo_18': 'redo',
    'baseline_opacity_18': 'opacity',
    'dot_baseline_clear_formatting_24': 'clear_formatting',
    'baseline_content_cut_24': 'content_cut',
    'baseline_select_all_24': 'select_all',
}
