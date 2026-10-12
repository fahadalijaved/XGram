"""Builds XGram's icon packs.

  XGram Line      XGram's own drawings: tools/xgram/icons_line.py (core) + tools/xgram/icon_packs/batches/*.py
  XGram Solar     Solar Icons by 480 Design (CC BY 4.0), bold duotone
  XGram Phosphor  Phosphor Icons (MIT), bold
  XGram MingCute  MingCute (Apache 2.0), line
  XGram Pixel     Pixelarticons by Gerrit Halfmann (MIT)

Library packs follow maps/<pack>.json ({concept: library base name or null}); concepts.json lists the Telegram X
drawables of every concept. Every size/colour variant the app references (baseline_group_16, ..._white) gets its own
drawable with the original's size and colour.

  python tools/xgram/icon_packs/build.py                  # drawables, Java table, docs/xgram/icons/preview.html
  python tools/xgram/icon_packs/build.py --batch b01      # preview one XGram Line batch (no files written)
"""
import argparse
import glob
import importlib.util
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import library  # noqa: E402
import styles  # noqa: E402
from dsl import core  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
MAIN = os.path.join(ROOT, 'app', 'src', 'main')
RES = os.path.join(MAIN, 'res', 'drawable')
JAVA = os.path.join(MAIN, 'java', 'com', 'fahadali', 'xgram', 'appearance', 'XgramIconPackTable.java')
PREVIEW = os.path.join(ROOT, 'docs', 'xgram', 'icons', 'preview.html')
EDGE = r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
LINE = dict(title='XGram Line', prefix='xgl', table='LINE', style='line')
DUOTONE = dict(title='XGram Duotone', prefix='xgd', table='DUOTONE', style='duotone')
OWN = [LINE, DUOTONE]  # packs drawn from XGram's own shapes
# every prefix XGram ever generated (old ones are cleaned up)
OUR_PREFIXES = ('xgl_', 'xgb_', 'xgd_', 'xgs_', 'xgc_', 'xgp_', 'xgh_', 'xgf_', 'xgm_')

AUTHORS = ('deproko_', 'templarian_', 'dotvhs_', 'dot_', 'ivanliana_', 'mrgrigri_', 'andrejsharapov_', 'belledeboheme_',
           'infanf_', 'nilsfast_', 'xt3000_', 'itsspelledhaley_', 'vkryl_', 'round_', 'outline_')
# Brand logos, icons that are text, and parts of animated icons keep Telegram X's drawing.
NEVER = re.compile(r'(logo|github|telegram|tdlib|webrtc|ffmpeg|webp|device_web|device_windows|device_macbook|device_imac|'
                   r'device_iphone|playback_speed|time_30m|time_2h|time_8h|time_1y|link_preview|lock_top|lock_base|'
                   r'xgram_|premium_star|acc_anon|acc_personal|currency_bitcoin|youtube|vimeo|dailymotion|foursquare)')
# Message status marks (sent / read ticks, sending clock) are drawn to line up with the time text: every pack keeps
# Telegram X's, a library's boxed check would cover the time.
STATUS = re.compile(r'^deproko_baseline_(check_single|check_double|clock)_\d+')


def concept(name):
    n = name
    for a in AUTHORS:
        if n.startswith(a):
            n = n[len(a):]
    if n.startswith('baseline_'):
        n = n[len('baseline_'):]
    n = re.sub(r'_white$', '', n)
    n = re.sub(r'_(\d+)(_white)?$', '', n)
    return re.sub(r'_(\d+)$', '', n)


