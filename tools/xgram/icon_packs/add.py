"""Adds Telegram X drawables to XGram's icon packs without a full rebuild.

  python tools/xgram/icon_packs/add.py baseline_cloud_24 baseline_flight_24 ...

Each name gets its drawable in every pack: XGram Line and Duotone from batches/*.py (its MAP entry), the library packs
from maps/<pack>.json by the name's concept: the one concepts.json lists it under, as a full build takes it (so
baseline_logout_24 shares exit_to_app's drawables), else the one its name gives. concepts.json should list it either
way, so a full build keeps it. The rows go at the end of the pack tables in XgramIconPackTable.java; nothing else there
changes, so the hand-written Material 3 Expressive parts stay. Files that already exist (an own shape other icons
share) are kept as they are. build.py remains the full rebuild.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build  # noqa: E402
import library  # noqa: E402
import styles  # noqa: E402


def main(names):
    if not names:
        raise SystemExit(__doc__)
    icons, mapping, _ = build.load_shapes()
    listed = {}
    for c, v in json.load(open(os.path.join(HERE, 'concepts.json'), encoding='utf-8')).items():
        for tgx in v['tgx']:
            listed.setdefault(tgx, c)  # the first concept that lists it, as build.concept_table
    rows = {}
    for tgx in names:
        info = build.tgx_info(tgx)
        if not info:
            raise SystemExit('no Telegram X vector drawable ' + tgx)
        size, white = info
        color = '#FFFFFFFF' if white else '#FF000000'
        icon = mapping.get(tgx)
        if icon is None:
            raise SystemExit('%s has no shape: add it to a batch MAP' % tgx)
        for pack in build.OWN:
            res = build.res_name(pack['prefix'], icon, size, white)
            path = os.path.join(build.RES, res + '.xml')
            if not os.path.exists(path):
                layers, scale = styles.render(icons[icon], pack['style'], icon)
                build.write(path, styles.vector_xml(layers, scale, size, color, pack['title'], frame=build.FRAMES.get(icon)))
                print('wrote', res)
            rows.setdefault(pack['table'], []).append((tgx, res))
        c = listed.get(tgx) or build.concept(tgx)
        for key, p in library.PACKS.items():
            base = json.load(open(os.path.join(HERE, 'maps', key + '.json'), encoding='utf-8')).get(c)
            got = library.icon(key, base) if base else None
            if not got:
                print('%s: %s keeps Telegram X (no %s in maps/%s.json)' % (p['title'], tgx, c, key))
                continue
            lys, viewport = got
            res = build.res_name(p['prefix'], c, size, white)
            path = os.path.join(build.RES, res + '.xml')
            if not os.path.exists(path):
                build.write(path, library.vector_xml(lys, viewport, size, color, p['title']))
                print('wrote', res)
            rows.setdefault(p['table'], []).append((tgx, res))
    java = open(build.JAVA, encoding='utf-8').read()
    for table, pairs in rows.items():
        m = re.search(r'(  static final int\[\] %s = \{\n)(.*?)(\n  \};)' % table, java, re.S)
        if not m:
            raise SystemExit('no table ' + table)
        body = m.group(2)
        add = ['    R.drawable.%s, R.drawable.%s,' % pair for pair in pairs if 'R.drawable.%s,' % pair[0] not in body]
        if add:
            java = java[:m.end(2)] + '\n' + '\n'.join(add) + java[m.end(2):]
            print('%s: %d rows' % (table, len(add)))
    build.write(build.JAVA, java)


if __name__ == '__main__':
    main(sys.argv[1:])
