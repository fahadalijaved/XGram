"""Open-source icon libraries as XGram icon packs (Iconify JSON in vendor/<lib>/icons.json).

  python tools/xgram/icon_packs/library.py search <pack> <word> [<word> ...]     names containing every word
  python tools/xgram/icon_packs/library.py sheet <pack> [--from N] [--count N] [--png out.png]
        renders concept | Telegram X icon | the pack's mapped icon, from maps/<pack>.json, for checking by eye
  python tools/xgram/icon_packs/library.py check <pack>                           mapping problems

maps/<pack>.json: {"<concept>": "<base name>", a composed object (see compose(): library icons struck through,
moved, mirrored or combined with shapes from drawn.py) or null}. The base name is the library name without the pack's
style suffix (see PACKS); null keeps Telegram X's icon for that concept.
"""
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
VENDOR = os.path.join(HERE, 'vendor')
MAPS = os.path.join(HERE, 'maps')
CONCEPTS = os.path.join(HERE, 'concepts.json')
EDGE = r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'

# Pack -> library and the style suffixes tried in order (the first that exists wins).
PACKS = {
    'solar': dict(lib='solar', title='XGram Solar', prefix='xgs', table='SOLAR', suffixes=['-bold-duotone', '-bold', '-linear'],
                  credit='Solar Icons by 480 Design, CC BY 4.0'),
    'phosphor': dict(lib='ph', title='XGram Phosphor', prefix='xgf', table='PHOSPHOR', suffixes=['-bold', ''],
                     credit='Phosphor Icons, MIT'),
    'mingcute': dict(lib='mingcute', title='XGram MingCute', prefix='xgm', table='MINGCUTE', suffixes=['-line', '-fill', ''],
                     credit='MingCute Design, Apache 2.0'),
    'pixel': dict(lib='pixelarticons', title='XGram Pixel', prefix='xgp', table='PIXEL', suffixes=[''],
                  credit='Pixelarticons by Gerrit Halfmann, MIT'),
}
UNSUPPORTED = ('<mask', '<clipPath', '<use', 'Gradient', 'transform=', '<image', '<text', '<pattern', '<filter')

_cache = {}


def library(lib):
    if lib not in _cache:
        d = json.load(open(os.path.join(VENDOR, lib, 'icons.json'), encoding='utf-8'))
        icons = {}
        w0, h0 = d.get('width', 16), d.get('height', 16)
        for name, v in d['icons'].items():
            icons[name] = (v['body'], v.get('width', w0), v.get('height', h0))
        for name, a in d.get('aliases', {}).items():
            parent = icons.get(a.get('parent'))
            if parent and not any(k in a for k in ('rotate', 'hFlip', 'vFlip')):
                icons[name] = parent
        _cache[lib] = icons
    return _cache[lib]


def supported(body):
    return not any(u in body for u in UNSUPPORTED)


def resolve(pack, base):
    """Full library name for a mapped base name, or None."""
    p = PACKS[pack]
    icons = library(p['lib'])
    for suffix in p['suffixes']:
        name = base + suffix
        if name in icons and supported(icons[name][0]):
            return name
    return None


# -- Composed icons -----------------------------------------------------------------------------------------------------
# A map entry may be an object instead of a base name, for concepts the library has no icon for:
#   {"icon": "pin", "slash": true}                    the library's pin struck through in the pack's own manner
#   {"drawn": "gif"}                                  a shape drawn for the pack in drawn.py (pack style, pack grid)
#   {"parts": [{"icon": "bolt", "scale": 0.8, "dx": -2, "dy": 2}, {"drawn": "letter_a"}]}   several parts
# Part options: "keep": [layer indexes], "scale", "dx", "dy" (moves, after scaling about the centre), "mirror": true
# (left-right), "rotate": degrees (clockwise, about the centre). Top level: "slash": true | "down" | "up".
# Pixel works on its 24x24 grid instead (pixelgrid.py): "dx"/"dy" whole cells, "rotate" in quarter turns, no "scale".

