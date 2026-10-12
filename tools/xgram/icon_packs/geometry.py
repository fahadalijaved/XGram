"""SVG path parsing and flattening for the derived pack styles (Pixel, Doodle)."""
import math
import re

_TOKEN = re.compile(r'[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?')


def _tokens(d):
    return _TOKEN.findall(d)


def _arc_points(x1, y1, rx, ry, phi_deg, large, sweep, x2, y2, step):
    """Endpoint arc -> points (SVG implementation notes F.6)."""
    if rx == 0 or ry == 0 or (x1 == x2 and y1 == y2):
        return [(x2, y2)]
    phi = math.radians(phi_deg)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cp * dx + sp * dy
    y1p = -sp * dx + cp * dy
    rx, ry = abs(rx), abs(ry)
    lam = (x1p ** 2) / (rx ** 2) + (y1p ** 2) / (ry ** 2)
    if lam > 1:
        s = math.sqrt(lam)
        rx, ry = rx * s, ry * s
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2.0
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2.0

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    n = max(2, int(abs(dt) * max(rx, ry) / step) + 1)
    pts = []
    for i in range(1, n + 1):
        t = t1 + dt * i / n
        x = cp * rx * math.cos(t) - sp * ry * math.sin(t) + cx
        y = sp * rx * math.cos(t) + cp * ry * math.sin(t) + cy
        pts.append((x, y))
    return pts


def _bezier(p0, p1, p2, p3, step):
    length = math.dist(p0, p1) + math.dist(p1, p2) + math.dist(p2, p3)
    n = max(2, int(length / step) + 1)
    pts = []
    for i in range(1, n + 1):
        t = i / n
        mt = 1 - t
        x = mt ** 3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0]
        y = mt ** 3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]
        pts.append((x, y))
    return pts


def flatten(d, step=0.4):
    """SVG path data -> list of (points, closed) subpaths."""
    toks = _tokens(d)
    i = 0
    cmd = None
    x = y = 0.0
    sx = sy = 0.0
    last_ctrl = None
    subpaths = []
    cur = []

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    def finish(closed):
        nonlocal cur
        if len(cur) > 1:
            subpaths.append((cur, closed))
        cur = []

    while i < len(toks):
        t = toks[i]
        if re.match(r'[A-Za-z]', t):
            cmd = t
            i += 1
            if cmd in 'Zz':
                if cur:
                    cur.append((sx, sy))
                finish(True)
                x, y = sx, sy
                last_ctrl = None
                continue
        if cmd is None:
            break
        rel = cmd.islower()
        c = cmd.upper()
        if c == 'M':
            finish(False)
            nx, ny = num(), num()
            if rel:
                nx += x
                ny += y
            x, y = nx, ny
            sx, sy = x, y
            cur = [(x, y)]
            cmd = 'l' if rel else 'L'
            last_ctrl = None
        elif c == 'L':
            nx, ny = num(), num()
            if rel:
                nx += x
                ny += y
            x, y = nx, ny
            cur.append((x, y))
            last_ctrl = None
        elif c == 'H':
            nx = num()
            x = nx + x if rel else nx
            cur.append((x, y))
            last_ctrl = None
        elif c == 'V':
            ny = num()
            y = ny + y if rel else ny
            cur.append((x, y))
            last_ctrl = None
        elif c in 'CS':
            if c == 'C':
                x1, y1 = num(), num()
                if rel:
                    x1 += x
                    y1 += y
            else:
                x1, y1 = (2 * x - last_ctrl[0], 2 * y - last_ctrl[1]) if last_ctrl else (x, y)
            x2, y2, nx, ny = num(), num(), num(), num()
            if rel:
                x2 += x
                y2 += y
                nx += x
                ny += y
            cur.extend(_bezier((x, y), (x1, y1), (x2, y2), (nx, ny), step))
            last_ctrl = (x2, y2)
            x, y = nx, ny
        elif c in 'QT':
            if c == 'Q':
                qx, qy = num(), num()
                if rel:
                    qx += x
                    qy += y
            else:
                qx, qy = (2 * x - last_ctrl[0], 2 * y - last_ctrl[1]) if last_ctrl else (x, y)
            nx, ny = num(), num()
            if rel:
                nx += x
                ny += y
            c1 = (x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y))
            c2 = (nx + 2 / 3 * (qx - nx), ny + 2 / 3 * (qy - ny))
            cur.extend(_bezier((x, y), c1, c2, (nx, ny), step))
            last_ctrl = (qx, qy)
            x, y = nx, ny
        elif c == 'A':
            rx, ry, phi = num(), num(), num()
            large, sweep = int(num()), int(num())
            nx, ny = num(), num()
            if rel:
                nx += x
                ny += y
            cur.extend(_arc_points(x, y, rx, ry, phi, large, sweep, nx, ny, step))
            x, y = nx, ny
            last_ctrl = None
        else:
            i += 1
    finish(False)
    return subpaths
