"""XGram themes: recolors Telegram X's Night Blue theme (its color groups are exactly the palette-relevant keys) role
by role into .tgx-theme files, installed by XgramThemePack through Telegram X's own theme format.

Two sets: the pack (AMOLED, Nord, Dracula, Catppuccin, Sakura, Paper) and one theme per app icon (pack_*.tgx-theme,
icon_<alias>.tgx-theme). Every theme has its own wallpaper slot (wallpaperUsageId 2) and, except AMOLED, a wallpaper:
  - the pack: a four-color gradient that TDLib builds locally from its name ("a~b~c~d");
  - the ten color icon themes: a Telegram doodle pattern over a stronger gradient
    ("<slug>?intensity=I&bg_color=a~b~c~d", or "a-b&rotation=N"), downloaded by TDLib on first use;
  - the six style icon themes (neon, turbo, retro, pixel, origami, stardust): their own picture from
    assets/xgram_wallpapers (tools/xgram/wallpapers.py), set by XgramThemePack when the theme is installed. The
    gradient here is only the fallback (accounts not loaded at the time, "reset to default" in the picker).
TGX tints a pattern at 40% x intensity, so intensities are 35-60 and always positive (it never draws inverted ones).

Run: python gen_themes.py   (writes app/src/main/assets/xgram_themes)
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
SRC = os.path.join(ROOT, 'app', 'src', 'main', 'other', 'themes', 'Night Blue.tgx-theme')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'xgram_themes')
NL = chr(10)

# Night Blue color (6-digit, upper case) -> role
ROLE = {
    '1C2733': 'surface', '151D26': 'bg', '7D8E98': 'muted', '7A8992': 'muted', '59A8E2': 'accent', '10171E': 'separator',
    '64CEFD': 'accentBright', 'FFFFFF': 'text', '61A9E1': 'link', '456A93': 'bubbleOut', '86CAFF': 'outAccent',
    '61A8E1': 'link', '91AFC8': 'outMuted', '6B91AD': 'outMuted2', '212121': 'surface2', '7A8E99': 'muted',
    '495966': 'inactive', '161F28': 'bgAlt', '202020': 'pressed', '222E3C': 'header', '545B63': 'iconLight',
    '5FA8DC': 'link', '538EBD': 'accent', '58A6E0': 'accent', '697882': 'muted', '131B24': 'seekEmpty',
    '2A3E4E': 'seekReady', '35475D': 'selection', '417597': 'accentDim', '254C5E': 'accentDim2', 'A4B9C5': 'quote',
    '8DA1AD': 'inQuote', 'A6C8E3': 'outQuote', '81929C': 'muted',
}

# Keys whose color means something else than their Night Blue group suggests.
COMMON_OVERRIDES = {
    'headerText': 'headerText', 'headerIcon': 'headerText', 'headerTabActive': 'headerTab',
    'headerTabActiveText': 'headerText', 'headerTabInactiveText': 'headerMuted', 'headerButtonIcon': 'onAccent',
    'circleButtonRegularIcon': 'onAccent', 'circleButtonActiveIcon': 'onAccent', 'circleButtonThemeIcon': 'onAccent',
    'badgeText': 'onAccent', 'badgeMutedText': 'onMuted', 'headerBadgeMuted': 'headerMuted',
    # round buttons in the theme's accent, not its link color
    'circleButtonRegular': 'accent', 'circleButtonActive': 'accent', 'circleButtonTheme': 'accent', 'headerButton': 'accent',
}
LIGHT_OVERRIDES = {
    'unreadText': 'accent', 'headerLightBackground': 'surface', 'headerLightIcon': 'muted', 'headerLightText': 'text',
    'headerBadge': 'headerText', 'chatKeyboardButton': 'surface2',
}
# Light themes keep Telegram X's Blue values for these (dark translucent date pills and the like).
LIGHT_KEEP_PARENT = {'bubble_date', 'bubble_date_noWallpaper', 'bubble_overlay', 'drawerText', 'introSectionActive'}


# ---------------------------------------------------------------- palettes derived from a few colors

def _rgb(h):
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def mix(a, b, t):
    """a blended toward b by t (0..1)."""
    ra, rb = _rgb(a), _rgb(b)
    return ''.join('%02X' % int(round(x + (y - x) * t)) for x, y in zip(ra, rb))


def derive(dark, bg, surface, header, text, muted, accent, link=None, accentBright=None, bubbleOut=None,
           onAccent='FFFFFF', headerText=None, headerMuted=None, headerTab=None, outText=None):
    link = link or accent
    accentBright = accentBright or accent
    headerText = headerText or ('FFFFFF' if not dark else text)
    if bubbleOut is None:
        bubbleOut = mix(surface, accent, .42 if dark else .22)
    outText = outText or text
    return dict(
        bg=bg, surface=surface, header=header, text=text, muted=muted, accent=accent, accentBright=accentBright,
        link=link, bubbleOut=bubbleOut, onAccent=onAccent, headerText=headerText,
        headerMuted=headerMuted or mix(header, headerText, .7), headerTab=headerTab or (accentBright if dark else headerText),
        surface2=mix(surface, text, .07), bgAlt=mix(bg, surface, .5),
        separator=mix(bg, '000000', .3) if dark else mix(surface, text, .1),
        inactive=mix(surface, muted, .45), iconLight=mix(surface, muted, .6),
        outAccent=mix(accentBright, 'FFFFFF', .35) if dark else mix(accent, '000000', .25),
        outMuted=mix(bubbleOut, outText, .5), outMuted2=mix(bubbleOut, outText, .35),
        selection=mix(surface, accent, .3), accentDim=mix(surface, accent, .6), accentDim2=mix(surface, accent, .35),
        pressed=mix(surface, text, .08), seekEmpty=mix(bg, surface, .3), seekReady=mix(surface, muted, .35),
        quote=mix(text, surface, .25), inQuote=muted, outQuote=mix(outText, bubbleOut, .3),
        onMuted=bg if dark else 'FFFFFF',
    )


# ---------------------------------------------------------------- the pack

PACK = [
    dict(key='pack_amoled', name='AMOLED', dark=True, wallpaper=None, p=dict(
        bg='000000', surface='000000', surface2='16161C', bgAlt='050507', header='000000', separator='1A1A20',
        text='F2F2F7', muted='8E8E99', inactive='3A3A44', iconLight='4A4A55', accent='8AB4F8', accentBright='9CC2FF',
        link='8AB4F8', bubbleOut='1F3A66', outAccent='A9C9FF', outMuted='8FA6C8', outMuted2='6F87AA', selection='2A3D5C',
        accentDim='3A5A8C', accentDim2='233A5E', pressed='1C1C22', seekEmpty='101014', seekReady='26262E', quote='A0A8B8',
        inQuote='8E96A6', outQuote='A9C0E0', onAccent='0A1220', onMuted='000000', headerText='F2F2F7', headerMuted='8E8E99',
        headerTab='8AB4F8')),
    dict(key='pack_nord', name='Nord', dark=True, wallpaper='2e3440~3b4252~2b303b~434c5e', p=dict(
        bg='2E3440', surface='3B4252', surface2='434C5E', bgAlt='2B303B', header='3B4252', separator='2A2F3A',
        text='ECEFF4', muted='A3ABBA', inactive='4C566A', iconLight='5B6478', accent='88C0D0', accentBright='8FBCBB',
        link='81A1C1', bubbleOut='5E81AC', outAccent='E5F0FA', outMuted='C5D3E6', outMuted2='A9BCD6', selection='4C566A',
        accentDim='5E8A9A', accentDim2='4A6E7A', pressed='434C5E', seekEmpty='2B303B', seekReady='4C566A', quote='D8DEE9',
        inQuote='B8C0CE', outQuote='D8E4F2', onAccent='2E3440', onMuted='2E3440', headerText='ECEFF4', headerMuted='A3ABBA',
        headerTab='88C0D0')),
    dict(key='pack_dracula', name='Dracula', dark=True, wallpaper='21222c~2c2442~282a36~1f2a38', p=dict(
        bg='21222C', surface='282A36', surface2='44475A', bgAlt='1E1F29', header='191A21', separator='191A21',
        text='F8F8F2', muted='9AA0C8', inactive='44475A', iconLight='55597A', accent='BD93F9', accentBright='FF79C6',
        link='8BE9FD', bubbleOut='6A4C9C', outAccent='F1C6FF', outMuted='CDB8F0', outMuted2='B49ADF', selection='44475A',
        accentDim='7B5BB0', accentDim2='5A4385', pressed='3A3C4E', seekEmpty='21222C', seekReady='44475A', quote='C9CCE0',
        inQuote='A6AAC8', outQuote='E2D2FF', onAccent='21222C', onMuted='21222C', headerText='F8F8F2', headerMuted='9AA0C8',
        headerTab='BD93F9')),
    dict(key='pack_mocha', name='Catppuccin Mocha', dark=True, wallpaper='11111b~1e1e2e~181825~241d38', p=dict(
        bg='11111B', surface='1E1E2E', surface2='313244', bgAlt='181825', header='181825', separator='11111B',
        text='CDD6F4', muted='A6ADC8', inactive='45475A', iconLight='585B70', accent='CBA6F7', accentBright='F5C2E7',
        link='89B4FA', bubbleOut='45386B', outAccent='F5C2E7', outMuted='BAC2DE', outMuted2='9399B2', selection='45475A',
        accentDim='7A5FA8', accentDim2='4C3F6E', pressed='313244', seekEmpty='181825', seekReady='45475A', quote='BAC2DE',
        inQuote='A6ADC8', outQuote='E2CCFF', onAccent='1E1E2E', onMuted='1E1E2E', headerText='CDD6F4', headerMuted='A6ADC8',
        headerTab='CBA6F7')),
    dict(key='pack_sakura', name='Sakura', dark=False, wallpaper='ffe3ec~ffd1e0~fff2f6~f9c8da', p=dict(
        bg='FBEFF3', surface='FFFFFF', surface2='FFF7FA', bgAlt='F7E4EB', header='E06C93', separator='F0DCE3',
        text='3B2830', muted='9A7D88', inactive='E2C6D0', iconLight='C9A9B5', accent='E0578A', accentBright='EC6A9A',
        link='D2487C', bubbleOut='FCD7E5', outAccent='C23C6F', outMuted='A8798C', outMuted2='C895A8', selection='F7C9DA',
        accentDim='F2A9C4', accentDim2='F5BDD2', pressed='F6E6EC', seekEmpty='F3DCE5', seekReady='EBC3D2', quote='8A6C77',
        inQuote='8A6C77', outQuote='A15A75', onAccent='FFFFFF', onMuted='FFFFFF', headerText='FFFFFF', headerMuted='F9D3E0',
        headerTab='FFFFFF')),
    dict(key='pack_latte', name='Catppuccin Latte', dark=False, wallpaper='e6e9ef~dcd0fa~eff1f5~cfdcf8', p=dict(
        bg='DCE0E8', surface='EFF1F5', surface2='E6E9EF', bgAlt='E6E9EF', header='8839EF', separator='CCD0DA',
        text='4C4F69', muted='7C7F93', inactive='BCC0CC', iconLight='ACB0BE', accent='8839EF', accentBright='7287FD',
        link='1E66F5', bubbleOut='DCD0FA', outAccent='7A2FE0', outMuted='8C7FAE', outMuted2='A493CF', selection='D7C6FB',
        accentDim='C3A6F7', accentDim2='D5C2FA', pressed='E1E4EB', seekEmpty='DCE0E8', seekReady='BCC0CC', quote='6C6F85',
        inQuote='6C6F85', outQuote='6B4FB0', onAccent='FFFFFF', onMuted='FFFFFF', headerText='FFFFFF', headerMuted='E3D2FC',
        headerTab='FFFFFF')),
    dict(key='pack_paper', name='Paper', dark=False, wallpaper='f3ead7~e8dbc0~f8f1e3~eadcc2', p=dict(
        bg='EFE7D8', surface='FBF7EE', surface2='F5EEDF', bgAlt='EDE3D0', header='6B5A43', separator='E3D8C4',
        text='3B3226', muted='8A7B66', inactive='D6CAB4', iconLight='C2B49C', accent='B5793A', accentBright='C98A45',
        link='9A6428', bubbleOut='EFE0C2', outAccent='8A5A22', outMuted='9C8868', outMuted2='B8A07A', selection='EAD3AE',
        accentDim='DDB98A', accentDim2='E8CFA8', pressed='EFE6D4', seekEmpty='E8DECB', seekReady='D6C6A8', quote='6E604C',
        inQuote='6E604C', outQuote='7E6440', onAccent='FFFFFF', onMuted='FFFFFF', headerText='F7EEDC', headerMuted='D9CBB0',
        headerTab='F7EEDC')),
]

# ---------------------------------------------------------------- one theme per app icon (keys: icon_<manifest alias>)

ICONS = [
    # colors
    dict(key='icon_default', name='XGram', dark=False, wallpaper='fqv01SQemVIBAAAApND8LDRUhRU?intensity=50&bg_color=9fccf4~b9b4f2~8ec0f0~aea2ee',
         p=derive(False, bg='EAF0FB', surface='FFFFFF', header='4A72E8', text='1F2433', muted='7B8399', accent='4C6FEA',
                  link='2AABEE', bubbleOut='DCE4FF')),
    dict(key='icon_aqua', name='Aurora', dark=True, wallpaper='O-wmAfBPSFADAAAA4zINVfD_bro?intensity=40&bg_color=0d3d43~271c5a~104e50~38256f',
         p=derive(True, bg='08161D', surface='0F222C', header='0F2430', text='E6FFFA', muted='86A7A6', accent='00C9A7',
                  accentBright='3CE3C4', link='9C82E8', bubbleOut='3E3480', onAccent='04211C')),
    dict(key='icon_sunset', name='Sunset', dark=False, wallpaper='p-pXcflrmFIBAAAAvXYQk-mCwZU?intensity=55&bg_color=ffbd92~ffa190~ffcaa0~ff917e',
         p=derive(False, bg='FFF1E8', surface='FFFFFF', header='FF7A59', text='3A2620', muted='9A7B70', accent='FF6A4D',
                  link='E8553A', bubbleOut='FFE0CC')),
    dict(key='icon_mono', name='Midnight', dark=True, wallpaper='9LW_RcoOSVACAAAAFTk3DTyXN-M?intensity=38&bg_color=14245f-25186c&rotation=315',
         p=derive(True, bg='070B1C', surface='0B1026', header='0B1026', text='E8F7FF', muted='7E8FB5', accent='00E5FF',
                  accentBright='7C4DFF', link='4FD8FF', bubbleOut='2A237A', onAccent='021820', headerTab='00E5FF')),
    dict(key='icon_emerald', name='Emerald', dark=False, wallpaper='JqSUrO0-mFIBAAAAWwTvLzoWGQI?intensity=50&bg_color=a6e3c4~88d6b3~bfecd3~79cba9',
         p=derive(False, bg='EAF7F0', surface='FFFFFF', header='129E86', text='1D332B', muted='6F8C80', accent='11998E',
                  link='0F8C6E', bubbleOut='D6F7E2')),
    dict(key='icon_gold', name='Gold', dark=True, wallpaper='F5oWoCs7QFACAAAAgf2bD_mg8Bw?intensity=35&bg_color=2b2317~3a2e1c~211c14~453520',
         p=derive(True, bg='0B0B0B', surface='151413', header='141414', text='F3EBDD', muted='A0947C', accent='E9C46A',
                  accentBright='F7D774', link='E0B65A', bubbleOut='4A3B1C', onAccent='1A1408', headerTab='E9C46A')),
    dict(key='icon_violet', name='Glass', dark=False, wallpaper='MIo6r0qGSFAFAAAAtL8TsDzNX60?intensity=50&bg_color=c4b2ef-a6bdf3&rotation=135',
         p=derive(False, bg='EEF0FB', surface='FFFFFF', header='6F79E0', text='262842', muted='7E80A0', accent='7B5FD6',
                  link='5B86E5', bubbleOut='E4DDFA')),
    dict(key='icon_rose', name='Rose', dark=False, wallpaper='PllZ-bf_SFAEAAAA8crRfwZiDNg?intensity=55&bg_color=ffb0c3~ffb5b5~ffc8d3~ff9cb2',
         p=derive(False, bg='FFF0F3', surface='FFFFFF', header='F2577F', text='3A2228', muted='9C7880', accent='F0507E',
                  link='E8436A', bubbleOut='FFDCE3')),
    dict(key='icon_night', name='Night', dark=True, wallpaper='dk_wwlghOFACAAAAfz9xrxi6euw?intensity=42&bg_color=232b43~2c3b5d~202539~353b64',
         p=derive(True, bg='10101C', surface='1A1A2E', header='16213E', text='E6ECFF', muted='8A93B8', accent='4FC3F7',
                  accentBright='7C9DFF', link='6FB8FF', bubbleOut='2E3570', onAccent='06202C')),
    dict(key='icon_light', name='Mono', dark=False, light_header=True, wallpaper='RepJ5uE_SVABAAAAr4d0YhgB850?intensity=45&bg_color=d2d2d2-bababa&rotation=180',
         p=derive(False, bg='EFEFEF', surface='FFFFFF', header='F7F7F7', text='111111', muted='777777', accent='111111',
                  link='333333', bubbleOut='E4E4E4', headerText='111111', headerMuted='777777', headerTab='111111')),
    # styles
    dict(key='icon_neon', name='Neon', dark=True, wallpaper='1a0a40~45125e~142a58~33115c',
         p=derive(True, bg='0A0418', surface='120A26', header='1C0A3A', text='F7EBFF', muted='9C86C0', accent='FF2BD6',
                  accentBright='00E5FF', link='00E5FF', bubbleOut='4E1460', onAccent='1C0A3A', headerTab='FF2BD6')),
    dict(key='icon_turbo', name='Turbo', dark=False, wallpaper='ffd49a~ffb98f~ffc7a8~ffa6a6',
         p=derive(False, bg='FFF2E6', surface='FFFFFF', header='FF6A00', text='37231A', muted='9B7B6B', accent='FF5A1F',
                  link='FF2D55', bubbleOut='FFE0C7')),
    dict(key='icon_retro', name='Retro', dark=False, wallpaper='ead6a4~dfc286~f0e0b8~d9b977',
         p=derive(False, bg='F3E6C4', surface='FBF3DE', header='264653', text='1D3557', muted='7C7458', accent='E63946',
                  link='2A9D8F', bubbleOut='F2DEA6', headerText='F7E9C6', headerMuted='A8C3BF')),
    dict(key='icon_pixel', name='Pixel', dark=True, wallpaper='28154f~42207a~2d1860~3a1c70',
         p=derive(True, bg='0F0820', surface='1B1037', header='2A1450', text='F2EEFF', muted='9A8CC0', accent='4DD0E1',
                  accentBright='7CF3FF', link='4DD0E1', bubbleOut='3A2470', onAccent='0A1F24')),
    dict(key='icon_origami', name='Origami', dark=False, wallpaper='bfe6fa~a9cdf5~d3eefc~9cc2f2',
         p=derive(False, bg='EAF4FD', surface='FFFFFF', header='3F86F0', text='1C2A3F', muted='72819A', accent='2F6FED',
                  link='1F8FE0', bubbleOut='D9EBFF')),
    dict(key='icon_stardust', name='Stardust', dark=False, wallpaper='c9d3ff~d6c0ff~efc0e2~ccbaff',
         p=derive(False, bg='F2EFFD', surface='FFFFFF', header='8A6CFF', text='2A2342', muted='857DA3', accent='976FFF',
                  accentBright='E46ACE', link='6B93FF', bubbleOut='EADFFF')),
]

THEMES = PACK + ICONS


def expand(hexv):
    """#RGB / #RGBA / #RRGGBB / #RRGGBBAA -> (RRGGBB, AA or '')."""
    h = hexv.lstrip('#').upper()
    if len(h) in (3, 4):
        h = ''.join(c * 2 for c in h)
    return h[:6], h[6:8]


