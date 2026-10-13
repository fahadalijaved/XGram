"""Writes XGram's launcher icons as Android resources (adaptive vectors, themed layer)."""
import math, os

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'res')
NL = chr(10)

# Glyph geometry (108 viewport), same as render3.py
W = 11.0
H = W / 2
X0, Y0 = 36.5, 71.5
TIP = (76.5, 31.5)
L = math.hypot(TIP[0] - X0, TIP[1] - Y0)
UX, UY = (TIP[0] - X0) / L, (TIP[1] - Y0) / L
VX, VY = -UY, UX
MX, MY = X0 + UX * 26, Y0 + UY * 26

def f(v):
    return ('%.3f' % v).rstrip('0').rstrip('.')

SHAFT = 'M%s,%s L%s,%s' % (f(X0), f(Y0), f(MX), f(MY))
TIP_PATH = 'M%s,%s L%s,%s L%s,%s Z' % (f(MX + VX * H), f(MY + VY * H), f(TIP[0]), f(TIP[1]), f(MX - VX * H), f(MY - VY * H))
BACK = 'M36.5,36.5 L71.5,71.5'

def argb(c, alpha=None):
    c = c.lstrip('#')
    if len(c) == 6:
        c = 'FF' + c
    if alpha is not None:
        c = '%02X' % int(round(alpha * 255)) + c[2:]
    return '#' + c.upper()

def grad(attr, c0, c1, x0=0, y0=0, x1=108, y1=108):
    return NL.join([
        '',
        '    <aapt:attr name="%s">' % attr,
        '      <gradient android:type="linear" android:startX="%s" android:startY="%s" android:endX="%s" android:endY="%s">' % (x0, y0, x1, y1),
        '        <item android:offset="0" android:color="%s" />' % argb(c0),
        '        <item android:offset="1" android:color="%s" />' % argb(c1),
        '      </gradient>',
        '    </aapt:attr>',
    ])

def head(comment):
    return NL.join([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- %s -->' % comment,
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '  xmlns:aapt="http://schemas.android.com/aapt"',
        '  android:width="108dp"',
        '  android:height="108dp"',
        '  android:viewportWidth="108"',
        '  android:viewportHeight="108">',
        '',
    ])

def opt(name, value):
    return '' if value is None else ' android:%s="%s"' % (name, value)

GLYPH_GRADIENT = dict(x0=36, y0=72, x1=77, y1=31)

def stroke_path(data, width, color=None, gradient=None, alpha=None):
    if gradient:
        return '  <path android:pathData="%s" android:strokeWidth="%s" android:strokeLineCap="round"%s>%s%s  </path>%s' % (
            data, f(width), opt('strokeAlpha', alpha), grad('android:strokeColor', *gradient, **GLYPH_GRADIENT), NL, NL)
    return '  <path android:pathData="%s" android:strokeWidth="%s" android:strokeLineCap="round" android:strokeColor="%s"%s />%s' % (
        data, f(width), argb(color), opt('strokeAlpha', alpha), NL)

def fill_path(data, color=None, gradient=None, alpha=None, stroke=None):
    extra = ''
    if stroke:
        extra = ' android:strokeWidth="%s" android:strokeLineJoin="round" android:strokeColor="%s"%s' % (f(stroke[0]), argb(stroke[1]), opt('strokeAlpha', alpha))
    if gradient:
        return '  <path android:pathData="%s"%s%s>%s%s  </path>%s' % (
            data, opt('fillAlpha', alpha), extra, grad('android:fillColor', *gradient, **GLYPH_GRADIENT), NL, NL)
    return '  <path android:pathData="%s" android:fillColor="%s"%s%s />%s' % (data, argb(color), opt('fillAlpha', alpha), extra, NL)

def circle(cx, cy, r):
    return 'M%s,%sm-%s,0a%s,%s 0 1,0 %s,0a%s,%s 0 1,0 -%s,0' % (cx, cy, r, r, r, 2 * r, r, r, 2 * r)

def background(v):
    out = head('XGram icon "%s": background' % v['name'])
    out += '  <path android:pathData="M0,0h108v108h-108z">%s%s  </path>%s' % (grad('android:fillColor', *v['bg']), NL, NL)
    if v.get('glass'):
        out += '  <path android:pathData="%s" android:fillColor="#FFFFFFFF" android:fillAlpha="0.16" />%s' % (circle(34, 32, 24), NL)
        out += '  <path android:pathData="%s" android:fillColor="#FFFFFFFF" android:fillAlpha="0.10" />%s' % (circle(81, 79, 29), NL)
    if v.get('ring'):
        out += '  <path android:pathData="%s" android:strokeWidth="2.2" android:strokeColor="%s" android:strokeAlpha="0.28" />%s' % (circle(54, 54, 31), argb(v['ring']), NL)
    return out + '</vector>' + NL