# The 'off' slash of each pack, from its own -off/-slash icons: end points, stroke width, clear gap each side.
SLASHES = {
    'solar': dict(down=((2.25, 2.25), (21.75, 21.75)), up=((2.25, 21.75), (21.75, 2.25)), dir='down', width=1.5, gap=1.2),
    'mingcute': dict(down=((4.22, 4.22), (19.78, 19.78)), up=((4.22, 19.78), (19.78, 4.22)), dir='down', width=2, gap=1.4),
    'phosphor': dict(down=((48, 40), (208, 216)), up=((48, 216), (208, 40)), dir='down', width=24, gap=14),
}


def _drawn(pack, name):
    import drawn
    return drawn.DRAWN[pack][name]


def _half_plane(poly, a, b, c):
    """The part of a convex polygon where a*x + b*y >= c (Sutherland-Hodgman)."""
    out = []
    for i, p in enumerate(poly):
        q = poly[(i + 1) % len(poly)]
        fp, fq = a * p[0] + b * p[1] - c, a * q[0] + b * q[1] - c
        if fp >= 0:
            out.append(p)
        if (fp >= 0) != (fq >= 0):
            t = fp / (fp - fq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out


def _poly(points):
    return 'M' + 'L'.join('%g %g' % (round(x, 3), round(y, 3)) for x, y in points) + 'Z'


def _slash(pack, lys, viewport, direction):
    s = SLASHES[pack]
    (x1, y1), (x2, y2) = s['up' if direction == 'up' else 'down']
    dx, dy = x2 - x1, y2 - y1
    ln = (dx * dx + dy * dy) ** 0.5
    nx, ny = -dy / ln, dx / ln
    c = nx * x1 + ny * y1
    half = s['width'] / 2 + s['gap']
    w, h = viewport
    rect = [(0, 0), (w, 0), (w, h), (0, h)]
    # the clip is the canvas minus a band along the slash: two convex pieces, no holes, so no fill rule is involved
    clip = _poly(_half_plane(rect, nx, ny, c + half)) + _poly(_half_plane(rect, -nx, -ny, -(c - half)))
    line = 'M%g %gL%g %g' % (x1, y1, x2, y2)
    return [dict(kind='group', clip=clip, children=lys),
            dict(kind='stroke', d=line, alpha=1.0, width=s['width'], cap='round', join='round', miter=4)]


def _part_body(pack, part):
    if 'icon' in part:
        full = resolve(pack, part['icon'])
        if not full:
            raise SystemExit('%s: compose part %r has no usable icon' % (pack, part['icon']))
        return library(PACKS[pack]['lib'])[full]
    body = _drawn(pack, part['drawn'])
    lib = library(PACKS[pack]['lib'])
    w = next(iter(lib.values()))[1]
    return body, w, w


def _compose_pixel(spec):
    import pixelgrid as pg
    import drawn
    grid = pg.empty()
    for part in spec.get('parts') or [spec]:
        if 'icon' in part:
            g = pg.from_layers(layers(library('pixelarticons')[resolve('pixel', part['icon'])][0]))
        else:
            g = pg.rows(drawn.PIXEL[part['drawn']])
        if part.get('mirror'):
            g = pg.mirror(g)
        for _ in range(int(part.get('rotate', 0)) // 90 % 4):
            g = pg.rotate_cw(g)
        if part.get('dx') or part.get('dy'):
            g = pg.shift(g, int(part.get('dx', 0)), int(part.get('dy', 0)))
        grid = pg.union(grid, g)
    if spec.get('slash'):
        grid = pg.slashed(grid)
    return [dict(kind='fill', d=pg.to_path(grid), alpha=1.0, even_odd=False)], (24, 24)


def compose(pack, spec):
    """-> (layers, viewport) of a composed map entry."""
    if pack == 'pixel':
        return _compose_pixel(spec)
    out, viewport = [], None
    for part in spec.get('parts') or [spec]:
        body, w, h = _part_body(pack, part)
        viewport = viewport or (w, h)
        lys = layers(body)
        if 'keep' in part:
            lys = [l for i, l in enumerate(lys) if i in part['keep']]
        t = {k: part[k] for k in ('scale', 'dx', 'dy', 'mirror', 'rotate') if part.get(k)}
        if t:
            lys = [dict(kind='group', transform=dict(t, pivot=(w / 2, h / 2)), children=lys)]
        out += lys
    if spec.get('slash'):
        out = _slash(pack, out, viewport, spec['slash'] if spec['slash'] in ('up', 'down') else SLASHES[pack]['dir'])
    return out, viewport


def icon(pack, value):
    """(layers, viewport) of a map entry (base name or composed object), or None when it keeps Telegram X's icon."""
    if not value:
        return None
    if isinstance(value, dict):
        return compose(pack, value)
    full = resolve(pack, value)
    if not full:
        return None
    body, w, h = library(PACKS[pack]['lib'])[full]
    return layers(body), (w, h)


def label(value):
    return value if isinstance(value, str) or value is None else json.dumps(value, separators=(',', ':'))


# -- SVG body -> vector layers ------------------------------------------------------------------------------------------

def _num(v, default=0.0):
    try:
        return float(re.sub(r'[a-z%]+$', '', str(v)))
    except (TypeError, ValueError):
        return default


def _shape_d(el):
    tag = el.tag.split('}')[-1]
    g = el.attrib.get
    if tag == 'path':
        return g('d')
    if tag == 'circle':
        cx, cy, r = _num(g('cx')), _num(g('cy')), _num(g('r'))
        return 'M%g %ga%g %g 0 1 0 %g 0a%g %g 0 1 0 %g 0Z' % (cx - r, cy, r, r, 2 * r, r, r, -2 * r)
    if tag == 'ellipse':
        cx, cy, rx, ry = _num(g('cx')), _num(g('cy')), _num(g('rx')), _num(g('ry'))
        return 'M%g %ga%g %g 0 1 0 %g 0a%g %g 0 1 0 %g 0Z' % (cx - rx, cy, rx, ry, 2 * rx, rx, ry, -2 * rx)
    if tag == 'rect':
        x, y, w, h = _num(g('x')), _num(g('y')), _num(g('width')), _num(g('height'))
        rx = _num(g('rx'), _num(g('ry'), 0))
        ry = _num(g('ry'), rx)
        if rx <= 0:
            return 'M%g %gh%gv%gh%gZ' % (x, y, w, h, -w)
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        return ('M%g %gh%ga%g %g 0 0 1 %g %gv%ga%g %g 0 0 1 %g %gh%ga%g %g 0 0 1 %g %gv%ga%g %g 0 0 1 %g %gZ' %
                (x + rx, y, w - 2 * rx, rx, ry, rx, ry, h - 2 * ry, rx, ry, -rx, ry, -(w - 2 * rx), rx, ry, -rx, -ry, -(h - 2 * ry), rx, ry, rx, -ry))
    if tag == 'line':
        return 'M%g %gL%g %g' % (_num(g('x1')), _num(g('y1')), _num(g('x2')), _num(g('y2')))
    if tag in ('polyline', 'polygon'):
        nums = [float(n) for n in re.findall(r'[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?', g('points', ''))]
        pts = list(zip(nums[0::2], nums[1::2]))
        if not pts:
            return None
        return 'M' + 'L'.join('%g %g' % p for p in pts) + ('Z' if tag == 'polygon' else '')
    return None


def _style(el):
    out = dict(el.attrib)
    for part in el.attrib.get('style', '').split(';'):
        if ':' in part:
            k, v = part.split(':', 1)
            out[k.strip()] = v.strip()
    return out


def layers(body):
    root = ET.fromstring('<svg xmlns="http://www.w3.org/2000/svg">%s</svg>' % body)
    out = []

    def walk(el, inherited):
        st = dict(inherited)
        a = _style(el)
        for k in ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'stroke-miterlimit', 'fill-rule'):
            if k in a:
                st[k] = a[k]
        opacity = st.get('_opacity', 1.0) * _num(a.get('opacity', 1), 1)
        st['_opacity'] = opacity
        tag = el.tag.split('}')[-1]
        if tag in ('svg', 'g'):
            for child in el:
                walk(child, st)
            return
        if tag in ('defs', 'title', 'desc'):
            return
        d = _shape_d(el)
        if not d:
            return
        fill = st.get('fill', 'currentColor')
        stroke = st.get('stroke', 'none')
        fill_op = opacity * _num(a.get('fill-opacity', 1), 1)
        stroke_op = opacity * _num(a.get('stroke-opacity', 1), 1)
        if fill not in ('none', 'transparent'):
            out.append(dict(kind='fill', d=d, alpha=fill_op, even_odd=st.get('fill-rule') == 'evenodd'))
        if stroke not in ('none', 'transparent'):
            out.append(dict(kind='stroke', d=d, alpha=stroke_op, width=_num(st.get('stroke-width', 1), 1),
                            cap=st.get('stroke-linecap', 'butt'), join=st.get('stroke-linejoin', 'miter'),
                            miter=_num(st.get('stroke-miterlimit', 4), 4)))

    walk(root, {})
    return out


def vector_xml(lys, viewport, size, color, title):
    vw, vh = viewport
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- %s icon, generated by tools/xgram/icon_packs/build.py -->' % title,
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '  android:width="%ddp"' % size, '  android:height="%ddp"' % size,
             '  android:viewportWidth="%g"' % vw, '  android:viewportHeight="%g">' % vh]
    _xml_layers(lines, lys, color, '  ')
    lines.append('</vector>')
    return '\n'.join(lines) + '\n'


