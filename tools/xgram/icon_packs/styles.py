"""The five XGram icon pack styles, all derived from one set of line shapes.

  line     XGram Line: rounded 1.8 px lines
  chunky   XGram Chunky: fat 2.8 px rounded lines, slightly smaller glyphs
  duotone  XGram Duotone: lines with a soft filled body
  pixel    XGram Pixel: 8-bit, the shape rasterised on a 12x12 grid
  doodle   XGram Doodle: hand-drawn, wobbly double strokes

render(elements, style, name) -> list of layers: dict(d=path data, kind='stroke'|'fill', width, cap, join, alpha, fill_type)
plus an optional group scale. Both the Android vector writer and the SVG preview use these layers.
"""
import hashlib
import math

from geometry import flatten
from dsl import core, f

STYLES = {
    'line': dict(title='XGram Line', prefix='xgl', table='LINE'),
    'chunky': dict(title='XGram Chunky', prefix='xgc', table='CHUNKY'),
    'duotone': dict(title='XGram Duotone', prefix='xgd', table='DUOTONE'),
    'pixel': dict(title='XGram Pixel', prefix='xgp', table='PIXEL'),
    'doodle': dict(title='XGram Doodle', prefix='xgh', table='DOODLE'),
}
ORDER = ['line', 'chunky', 'duotone', 'pixel', 'doodle']

_BASE = dict(width=2.0, cap='round', join='round', rr=1.0, dot=1.0, duo=0)


def _resolve(elements, **over):
    st = dict(_BASE)
    st.update(over)
    return core.resolve(elements, st)  # (strokes, fills, closed) path data


def _stroke(d, width, alpha=1.0, cap='round', join='round'):
    return dict(kind='stroke', d=d, width=width, cap=cap, join=join, alpha=alpha)


def _fill(d, alpha=1.0, even_odd=False):
    return dict(kind='fill', d=d, alpha=alpha, even_odd=even_odd)


def _poly_d(points, closed=False):
    if not points:
        return ''
    d = 'M%s %s' % (f(points[0][0]), f(points[0][1]))
    for x, y in points[1:]:
        d += 'L%s %s' % (f(x), f(y))
    return d + ('Z' if closed else '')


# -- Pixel --------------------------------------------------------------------------------------------------------------

PIXEL_GRID = 12


def _pixel(elements, name):
    from PIL import Image, ImageDraw
    scale = 24  # raster px per unit
    size = 24 * scale
    im = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(im)
    strokes, fills, _ = _resolve(elements)
    width = int(round(2.0 * scale))
    for pts, closed in flatten(strokes, step=0.25):
        p = [(x * scale, y * scale) for x, y in pts]
        draw.line(p, fill=255, width=width, joint='curve')
        if not closed:
            for x, y in (p[0], p[-1]):
                r = width / 2
                draw.ellipse((x - r, y - r, x + r, y + r), fill=255)
    for pts, closed in flatten(fills, step=0.25):
        p = [(x * scale, y * scale) for x, y in pts]
        if len(p) > 2:
            draw.polygon(p, fill=255)
    cell = size // PIXEL_GRID
    unit = 24.0 / PIXEL_GRID
    on = []
    for row in range(PIXEL_GRID):
        line = []
        for col in range(PIXEL_GRID):
            box = im.crop((col * cell, row * cell, (col + 1) * cell, (row + 1) * cell))
            hist = box.histogram()
            covered = sum(hist[128:]) / float(cell * cell)
            line.append(covered >= 0.34)
        on.append(line)
    d = ''
    for row in range(PIXEL_GRID):
        col = 0
        while col < PIXEL_GRID:
            if on[row][col]:
                start = col
                while col < PIXEL_GRID and on[row][col]:
                    col += 1
                d += 'M%s %sh%sv%sh%sZ' % (f(start * unit), f(row * unit), f((col - start) * unit), f(unit), f(-(col - start) * unit))
            else:
                col += 1
    return [_fill(d)] if d else []


# -- Doodle -------------------------------------------------------------------------------------------------------------

def _rng(name, salt):
    h = hashlib.sha1(('%s/%s' % (name, salt)).encode('utf-8')).digest()
    return [b / 255.0 for b in h]


def _resample(points, spacing):
    out = [points[0]]
    carry = 0.0
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        seg = math.hypot(x1 - x0, y1 - y0)
        if seg == 0:
            continue
        t = spacing - carry
        while t <= seg:
            out.append((x0 + (x1 - x0) * t / seg, y0 + (y1 - y0) * t / seg))
            t += spacing
        carry = seg - (t - spacing)
    if math.dist(out[-1], points[-1]) > 0.05:
        out.append(points[-1])
    return out