def foreground(v):
    out = head('XGram icon "%s": the woven X, its front stroke ending in a paper-plane point' % v['name'])
    if v.get('glow'):
        g = v['glow']
        for width, alpha in ((22, 0.08), (17, 0.14)):
            out += stroke_path(BACK, width, g, alpha=alpha)
            out += stroke_path(SHAFT, width, g, alpha=alpha)
            out += fill_path(TIP_PATH, g, alpha=alpha, stroke=(width - W + 1, g))
    out += stroke_path(BACK, W, v['b'], alpha=v.get('b_alpha'))
    if v.get('shadow', True):
        out += '  <group android:translateY="1.6">' + NL
        out += '  ' + stroke_path(SHAFT, W + 1.2, '#000000', alpha=0.16)
        out += '  ' + fill_path(TIP_PATH, '#000000', alpha=0.16, stroke=(1.2, '#000000'))
        out += '  </group>' + NL
    out += stroke_path(SHAFT, W, v.get('a'), gradient=v.get('a_grad'))
    out += fill_path(TIP_PATH, v.get('a'), gradient=v.get('a_grad'))
    return out + '</vector>' + NL

def monochrome():
    out = head('XGram icon: themed (monochrome) layer, Android 13+')
    out += stroke_path(BACK, W, '#000000', alpha=0.55)
    out += stroke_path(SHAFT, W, '#000000')
    out += fill_path(TIP_PATH, '#000000')
    return out + '</vector>' + NL

def adaptive(key):
    return NL.join([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
        '  <background android:drawable="@drawable/xgram_ic_bg_%s" />' % key,
        '  <foreground android:drawable="@drawable/xgram_ic_fg_%s" />' % key,
        '  <monochrome android:drawable="@drawable/xgram_ic_monochrome" />',
        '</adaptive-icon>',
        '',
    ])

# alias id (manifest component names stay the same, so the chosen icon survives the update) -> design
VARIANTS = [
    dict(alias='default', name='XGram', bg=['#2AABEE', '#6C5CE7'], a='#FFFFFF', b='#FFFFFF', b_alpha=0.7),
    dict(alias='aqua', name='Aurora', bg=['#00C9A7', '#845EC2'], a='#FFFFFF', b='#FFFFFF', b_alpha=0.7),
    dict(alias='sunset', name='Sunset', bg=['#FFB347', '#FF5E62'], a='#FFFFFF', b='#FFFFFF', b_alpha=0.7),
    dict(alias='mono', name='Midnight', bg=['#0B1026', '#1F2C56'], a_grad=['#00E5FF', '#7C4DFF'], b='#00E5FF', b_alpha=0.5, glow='#00E5FF', shadow=False),
    dict(alias='emerald', name='Emerald', bg=['#11998E', '#38EF7D'], a='#FFFFFF', b='#FFFFFF', b_alpha=0.7),
    dict(alias='gold', name='Gold', bg=['#141414', '#2E2A24'], a_grad=['#F7D774', '#C9932B'], b='#C9932B', b_alpha=0.55, ring='#E9C46A', shadow=False),
    dict(alias='violet', name='Glass', bg=['#5B86E5', '#9D50BB'], a='#FFFFFF', b='#FFFFFF', b_alpha=0.6, glass=True),
    dict(alias='light', name='Mono', bg=['#F4F4F4', '#DCDCDC'], a='#111111', b='#111111', b_alpha=0.5, shadow=False),
    dict(alias='night', name='Night', bg=['#1A1A2E', '#16213E'], a_grad=['#4FC3F7', '#7C4DFF'], b='#4FC3F7', b_alpha=0.45, shadow=False),
    dict(alias='rose', name='Rose', bg=['#F857A6', '#FF5858'], a='#FFFFFF', b='#FFFFFF', b_alpha=0.7),
]

def write(path, text):
    with open(path, 'w', encoding='utf-8', newline=NL) as fh:
        fh.write(text)

def main():
    drawable = os.path.join(RES, 'drawable')
    anydpi = os.path.join(RES, 'mipmap-anydpi-v26')
    write(os.path.join(drawable, 'xgram_ic_monochrome.xml'), monochrome())
    for v in VARIANTS:
        key = v['alias']
        write(os.path.join(drawable, 'xgram_ic_bg_%s.xml' % key), background(v))
        write(os.path.join(drawable, 'xgram_ic_fg_%s.xml' % key), foreground(v))
        write(os.path.join(anydpi, 'xgram_icon_%s.xml' % key), adaptive(key))
    print('wrote', len(VARIANTS), 'icons')

if __name__ == '__main__':
    main()