def _group_attrs(t):
    """Android group attributes of a compose transform (scale and rotation about the pivot, then the move)."""
    px, py = t['pivot']
    s = t.get('scale', 1)
    a = ['android:pivotX="%g"' % px, 'android:pivotY="%g"' % py]
    if s != 1 or t.get('mirror'):
        a += ['android:scaleX="%g"' % (-s if t.get('mirror') else s), 'android:scaleY="%g"' % s]
    if t.get('rotate'):
        a.append('android:rotation="%g"' % t['rotate'])
    if t.get('dx'):
        a.append('android:translateX="%g"' % t['dx'])
    if t.get('dy'):
        a.append('android:translateY="%g"' % t['dy'])
    return a


def _xml_layers(lines, lys, color, ind):
    for l in lys:
        if l['kind'] == 'group':
            lines.append(ind + '<group' + ''.join(' ' + a for a in (_group_attrs(l['transform']) if 'transform' in l else [])) + '>')
            if 'clip' in l:
                lines.append(ind + '  <clip-path android:pathData="%s" />' % l['clip'])
            _xml_layers(lines, l['children'], color, ind + '  ')
            lines.append(ind + '</group>')
            continue
        lines += [ind + '<path', ind + '  android:pathData="%s"' % l['d']]
        if l['kind'] == 'fill':
            lines.append(ind + '  android:fillColor="%s"' % color)
            if l['alpha'] < 1:
                lines.append(ind + '  android:fillAlpha="%g"' % round(l['alpha'], 3))
            if l.get('even_odd'):
                lines.append(ind + '  android:fillType="evenOdd"')
        else:
            lines += [ind + '  android:fillColor="#00000000"', ind + '  android:strokeColor="%s"' % color,
                      ind + '  android:strokeWidth="%g"' % l['width'], ind + '  android:strokeLineCap="%s"' % l['cap'],
                      ind + '  android:strokeLineJoin="%s"' % l['join'], ind + '  android:strokeMiterLimit="%g"' % l['miter']]
            if l['alpha'] < 1:
                lines.append(ind + '  android:strokeAlpha="%g"' % round(l['alpha'], 3))
        lines[-1] += ' />'


