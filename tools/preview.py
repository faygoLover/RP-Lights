"""Превью моделей предметов/блоков RP Lights без игры: программная отрисовка JSON-модели (элементы без поворота).

python tools/preview.py out.png name[:вид] ...   вид: gui (по умолчанию), fixed, side, top, front, iso, tp, fp
Вид tp/fp — как в руке (третье / первое лицо), рисуется со стороны.
"""
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets')

sys.path.insert(0, os.path.dirname(__file__))
from gen_models import rot_xyz, apply, texel_point  # noqa: E402


def load_model(rl):
    ns, path = rl.split(':') if ':' in rl else ('minecraft', rl)
    f = os.path.join(ROOT, ns, 'models', path + '.json')
    if not os.path.exists(f):
        return {}
    m = json.load(open(f, encoding='utf-8'))
    if 'parent' in m:
        p = load_model(m['parent'])
        out = dict(p)
        out.update({k: v for k, v in m.items() if k not in ('textures', 'display')})
        out['textures'] = dict(p.get('textures', {}), **m.get('textures', {}))
        out['display'] = dict(p.get('display', {}), **m.get('display', {}))
        if 'elements' not in m and 'elements' in p:
            out['elements'] = p['elements']
        return out
    return m


def tex(rl):
    ns, path = rl.split(':')
    return Image.open(os.path.join(ROOT, ns, 'textures', path + '.png')).convert('RGBA')


SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}


def corners(el, face):
    a, b = el['from'], el['to']
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def auto_uv(p, face):
    x, y, z = p
    return {
        'north': (16 - x, 16 - y), 'south': (x, 16 - y), 'west': (z, 16 - y),
        'east': (16 - z, 16 - y), 'up': (x, z), 'down': (x, 16 - z),
    }[face]


def face_auto_rect(el, face):
    us, vs = zip(*[auto_uv(c, face) for c in corners(el, face)])
    return min(us), min(vs), max(us), max(vs)


def render(name, view='gui', size=256, model_rl=None):
    m = load_model(model_rl or f'rplights:item/{name}')
    textures = {}
    for k, v in m.get('textures', {}).items():
        while v.startswith('#'):
            v = m['textures'][v[1:]]
        textures[k] = v
    disp = m.get('display', {})
    if view in ('gui', 'fixed', 'tp', 'fp') and view in {'gui': 'gui', 'fixed': 'fixed'}:
        d = disp.get(view, {})
        R = rot_xyz(*d.get('rotation', [0, 0, 0]))
        s = d.get('scale', [1, 1, 1])[0]
        t = d.get('translation', [0, 0, 0])
    else:
        rots = {'side': (0, 90, 0), 'top': (90, 0, 0), 'front': (0, 0, 0), 'iso': (30, 225, 0),
                'gui': (30, 225, 0), 'tp': (0, 0, 0), 'fp': (0, 0, 0)}
        R = rot_xyz(*rots.get(view, (30, 225, 0)))
        s, t = 1.0, [0, 0, 0]
    if view in ('tp', 'fp'):
        key = 'thirdperson_righthand' if view == 'tp' else 'firstperson_righthand'
        d = disp.get(key, {})
        Rd = rot_xyz(*d.get('rotation', [0, 0, 0]))
        sd = d.get('scale', [1, 1, 1])[0]
        td = d.get('translation', [0, 0, 0])
        # tp: кадр руки (+Y вперёд, +Z вверх) → вид сбоку: экран x = вперёд, y = вверх
        view_m = [[0, 1, 0], [0, 0, 1], [1, 0, 0]] if view == 'tp' else [[0, 0, -1], [0, 1, 0], [1, 0, 0]]

        def xf(p):
            q = apply(Rd, [(p[i] - 8) * sd for i in range(3)])
            q = [q[i] + td[i] for i in range(3)]
            return apply(view_m, q)
        scale_px = size / 24.0
    else:
        def xf(p):
            q = apply(R, [(p[i] - 8) * s for i in range(3)])
            return [q[i] + t[i] for i in range(3)]
        scale_px = size / 16.0 if view in ('gui', 'fixed') else size / 18.0

    img = [[(0, 0, 0, 0)] * size for _ in range(size)]
    zb = [[-1e9] * size for _ in range(size)]
    tris = []
    for el in m.get('elements', []):
        for face, fd in el['faces'].items():
            tx = textures[fd['texture'][1:]]
            im = textures.setdefault('__img_' + tx, None) or tex(tx)
            textures['__img_' + tx] = im
            u0, v0, u1, v1 = fd.get('uv', face_auto_rect(el, face))
            au0, av0, au1, av1 = face_auto_rect(el, face)
            cs = corners(el, face)
            pts = []
            for c in cs:
                au, av = auto_uv(c, face)
                fu = (au - au0) / max(au1 - au0, 1e-9)
                fv = (av - av0) / max(av1 - av0, 1e-9)
                uu = (u0 + (u1 - u0) * fu) / 16 * im.width
                vv = (v0 + (v1 - v0) * fv) / 16 * im.height
                p = xf(c)
                pts.append((size / 2 + p[0] * scale_px, size / 2 - p[1] * scale_px, p[2], uu, vv))
            emit = 'forge_data' in el
            shade = 1.0 if emit or el.get('shade') is False else SHADE[face]
            tris.append((pts[0], pts[1], pts[2], im, shade))
            tris.append((pts[0], pts[2], pts[3], im, shade))
    translucent = m.get('render_type', '').endswith('translucent')
    later = []
    for tr in tris:
        draw(tr, img, zb, size, later, translucent)
    later.sort(key=lambda e: e[0])
    for z, x, y, c in later:
        if z < zb[y][x] - 1e-6:
            continue
        o = img[y][x]
        a = c[3] / 255.0
        img[y][x] = (int(c[0] * a + o[0] * (1 - a)), int(c[1] * a + o[1] * (1 - a)), int(c[2] * a + o[2] * (1 - a)),
                     max(o[3], c[3]))
    out = Image.new('RGBA', (size, size))
    out.putdata([p for row in img for p in row])
    return out


