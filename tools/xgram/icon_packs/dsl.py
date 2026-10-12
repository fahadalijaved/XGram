"""Shape helpers for XGram icon packs (re-exported from tools/xgram/icons_line.py, the core set).

Every icon is a list of elements on a 24x24 grid, drawn like a 2 px rounded line icon:
  P(d)                 stroked SVG path data (absolute or relative commands; end with Z to mark it closed)
  L(x1, y1, x2, y2)    stroked line
  PL((x, y), ..., close=False)  stroked polyline
  C(cx, cy, r)         stroked circle (closed)
  RR(x, y, w, h, r)    stroked rounded rectangle (closed)
  DOT(cx, cy, r=1.25)  filled dot
  F(d)                 filled SVG path
  SLASH()              the 'off' diagonal from top-left to bottom-right
  gear(), star_d()     ready-made shapes
Keep strokes inside 2..22; the stroke is 2 units wide.
"""
import importlib.util
import os

_core_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'icons_line.py')
_spec = importlib.util.spec_from_file_location('xgram_icons_core', _core_path)
core = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(core)

f = core.f
P, F, L, PL, C, DOT, RR, SLASH = core.P, core.F, core.L, core.PL, core.C, core.DOT, core.RR, core.SLASH
circle_d, rr_d, gear, star_d = core.circle_d, core.rr_d, core.gear, core.star_d

__all__ = ['f', 'P', 'F', 'L', 'PL', 'C', 'DOT', 'RR', 'SLASH', 'circle_d', 'rr_d', 'gear', 'star_d']