def load_module(path):
    spec = importlib.util.spec_from_file_location(os.path.splitext(os.path.basename(path))[0], path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def load_shapes(only_batch=None):
    icons, mapping, origin = {}, {}, {}
    modules = [('core', core)]
    for p in sorted(glob.glob(os.path.join(HERE, 'batches', '*.py'))):
        key = os.path.splitext(os.path.basename(p))[0]
        if key.startswith('_') or (only_batch and key != only_batch):
            continue  # a batch preview loads only its own batch
        modules.append((key, load_module(p)))
    for key, m in modules:
        if only_batch and key not in ('core', only_batch):
            continue
        for name, el in m.ICONS.items():
            if name in icons:
                raise SystemExit('icon %r defined in both %s and %s' % (name, origin[name], key))
            icons[name] = el
            origin[name] = key
        for tgx, target in m.MAP.items():
            icon = target if isinstance(target, str) else target[0]
            if tgx in mapping and mapping[tgx] != icon:
                raise SystemExit('%s mapped twice (%s, %s)' % (tgx, mapping[tgx], icon))
            mapping[tgx] = icon
    for tgx, icon in mapping.items():
        if icon not in icons:
            raise SystemExit('%s -> unknown icon %r' % (tgx, icon))
    return icons, mapping, origin


_refs = None


def referenced_drawables():
    global _refs
    if _refs is None:
        refs = set()
        for base, rx in ((os.path.join(MAIN, 'java'), r'R\.drawable\.([a-z0-9_]+)'), (os.path.join(MAIN, 'res'), r'@drawable/([a-z0-9_]+)')):
            for dp, dn, fn in os.walk(base):
                for fname in fn:
                    if fname.endswith(('.java', '.kt', '.xml')):
                        try:
                            refs.update(re.findall(rx, open(os.path.join(dp, fname), encoding='utf-8', errors='ignore').read()))
                        except OSError:
                            pass
        _refs = refs
    return _refs


def tgx_info(name):
    """(size dp, is white) of a Telegram X vector drawable, or None if it is not one."""
    p = os.path.join(RES, name + '.xml')
    if not os.path.exists(p):
        return None
    s = open(p, encoding='utf-8', errors='ignore').read()
    if '<vector' not in s:
        return None
    w = re.search(r'android:width="([\d.]+)dp"', s)
    size = int(round(float(w.group(1)))) if w else 24
    colors = [c.lower() for c in re.findall(r'android:(?:fillColor|strokeColor)="(#[0-9A-Fa-f]{3,8})"', s)]
    colors = [c for c in colors if not (len(c) == 9 and c[1:3] == '00')]
    white = bool(colors) and all(c in ('#fff', '#ffffff', '#ffffffff') for c in colors)
    return size, white


def with_variants(explicit):
    """{tgx: value} plus every referenced size/colour variant of the same concept."""
    by_concept = {}
    for tgx, value in explicit.items():
        by_concept.setdefault(concept(tgx), value)
    result = dict(explicit)
    for name in sorted(referenced_drawables()):
        if name in result or name.startswith(OUR_PREFIXES) or NEVER.search(name):
            continue
        value = by_concept.get(concept(name))
        if value is not None and tgx_info(name):
            result[name] = value
    return result


def write(path, text):
    data = text.encode('utf-8')  # encode first: a failure must not leave a truncated file
    with open(path, 'wb') as fh:
        fh.write(data)


def res_name(prefix, key, size, white):
    return '%s_%s%s%s' % (prefix, key, '' if size == 24 else '_%d' % size, '_w' if white else '')


def build_own(pack, icons, mapping, written):
    table, cache = [], {}
    for tgx in sorted(mapping):
        if STATUS.match(tgx):
            continue
        info = tgx_info(tgx)
        if not info:
            raise SystemExit('no Telegram X vector drawable ' + tgx)
        size, white = info
        icon = mapping[tgx]
        res = res_name(pack['prefix'], icon, size, white)
        if res not in written:
            if icon not in cache:
                cache[icon] = styles.render(icons[icon], pack['style'], icon)
            layers, scale = cache[icon]
            write(os.path.join(RES, res + '.xml'), styles.vector_xml(layers, scale, size, '#FFFFFFFF' if white else '#FF000000', pack['title']))
            written.add(res)
        table.append((tgx, res))
    return table


def concept_table():
    """{tgx drawable: concept} from concepts.json, with variants."""
    concepts = json.load(open(os.path.join(HERE, 'concepts.json'), encoding='utf-8'))
    explicit = {}
    for c, v in concepts.items():
        for tgx in v['tgx']:
            explicit.setdefault(tgx, c)
    return concepts, with_variants(explicit)


def build_library(pack, tgx_concept, written):
    p = library.PACKS[pack]
    map_path = os.path.join(HERE, 'maps', pack + '.json')
    mapping = json.load(open(map_path, encoding='utf-8')) if os.path.exists(map_path) else {}
    icons = library.library(p['lib'])
    table, cache, missing = [], {}, set()
    for tgx in sorted(tgx_concept):
        if STATUS.match(tgx):
            continue
        c = tgx_concept[tgx]
        base = mapping.get(c)
        full = library.resolve(pack, base) if base else None
        if not full:
            missing.add(c)
            continue
        info = tgx_info(tgx)
        if not info:
            continue
        size, white = info
        res = res_name(p['prefix'], c, size, white)
        if res not in written:
            if full not in cache:
                body, w, h = icons[full]
                cache[full] = (library.layers(body), (w, h))
            lys, viewport = cache[full]
            write(os.path.join(RES, res + '.xml'), library.vector_xml(lys, viewport, size, '#FFFFFFFF' if white else '#FF000000', p['title']))
            written.add(res)
        table.append((tgx, res))
    return table, mapping, missing


def write_java(tables, previews):
    lines = ['/*', ' * This file is a part of XGram, a fork of Telegram X', ' *',
             ' * Generated by tools/xgram/icon_packs/build.py, do not edit.', ' */',
             'package com.fahadali.xgram.appearance;', '', 'import org.thunderdog.challegram.R;', '',
             '/** XGram icon packs: pairs of a Telegram X icon and the icon that replaces it, and picker previews. */',
             'final class XgramIconPackTable {', '  private XgramIconPackTable () { }', '',
             '  static final int[] PREVIEW_TGX = {%s};' % ', '.join('R.drawable.' + n for n in core.PREVIEW_TGX)]
    for table, names in previews:
        lines.append('  static final int[] PREVIEW_%s = {%s};' % (table, ', '.join('R.drawable.' + n for n in names)))
    for table, title, pairs in tables:
        lines += ['', '  /** %s */' % title, '  static final int[] %s = {' % table]
        lines += ['    R.drawable.%s, R.drawable.%s,' % pair for pair in pairs]
        lines.append('  };')
    lines += ['}', '']
    write(JAVA, '\n'.join(lines))


def original_svg(name, px=36):
    p = os.path.join(RES, name + '.xml')
    if not os.path.exists(p):
        return '<span>?</span>'
    s = open(p, encoding='utf-8', errors='ignore').read()
    vw = re.search(r'viewportWidth="([\d.]+)"', s)
    vh = re.search(r'viewportHeight="([\d.]+)"', s)
    paths = re.findall(r'pathData="([^"]+)"', s)
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" width="%d" height="%d">%s</svg>' % (
        vw.group(1) if vw else 24, vh.group(1) if vh else 24, px, px, ''.join('<path d="%s" fill="currentColor"/>' % d for d in paths))


