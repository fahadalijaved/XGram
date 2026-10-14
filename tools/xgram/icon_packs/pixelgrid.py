"""24x24 pixel grids for XGram Pixel: Pixelarticons bodies (axis-aligned paths) to grids and back, and grid edits
used by maps/pixel.json "compose" entries (slash, mirror, rotate, shift, hand-drawn rows).

A grid is a list of 24 strings of '#' (ink) and '.'; every helper returns a new grid.
"""
import re

N = 24
_TOK = re.compile(r'[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?')


def _polys(d):
    """Polygons of a path made of M/L/H/V/Z commands (absolute or relative)."""
    toks = _TOK.findall(d)
    polys, cur = [], []
    x = y = sx = sy = 0.0
    cmd, i = None, 0
    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in 'Zz':
                if cur:
                    polys.append(cur)
                cur, x, y = [], sx, sy
            elif cmd not in 'MmLlHhVv':
                raise ValueError('pixel path with curve command %r' % cmd)
            continue
        if cmd in 'Mm':
            nx, ny = float(toks[i]), float(toks[i + 1])
            i += 2
            if cmd == 'm':
                nx, ny = nx + x, ny + y
            if cur:
                polys.append(cur)
            cur, x, y, sx, sy = [(nx, ny)], nx, ny, nx, ny
            cmd = 'L' if cmd == 'M' else 'l'
            continue
        if cmd in 'Ll':
            nx, ny = float(toks[i]), float(toks[i + 1])
            i += 2
            if cmd == 'l':
                nx, ny = nx + x, ny + y
        elif cmd in 'Hh':
            nx, ny = float(toks[i]) + (x if cmd == 'h' else 0), y
            i += 1
        else:
            nx, ny = x, float(toks[i]) + (y if cmd == 'v' else 0)
            i += 1
        x, y = nx, ny
        cur.append((x, y))
    if cur:
        polys.append(cur)
    return [p for p in polys if len(p) > 2]


def _winding(polys, px, py):
    w = 0
    for p in polys:
        for (x1, y1), (x2, y2) in zip(p, p[1:] + p[:1]):
            if y1 <= py < y2 and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                w += 1
            elif y2 <= py < y1 and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                w -= 1
    return w


def from_paths(ds, even_odd=False):
    polys = [p for d in ds for p in _polys(d)]
    rows = []
    for r in range(N):
        row = ''
        for c in range(N):
            w = _winding(polys, c + 0.5, r + 0.5)
            row += '#' if (w % 2 if even_odd else w) else '.'
        rows.append(row)
    return rows


def from_layers(lys):
    """Grid of a library icon's fill layers (Pixelarticons draws with fills only)."""
    g = empty()
    for l in lys:
        if l['kind'] != 'fill':
            raise ValueError('pixel icon with a stroke layer')
        g = union(g, from_paths([l['d']], l.get('even_odd')))
    return g


def empty():
    return ['.' * N] * N


def rows(lines):
    """A hand-drawn grid: up to 24 rows, padded with '.'."""
    lines = [ln.replace(' ', '') for ln in lines]
    lines = [ln.ljust(N, '.')[:N] for ln in lines] + ['.' * N] * (N - len(lines))
    return lines[:N]


def union(a, b):
    return [''.join('#' if '#' in (p, q) else '.' for p, q in zip(ra, rb)) for ra, rb in zip(a, b)]


def subtract(a, b):
    return [''.join('.' if q == '#' else p for p, q in zip(ra, rb)) for ra, rb in zip(a, b)]


def mirror(g):
    return [r[::-1] for r in g]


def rotate_cw(g):
    return [''.join(g[N - 1 - r][c] for r in range(N)) for c in range(N)]


def shift(g, dx, dy):
    out = []
    for r in range(N):
        sr = r - dy
        out.append(''.join(g[sr][c - dx] if 0 <= sr < N and 0 <= c - dx < N else '.' for c in range(N)))
    return out


def slash_cells(x0=2, y0=1, x1=21, y1=22, thick=2):
    """Cells of Pixelarticons' 'off' slash: 2x2 steps from the top-left to the bottom-right (as in zap-off)."""
    cells = set()
    x, y = x0, y0
    while x + thick - 1 <= x1 and y + thick - 1 <= y1:
        for dx in range(thick):
            for dy in range(thick):
                cells.add((x + dx, y + dy))
        x += 2
        y += 2
    return cells


def _grid_of(cells):
    return [''.join('#' if (c, r) in cells else '.' for c in range(N)) for r in range(N)]


def slashed(g, x0=2, y0=1, x1=21, y1=22):
    """The icon struck through like Pixelarticons' -off icons: a 2x2 staircase from the top-left to the bottom-right
    with two clear cells each side of it."""
    cells = slash_cells(x0, y0, x1, y1)
    k = x0 - y0  # c - r along the middle of the staircase
    gap = {(c, r) for c in range(N) for r in range(y0 - 1, y1 + 2) if abs(c - r - k) <= 3}
    return union(subtract(g, _grid_of(gap)), _grid_of(cells))


def to_path(g):
    """Path data: one rectangle per horizontal run, merged down over identical runs."""
    runs = []
    for r, row in enumerate(g):
        for m in re.finditer(r'#+', row):
            runs.append((r, m.start(), m.end()))
    used, out = set(), []
    for r, a, b in runs:
        if (r, a, b) in used:
            continue
        h = 1
        while (r + h, a, b) in runs and (r + h, a, b) not in used:
            used.add((r + h, a, b))
            h += 1
        used.add((r, a, b))
        out.append('M%d %dh%dv%dh-%dz' % (a, r, b - a, h, b - a))
    return ''.join(out)


def ascii(g):
    return '\n'.join(g)