_clip_ids = [0]


def svg_body(lys):
    """SVG markup of vector layers (for previews of composed icons)."""
    out = []
    for l in lys:
        if l['kind'] == 'group':
            inner = svg_body(l['children'])
            if 'transform' in l:
                t = l['transform']
                px, py = t['pivot']
                s = t.get('scale', 1)
                inner = '<g transform="translate(%g %g) rotate(%g) scale(%g %g) translate(%g %g)">%s</g>' % (
                    px + t.get('dx', 0), py + t.get('dy', 0), t.get('rotate', 0), -s if t.get('mirror') else s, s, -px, -py, inner)
            if 'clip' in l:
                _clip_ids[0] += 1
                cid = 'xgclip%d' % _clip_ids[0]
                inner = '<clipPath id="%s"><path d="%s"/></clipPath><g clip-path="url(#%s)">%s</g>' % (cid, l['clip'], cid, inner)
            out.append(inner)
        elif l['kind'] == 'fill':
            out.append('<path d="%s" fill="currentColor" fill-opacity="%g"%s/>' % (l['d'], l['alpha'], ' fill-rule="evenodd"' if l.get('even_odd') else ''))
        else:
            out.append('<path d="%s" fill="none" stroke="currentColor" stroke-width="%g" stroke-linecap="%s" stroke-linejoin="%s" stroke-opacity="%g"/>' % (
                l['d'], l['width'], l['cap'], l['join'], l['alpha']))
    return ''.join(out)