def preview_page(concepts, icons, line_concepts, lib_maps, title):
    packs = list(library.PACKS)
    head = ''.join('<span>%s</span>' % t for t in ['Telegram X'] + [p['title'] for p in OWN] + [library.PACKS[k]['title'] for k in packs])
    cells = []
    for c, v in concepts.items():
        row = ['<div class="o">%s</div>' % original_svg(v['tgx'][0])]
        for own in OWN:
            if c in line_concepts:
                row.append('<div class="n">%s</div>' % styles.svg(*styles.render(icons[c], own['style'], c), px=36))
            else:
                row.append('<div class="x">-</div>')
        for k in packs:
            base = lib_maps[k].get(c)
            full = library.resolve(k, base) if base else None
            if full:
                body, w, h = library.library(library.PACKS[k]['lib'])[full]
                row.append('<div class="n">%s</div>' % library.svg_preview(body, w, h))
            else:
                row.append('<div class="x">-</div>')
        cells.append('<div class="c"><div class="r">%s</div><div class="t">%s</div></div>' % (''.join(row), c))
    return '''<!doctype html><html><head><meta charset="utf-8"><title>%s</title><style>
body{margin:0;padding:14px;font:12px system-ui,sans-serif;background:#fff;color:#1d2733}h1{font-size:16px;margin:0 0 6px}
.l{display:flex;gap:16px;margin-bottom:8px;color:#56616b}.g{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:6px}
.c{border:1px solid #e3e7ec;border-radius:10px;padding:6px}.r{display:flex;justify-content:space-around;align-items:center;min-height:38px}
.o{color:#9aa4ad}.n{color:#2b5278}.x{color:#c9ced3;width:36px;text-align:center}.t{margin-top:2px;text-align:center;color:#56616b}
</style></head><body><h1>%s</h1><div class="l">%s</div><div class="g">%s</div></body></html>''' % (title, title, head, ''.join(cells))