def _wobble(points, closed, name, salt, amplitude):
    r = _rng(name, salt)
    if closed:
        # hand-drawn loops overshoot their start a little
        extra = _resample(points, 0.5)[:4]
        points = points + extra[1:]
    pts = _resample(points, 0.55)
    if len(pts) < 2:
        return pts
    out = []
    s = 0.0
    phase1, phase2 = r[0] * 6.28, r[1] * 6.28
    l1, l2 = 5.5 + r[2] * 3, 1.9 + r[3] * 1.2
    for i, (x, y) in enumerate(pts):
        if i > 0:
            s += math.dist(pts[i - 1], pts[i])
        a = pts[max(0, i - 1)]
        b = pts[min(len(pts) - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        n = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / n, dx / n
        off = amplitude * (0.7 * math.sin(6.283 * s / l1 + phase1) + 0.3 * math.sin(6.283 * s / l2 + phase2))
        out.append((x + nx * off, y + ny * off))
    if not closed and len(out) > 2:
        # pen strokes run a touch past their ends
        (x0, y0), (x1, y1) = out[0], out[1]
        k = 0.35 / (math.dist(out[0], out[1]) or 1)
        out.insert(0, (x0 - (x1 - x0) * k, y0 - (y1 - y0) * k))
        (x0, y0), (x1, y1) = out[-1], out[-2]
        k = 0.35 / (math.dist(out[-1], out[-2]) or 1)
        out.append((x0 - (x1 - x0) * k, y0 - (y1 - y0) * k))
    return out


def _doodle(elements, name):
    strokes, fills, _ = _resolve(elements)
    main, sketch = '', ''
    for idx, (pts, closed) in enumerate(flatten(strokes, step=0.3)):
        main += _poly_d(_wobble(pts, closed, name, 'm%d' % idx, 0.26))
        sketch += _poly_d(_wobble(pts, closed, name, 's%d' % idx, 0.42))
    layers = []
    if sketch:
        layers.append(_stroke(sketch, 0.8, alpha=0.5))
    if main:
        layers.append(_stroke(main, 1.75))
    if fills:
        layers.append(_fill(fills))
    return layers


# -- Entry --------------------------------------------------------------------------------------------------------------

def render(elements, style, name):
    """-> (layers, group_scale)"""
    if style == 'line':
        strokes, fills, _ = _resolve(elements)
        return [l for l in (_stroke(strokes, 1.8) if strokes else None, _fill(fills) if fills else None) if l], 1.0
    if style == 'chunky':
        strokes, fills, _ = _resolve(elements, dot=1.35)
        return [l for l in (_stroke(strokes, 2.9) if strokes else None, _fill(fills) if fills else None) if l], 0.86
    if style == 'duotone':
        strokes, fills, closed = _resolve(elements)
        layers = []
        if closed:
            layers.append(_fill(closed, alpha=0.32, even_odd=True))
        if strokes:
            layers.append(_stroke(strokes, 1.9))
        if fills:
            layers.append(_fill(fills))
        return layers, 1.0
    if style == 'pixel':
        return _pixel(elements, name), 1.0
    if style == 'doodle':
        return _doodle(elements, name), 1.0
    raise ValueError(style)


def vector_xml(layers, scale, size, color, title):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- %s icon, generated by tools/xgram/icon_packs/build.py -->' % title,
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '  android:width="%ddp"' % size, '  android:height="%ddp"' % size,
           '  android:viewportWidth="24"', '  android:viewportHeight="24">']
    indent = '  '
    if scale != 1.0:
        out.append('  <group android:pivotX="12" android:pivotY="12" android:scaleX="%s" android:scaleY="%s">' % (f(scale), f(scale)))
        indent = '    '
    for l in layers:
        out.append(indent + '<path')
        out.append(indent + '  android:pathData="%s"' % l['d'])
        if l['kind'] == 'stroke':
            out += [indent + '  android:fillColor="#00000000"', indent + '  android:strokeColor="%s"' % color,
                    indent + '  android:strokeWidth="%s"' % f(l['width']), indent + '  android:strokeLineCap="%s"' % l['cap'],
                    indent + '  android:strokeLineJoin="%s"' % l['join']]
            if l['alpha'] != 1.0:
                out.append(indent + '  android:strokeAlpha="%s"' % f(l['alpha']))
        else:
            out.append(indent + '  android:fillColor="%s"' % color)
            if l['alpha'] != 1.0:
                out.append(indent + '  android:fillAlpha="%s"' % f(l['alpha']))
            if l.get('even_odd'):
                out.append(indent + '  android:fillType="evenOdd"')
        out[-1] += ' />'
    if scale != 1.0:
        out.append('  </group>')
    out.append('</vector>')
    return '\n'.join(out) + '\n'


def svg(layers, scale, px=40):
    body = []
    for l in layers:
        if l['kind'] == 'stroke':
            body.append('<path d="%s" fill="none" stroke="currentColor" stroke-width="%s" stroke-linecap="%s" stroke-linejoin="%s" stroke-opacity="%s"/>' % (
                l['d'], f(l['width']), l['cap'], l['join'], f(l['alpha'])))
        else:
            body.append('<path d="%s" fill="currentColor" fill-opacity="%s"%s/>' % (
                l['d'], f(l['alpha']), ' fill-rule="evenodd"' if l.get('even_odd') else ''))
    g = ''.join(body)
    if scale != 1.0:
        g = '<g transform="translate(12 12) scale(%s) translate(-12 -12)">%s</g>' % (f(scale), g)
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="%d" height="%d">%s</svg>' % (px, px, g)