def svg_preview(body, w, h, px=36):
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %g %g" width="%d" height="%d" style="color:#2b5278">%s</svg>' % (w, h, px, px, body)


def icon_preview(pack, value, px=36):
    ic = icon(pack, value)
    if not ic:
        return None
    lys, (w, h) = ic
    return svg_preview(svg_body(lys), w, h, px)


# -- CLI ----------------------------------------------------------------------------------------------------------------

def _concepts():
    return json.load(open(CONCEPTS, encoding='utf-8'))


def _load_map(pack):
    p = os.path.join(MAPS, pack + '.json')
    return json.load(open(p, encoding='utf-8')) if os.path.exists(p) else {}


def _tgx_svg(name, px=36):
    p = os.path.join(HERE, '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable', name + '.xml')
    if not os.path.exists(p):
        return '?'
    s = open(p, encoding='utf-8', errors='ignore').read()
    vw = re.search(r'viewportWidth="([\d.]+)"', s)
    vh = re.search(r'viewportHeight="([\d.]+)"', s)
    paths = re.findall(r'pathData="([^"]+)"', s)
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" width="%d" height="%d" style="color:#8a949e">%s</svg>' % (
        vw.group(1) if vw else 24, vh.group(1) if vh else 24, px, px, ''.join('<path d="%s" fill="currentColor"/>' % d for d in paths))


def cmd_search(pack, words):
    icons = library(PACKS[pack]['lib'])
    words = [w.lower() for w in words]
    bases = {}
    style_suffixes = {
        'solar': ['-bold-duotone', '-line-duotone', '-bold', '-linear', '-broken', '-outline'],
        'ph': ['-bold', '-fill', '-duotone', '-light', '-thin'],
        'mingcute': ['-line', '-fill'],
        'pixelarticons': [],
    }[PACKS[pack]['lib']]
    suffixes = sorted(style_suffixes, key=len, reverse=True)
    for name in icons:
        if all(w in name for w in words):
            base = name
            for s in suffixes:
                if name.endswith(s):
                    base = name[:-len(s)]
                    break
            bases.setdefault(base, []).append(name)
    for base in sorted(bases, key=lambda b: (len(b), b))[:80]:
        full = resolve(pack, base)
        print('%-40s %s' % (base, ('-> ' + full) if full else '(no usable style)'))
    if len(bases) > 80:
        print('... %d more' % (len(bases) - 80))


