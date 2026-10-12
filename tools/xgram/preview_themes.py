"""Preview sheet of the generated XGram themes: header, chat list rows, wallpaper, bubbles and buttons.

Run: python preview_themes.py out.png
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'assets', 'xgram_themes')
KEYS = sorted(n[:-len('.tgx-theme')] for n in os.listdir(D) if n.endswith('.tgx-theme'))


def load(key):
    colors = {}
    sec = None
    for line in open(os.path.join(D, key + '.tgx-theme'), encoding='utf-8'):
        line = line.strip()
        if line in ('!', '@', '#'):
            sec = line
            continue
        if sec == '#' and ':' in line:
            k, v = line.split(':', 1)
            for name in k.split(','):
                colors[name.strip()] = v.strip()
        if sec == '!' and line.startswith('name:'):
            colors['__name'] = line.split(':', 1)[1].strip().strip('"')
        if sec == '!' and line.startswith('wallpaper:'):
            colors['__wallpaper'] = line.split(':', 1)[1].strip().strip('"')
    return colors


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def freeform(cols, w, h):
    yy, xx = np.mgrid[0:h, 0:w]
    acc = np.zeros((h, w, 3))
    total = np.zeros((h, w, 1))
    for (px, py), col in zip([(0, 0), (w, 0), (w, h), (0, h)], cols):
        weight = (1.0 / (np.hypot(xx - px, yy - py) + 30) ** 2)[..., None]
        acc += weight * np.array(col)
        total += weight
    return Image.fromarray((acc / total).astype('uint8'))


W, H, COLS = 200, 290, 6
rows = (len(KEYS) + COLS - 1) // COLS
sheet = Image.new('RGB', (COLS * (W + 10) + 10, rows * (H + 10) + 10), (40, 40, 40))
for i, key in enumerate(KEYS):
    c = load(key)
    g = lambda k: rgb(c.get(k, '#FF00FF'))
    im = Image.new('RGB', (W, H), g('background'))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W, 40], fill=g('headerBackground'))
    d.text((12, 14), c['__name'], fill=g('headerText'))
    for r in range(3):
        y = 44 + r * 34
        d.rectangle([0, y, W, y + 32], fill=g('filling'))
        d.ellipse([8, y + 4, 32, y + 28], fill=g('circleButtonRegular'))
        d.text((40, y + 4), 'Chat name', fill=g('text'))
        d.text((40, y + 17), 'last message', fill=g('textLight'))
        d.ellipse([W - 30, y + 9, W - 14, y + 25], fill=g('badge'))
        d.text((W - 25, y + 11), '3', fill=g('badgeText'))
    if c.get('__wallpaper'):
        im.paste(freeform([rgb(x) for x in c['__wallpaper'].split('~')], W, H - 150), (0, 150))
    else:
        d.rectangle([0, 150, W, H], fill=g('chatBackground'))
    d.rounded_rectangle([8, 160, 130, 196], 10, fill=g('bubbleIn_background'))
    d.text((16, 166), 'Hello there', fill=g('bubbleIn_text'))
    d.text((16, 180), 'link.com', fill=g('bubbleIn_textLink'))
    d.rounded_rectangle([64, 206, W - 8, 242], 10, fill=g('bubbleOut_background'))
    d.text((74, 212), 'Hi! How are you', fill=g('bubbleOut_text'))
    d.text((74, 226), '12:00', fill=g('bubbleOut_time'))
    d.ellipse([W - 44, H - 44, W - 10, H - 10], fill=g('circleButtonRegular'))
    d.text((W - 31, H - 33), '+', fill=g('circleButtonRegularIcon'))
    d.text((6, H - 14), key, fill=(255, 0, 255))
    sheet.paste(im, (10 + (i % COLS) * (W + 10), 10 + (i // COLS) * (H + 10)))
sheet.save(sys.argv[1] if len(sys.argv) > 1 else 'themes_sheet.png')
print('ok', len(KEYS))