def template():
    """[(keys, value)] of Night Blue's color section."""
    groups = []
    in_colors = False
    for raw in open(SRC, encoding='utf-8'):
        line = raw.strip()
        if line == '#':
            in_colors = True
            continue
        if not in_colors or not line or ':' not in line:
            continue
        keys, value = line.split(':', 1)
        groups.append(([k.strip() for k in keys.split(',') if k.strip()], value.strip()))
    return groups


def build(theme):
    p = theme['p']
    dark = theme['dark']
    colors = {}
    for keys, value in template():
        base, alpha = expand(value)
        role = ROLE.get(base)
        for k in keys:
            if k.startswith('theme'):
                continue  # the swatches of Telegram X's built-in themes
            if role is None:
                if dark:
                    colors[k] = '#' + base + alpha
                continue
            if not dark and k in LIGHT_KEEP_PARENT:
                continue
            colors[k] = '#' + p[role] + alpha
    overrides = dict(COMMON_OVERRIDES)
    if not dark:
        overrides.update(LIGHT_OVERRIDES)
    for k, role in overrides.items():
        colors[k] = '#' + p[role]
    colors['headerPlaceholder'] = '#' + p['headerText'] + '10'
    if theme.get('light_header'):
        # A light header: dark icons and a light status bar.
        colors['headerLightBackground'] = '#' + p['surface']
    by_value = {}
    for k in sorted(colors, key=str.lower):
        by_value.setdefault(colors[k], []).append(k)
    lines = ['!', 'name: "%s"' % theme['name'], 'time: 1760227200']
    if theme.get('wallpaper'):
        lines.append('wallpaper: "%s"' % theme['wallpaper'])
    lines.append('@')
    if dark:
        lines += ['dark, shadowDepth: 1', 'parentTheme: 10']
    else:
        lines.append('parentTheme: 1')
        if theme.get('light_header'):
            lines.append('lightStatusBar: 1')
    lines.append('wallpaperUsageId: 2')
    lines.append('#')
    for value in sorted(by_value, key=lambda v: by_value[v][0].lower()):
        lines.append('%s: %s' % (', '.join(by_value[value]), value))
    return NL.join(lines) + NL


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in os.listdir(OUT):
        if name.endswith('.tgx-theme'):
            os.remove(os.path.join(OUT, name))
    for t in THEMES:
        with open(os.path.join(OUT, t['key'] + '.tgx-theme'), 'w', encoding='utf-8', newline=NL) as fh:
            fh.write(build(t))
    print('wrote', len(THEMES), 'themes to', OUT)


if __name__ == '__main__':
    main()