def cmd_sheet(pack, start, count, png):
    concepts = _concepts()
    mapping = _load_map(pack)
    names = list(concepts)[start:start + count]
    icons = library(PACKS[pack]['lib'])
    cells = []
    for c in names:
        tgx = concepts[c]['tgx'][0]
        base = mapping.get(c)
        ours = icon_preview(pack, base)
        if ours:
            name = resolve(pack, base) if isinstance(base, str) else 'composed'
        else:
            ours = '<span style="color:#c33">none</span>'
            name = repr(base)
        cells.append('<div class="c"><div class="r">%s<span>&rarr;</span>%s</div><div class="t"><b>%s</b><br>%s</div></div>' % (_tgx_svg(tgx), ours, c, name))
    html = ('<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;padding:10px;font:11px system-ui,sans-serif}'
            '.g{display:grid;grid-template-columns:repeat(6,1fr);gap:6px}.c{border:1px solid #ddd;border-radius:8px;padding:6px;text-align:center}'
            '.r{display:flex;justify-content:center;align-items:center;gap:10px}.t{margin-top:3px;color:#444;word-break:break-all}</style></head>'
            '<body><b>%s: concepts %d-%d</b><div class="g">%s</div></body></html>') % (pack, start, start + len(names) - 1, ''.join(cells))
    out_html = os.path.join(MAPS, '_sheet_%s_%d.html' % (pack, start))
    os.makedirs(MAPS, exist_ok=True)
    with open(out_html, 'wb') as fh:
        fh.write(html.encode('utf-8'))
    png = png or out_html[:-5] + '.png'
    rows = (len(names) + 5) // 6
    prof = os.path.join(os.environ.get('TEMP', HERE), 'xgram_lib_preview_profile_' + pack)
    subprocess.run([EDGE, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--user-data-dir=' + prof,
                    '--window-size=1200,%d' % (40 + rows * 92), '--screenshot=' + png, 'file:///' + out_html.replace('\\', '/')],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
    print('sheet:', png)


def _usable(pack, value):
    try:
        return icon(pack, value) is not None
    except (KeyError, SystemExit, ValueError):
        return False


def cmd_check(pack):
    concepts = _concepts()
    mapping = _load_map(pack)
    missing = [c for c in concepts if c not in mapping]
    bad = [(c, label(b)) for c, b in mapping.items() if b and not _usable(pack, b)]
    unknown = [c for c in mapping if c not in concepts]
    nulls = [c for c, b in mapping.items() if not b]
    print('concepts %d, mapped %d, null %d, missing %d, unresolvable %d, unknown keys %d' % (
        len(concepts), len(mapping) - len(nulls), len(nulls), len(missing), len(bad), len(unknown)))
    for c in missing[:50]:
        print('missing:', c)
    for c, b in bad:
        print('unresolvable:', c, b)
    for c in unknown:
        print('unknown key:', c)


if __name__ == '__main__':
    cmd, pack = sys.argv[1], sys.argv[2]
    if pack not in PACKS:
        raise SystemExit('pack must be one of ' + ', '.join(PACKS))
    if cmd == 'search':
        cmd_search(pack, sys.argv[3:])
    elif cmd == 'sheet':
        args = sys.argv[3:]
        start = int(args[args.index('--from') + 1]) if '--from' in args else 0
        count = int(args[args.index('--count') + 1]) if '--count' in args else 60
        png = args[args.index('--png') + 1] if '--png' in args else None
        cmd_sheet(pack, start, count, png)
    elif cmd == 'check':
        cmd_check(pack)