def draw(tr, img, zb, size, later, translucent):
    (ax, ay, az, au, av), (bx, by, bz, bu, bv), (cx, cy, cz, cu, cv), im, shade = tr
    minx, maxx = max(0, int(min(ax, bx, cx))), min(size - 1, int(max(ax, bx, cx)) + 1)
    miny, maxy = max(0, int(min(ay, by, cy))), min(size - 1, int(max(ay, by, cy)) + 1)
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9:
        return
    px = im.load()
    W, H = im.width, im.height
    for y in range(miny, maxy + 1):
        for x in range(minx, maxx + 1):
            fx, fy = x + 0.5, y + 0.5
            w1 = ((by - cy) * (fx - cx) + (cx - bx) * (fy - cy)) / den
            w2 = ((cy - ay) * (fx - cx) + (ax - cx) * (fy - cy)) / den
            w3 = 1 - w1 - w2
            if w1 < -1e-6 or w2 < -1e-6 or w3 < -1e-6:
                continue
            z = w1 * az + w2 * bz + w3 * cz
            u = w1 * au + w2 * bu + w3 * cu
            v = w1 * av + w2 * bv + w3 * cv
            c = px[min(W - 1, max(0, int(u))), min(H - 1, max(0, int(v)))]
            if c[3] == 0:
                continue
            c = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), c[3])
            if translucent and c[3] < 250:
                later.append((z, x, y, c))
                continue
            if z > zb[y][x]:
                zb[y][x] = z
                img[y][x] = c


def main():
    out = sys.argv[1]
    specs = sys.argv[2:]
    tiles = []
    for sp in specs:
        name, _, view = sp.partition(':')
        rl = None
        if name.startswith('block/'):
            rl = 'rplights:' + name
        tiles.append(render(name, view or 'gui', model_rl=rl))
    cols = min(6, len(tiles))
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * 256, rows * 256), (139, 139, 139, 255))
    for i, t in enumerate(tiles):
        sheet.alpha_composite(t, ((i % cols) * 256, (i // cols) * 256))
    sheet.save(out)


if __name__ == '__main__':
    main()