def render_png(html_path, png_path, height):
    prof = os.path.join(os.environ.get('TEMP', HERE), 'xgram_icon_preview_profile')
    subprocess.run([EDGE, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--user-data-dir=' + prof,
                    '--window-size=1250,%d' % height, '--screenshot=' + png_path, 'file:///' + html_path.replace('\\', '/')],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
    return png_path


def batch_preview(batch, png):
    icons, mapping, origin = load_shapes(batch)
    names = [n for n in icons if origin[n] == batch]
    cells = ''.join('<div class="c"><div class="r"><div class="o">%s</div><div class="n">%s</div><div class="n">%s</div></div><div class="t">%s</div></div>' % (
        original_svg(sorted(t for t, i in mapping.items() if i == n)[0]) if any(i == n for i in mapping.values()) else '',
        styles.svg(*styles.render(icons[n], 'line', n), px=56), styles.svg(*styles.render(icons[n], 'duotone', n), px=56), n) for n in names)
    html = os.path.join(HERE, 'batches', '_preview_%s.html' % batch)
    write(html, '<!doctype html><html><head><meta charset="utf-8"><style>body{font:12px sans-serif;padding:12px}'
                '.g{display:grid;grid-template-columns:repeat(6,1fr);gap:6px}.c{border:1px solid #ddd;border-radius:8px;padding:6px;text-align:center}'
                '.r{display:flex;justify-content:center;gap:12px}.o{color:#9aa4ad}.n{color:#2b5278}</style></head><body><div class="g">%s</div></body></html>' % cells)
    out = render_png(html, png or os.path.join(HERE, 'batches', '_preview_%s.png' % batch), 60 + ((len(names) + 5) // 6) * 104)
    print('preview:', out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--batch')
    ap.add_argument('--png')
    a = ap.parse_args()
    if a.batch:
        batch_preview(a.batch, a.png)
        return
    written = set()
    icons, mapping, origin = load_shapes()
    own_full = with_variants(mapping)
    tables, previews = [], []
    for pack in OWN:
        tables.append((pack['table'], pack['title'], build_own(pack, icons, own_full, written)))
        previews.append((pack['table'], ['%s_%s' % (pack['prefix'], n) for n in core.PICKER]))
    concepts, tgx_concept = concept_table()
    lib_maps = {}
    report = []
    for pack, p in library.PACKS.items():
        table, m, missing = build_library(pack, tgx_concept, written)
        lib_maps[pack] = m
        tables.append((p['table'], p['title'] + ' (' + p['credit'] + ')', table))
        pv = []
        for n, tgx in zip(core.PICKER, core.PREVIEW_TGX):
            res = res_name(p['prefix'], n, 24, False)
            pv.append(res if res in written else tgx)
        previews.append((p['table'], pv))
        report.append('%s: %d drawables for %d concepts (%d concepts keep Telegram X)' % (
            pack, len(table), len({tgx_concept[t] for t, _ in table}), len(missing)))
    for fn in os.listdir(RES):
        if fn.startswith(OUR_PREFIXES) and fn[:-4] not in written:
            os.remove(os.path.join(RES, fn))
    write_java(tables, previews)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    line_concepts = set(icons)
    write(PREVIEW, preview_page(concepts, icons, line_concepts, lib_maps, 'XGram icon packs: %d concepts' % len(concepts)))
    print('own shapes: %d, Telegram X icons covered: %d (line and duotone)' % (len(icons), len(tables[0][2])))
    print('\n'.join(report))
    print('total drawables', len(written))


if __name__ == '__main__':
    main()
