"""PNG launcher icons for Android 7.x and older (no adaptive icons), rendered from the same design as gen_android.py."""
import os, sys, importlib.util
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('render3', os.path.join(HERE, 'render3.py'))
r3 = importlib.util.module_from_spec(spec)
sys.argv = [sys.argv[0], os.path.join(HERE, 'sheet_tmp.png')]
spec.loader.exec_module(r3)  # also renders a preview sheet

OUT = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'res', 'mipmap-xxxhdpi')
BY_NAME = {v['name']: v for v in r3.V}
ALIASES = {
    'default': 'XGram', 'aqua': 'Aurora', 'sunset': 'Sunset', 'mono': 'Midnight', 'emerald': 'Emerald',
    'gold': 'Gold', 'violet': 'Glass', 'light': 'Mono', 'night': 'Night', 'rose': 'Rose',
}
for alias, name in ALIASES.items():
    im = r3.render(BY_NAME[name]).resize((192, 192), Image.LANCZOS)
    im.save(os.path.join(OUT, 'xgram_icon_%s.png' % alias))
print('legacy icons:', len(ALIASES))
