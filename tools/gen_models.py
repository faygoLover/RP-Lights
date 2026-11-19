"""3D-модели RP Lights: фонари, лампа (предмет и блок), налобный на игроке, батарейки, тестер, зарядка, маркер.

Модель — коробки на сетке 0,5 единицы (1 единица = 1/16 блока), у каждой модели своя текстура-атлас,
плотность ровно D = 2 текселя на единицу на всех гранях (пиксель вдвое мельче ванильного, сетка ровная).
Круглое — ступенчатое сечение из коробок по пиксельному кругу, без поворотов. Каждый тексель красится по своей
точке в пространстве модели, поэтому перекрывающиеся грани ступенек совпадают, а узор идёт через стыки.

Запуск из корня репозитория: python tools/gen_models.py
"""
import json
import math
import os

from PIL import Image

D = 2  # текселей на единицу модели

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rplights')
TEX = os.path.join(ROOT, 'textures', 'item', 'model')
MOD_ITEM = os.path.join(ROOT, 'models', 'item')
MOD_BLOCK = os.path.join(ROOT, 'models', 'block')
STATES = os.path.join(ROOT, 'blockstates')
for d in (TEX, MOD_ITEM, MOD_BLOCK, STATES):
    os.makedirs(d, exist_ok=True)


# ------------------------------------------------------------------------------------------------ цвет

def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def mul(c, f):
    return (max(0, min(255, int(round(c[0] * f)))), max(0, min(255, int(round(c[1] * f)))),
            max(0, min(255, int(round(c[2] * f)))), c[3])


def hsh(*v):
    h = 2166136261
    for x in v:
        h = ((h ^ (int(x) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 0x5bd1e995) & 0xFFFFFFFF
    h ^= h >> 15
    return (h & 0xFFFF) / 65535.0


# ------------------------------------------------------------------------------------------------ контекст текселя

class Tx:
    """Тексель грани: точка P (единицы модели), нормаль n, край грани, коробка, целые координаты t."""
    __slots__ = ('P', 'n', 'edge', 'box', 't')

    def __init__(self, P, n, edge, box):
        self.P, self.n, self.edge, self.box = P, n, edge, box
        self.t = tuple(int(math.floor(P[i] * D)) if n[i] == 0 else int(round(P[i] * D)) * 7 + (1 if n[i] > 0 else 0)
                       for i in range(3))

    def noise(self, salt=0):
        return hsh(self.t[0], self.t[1], self.t[2], salt)

    def axial(self):
        """Ось цилиндра коробки и радиальный вектор в сечении (единичный), расстояние до оси."""
        cyl = self.box.get('cyl')
        if not cyl:
            return None
        axis, c1, c2, r = cyl
        P = self.P
        if axis == 'x':
            a, b = P[1] - c1, P[2] - c2
        elif axis == 'y':
            a, b = P[0] - c1, P[2] - c2
        else:
            a, b = P[0] - c1, P[1] - c2
        d = math.hypot(a, b)
        return axis, (a / d, b / d) if d > 1e-6 else (0.0, 0.0), d, r

    def along_axis(self):
        cyl = self.box.get('cyl')
        if not cyl:
            return False
        i = 'xyz'.index(cyl[0])
        return self.n[i] != 0


LIGHT2D = {'z': (-0.55, 0.83), 'x': (0.83, -0.55), 'y': (-0.6, -0.8)}


def roundness(tx, k=0.3):
    """Множитель «круглости»: сторона цилиндра к свету светлее, от света темнее."""
    ax = tx.axial()
    if ax is None or tx.along_axis():
        return 1.0
    axis, (a, b), _, _ = ax
    l = LIGHT2D[axis]
    d = a * l[0] + b * l[1]
    spec = max(0.0, d) ** 6 * 0.35
    return 0.86 + k * d + spec


# ------------------------------------------------------------------------------------------------ материалы

def paint(base, noise=0.05, edge=-0.12, wear=0.0, wear_col='#3A3A38', k=0.3):
    base, wear_col = hexc(base), hexc(wear_col)

    def f(tx):
        c = base
        if wear and tx.noise(3) < wear and (tx.edge or tx.noise(4) < 0.35):
            c = wear_col
        f_ = roundness(tx, k) * (1.0 + (tx.noise() - 0.5) * 2 * noise)
        if tx.edge and not tx.box.get('cyl'):
            f_ *= 1.0 + edge
        return mul(c, f_)
    return f


def chrome(dark='#4E555C', light='#EEF3F7', mid='#9AA3AB'):
    dark, light, mid = hexc(dark), hexc(light), hexc(mid)

    def f(tx):
        ax = tx.axial()
        if ax is None or tx.along_axis():
            c = mix(mid, light, 0.35 + tx.noise() * 0.2)
            return mul(c, 0.92 if tx.edge and ax is None else 1.0)
        axis, (a, b), _, _ = ax
        l = LIGHT2D[axis]
        d = a * l[0] + b * l[1]
        # отражение: тёмная полоса горизонта, яркий блик к свету
        if d > 0.55:
            c = mix(mid, light, (d - 0.55) / 0.45 * 1.2)
        elif d > -0.1:
            c = mix(dark, mid, (d + 0.1) / 0.65)
        else:
            c = mix(mid, dark, min(1.0, (-d) * 1.6))
        return mul(c, 1.0 + (tx.noise() - 0.5) * 0.06)
    return f


def knurl(base_mat, dark=0.72):
    def f(tx):
        c = base_mat(tx)
        q = tx.t
        if (q[0] + q[1] + q[2]) % 2 == 0:
            c = mul(c, dark)
        return c
    return f


def ribs(base_mat, period=2, along='z', dark=0.75):
    i = 'xyz'.index(along)

    def f(tx):
        c = base_mat(tx)
        if tx.n[i] == 0 and int(math.floor(tx.P[i] * D)) % period == 0:
            c = mul(c, dark)
        return c
    return f


def lens(off=True, tint='#FFF4D8', rim='#2A2A2A', reflector='#CFD5DA', glass='#9FB2BF', core=0.45, emit=False):
    tint, rim, reflector, glass = hexc(tint), hexc(rim), hexc(reflector), hexc(glass)

    def f(tx):
        ax = tx.axial()
        if ax is None:
            return glass
        _, (a, b), d, r = ax
        rr = d / max(r, 1e-3)
        if d > r - 0.5:
            return mul(rim, 1.0 + (tx.noise() - 0.5) * 0.1)
        if off:
            c = mix(glass, reflector, rr ** 1.5)
            if rr < core * 0.6:
                c = mix(hexc('#E9D9A6'), glass, rr / (core * 0.6))
            # блик стекла сверху слева
            if a < -0.2 and b > 0.25 and 0.3 < rr < 0.8:
                c = mix(c, hexc('#FFFFFF'), 0.6)
            return c
        c = mix(hexc('#FFFFFF'), tint, (rr / max(core, 1e-3)) ** 1.2)
        if rr > core:
            c = mix(tint, mul(tint, 0.82), (rr - core) / (1 - core))
        return c
    return f


def glass(col='#D7E8EE', alpha=92, streak=210):
    col = hexc(col, alpha)

    def f(tx):
        c = col
        ax = tx.axial()
        if ax is not None and not tx.along_axis():
            axis, (a, b), _, _ = ax
            l = LIGHT2D[axis]
            d = a * l[0] + b * l[1]
            if d > 0.75:
                return (245, 252, 255, streak)
            if d < -0.6:
                return (col[0], col[1], col[2], int(alpha * 1.35))
        if tx.edge and ax is None:
            return (col[0], col[1], col[2], int(alpha * 1.5))
        return c
    return f


def flat(col):
    c = hexc(col) if isinstance(col, str) else col
    return lambda tx: c


def label(base, stripe, lo, hi, axis='y', text_col=None):
    """Обёртка батарейки: основа, полоса lo..hi по оси, редкие «буквы» на полосе."""
    base_m = paint(base, noise=0.03, k=0.25)
    stripe_m = paint(stripe, noise=0.03, k=0.25)
    i = 'xyz'.index(axis)
    tc = hexc(text_col) if text_col else None

    def f(tx):
        p = tx.P[i]
        if lo <= p <= hi and tx.n[i] == 0:
            c = stripe_m(tx)
            if tc and tx.noise(9) < 0.22 and lo + 0.5 < p < hi - 0.5:
                c = tc
            return c
        return base_m(tx)
    return f


# ------------------------------------------------------------------------------------------------ модель

DIRS = {
    'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0),
    'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0),
}


def snap(v):
    r = round(v * D) / D
    assert abs(r - v) < 1e-6, f'не на сетке: {v}'
    return r


class Model:
    def __init__(self, name):
        self.name = name
        self.boxes = []

    def box(self, a, b, mat, cap=None, cyl=None, emit=False, occlude=True, shade=True, skip=(), tag=None):
        a = tuple(snap(v) for v in a)
        b = tuple(snap(v) for v in b)
        assert all(b[i] > a[i] for i in range(3)), (self.name, a, b)
        self.boxes.append({'a': a, 'b': b, 'mat': mat, 'cap': cap, 'cyl': cyl, 'emit': emit,
                           'occlude': occlude, 'shade': shade, 'skip': set(skip), 'tag': tag})
        return self

    def cyl(self, axis, c1, c2, r, lo, hi, mat, cap=None, **kw):
        """Цилиндр вдоль оси: c1, c2 — центр в сечении (для z: x, y; для x: y, z; для y: x, z)."""
        for hw, hh in disk(r):
            if axis == 'z':
                a, b = (c1 - hw, c2 - hh, lo), (c1 + hw, c2 + hh, hi)
            elif axis == 'x':
                a, b = (lo, c1 - hw, c2 - hh), (hi, c1 + hw, c2 + hh)
            else:
                a, b = (c1 - hw, lo, c2 - hh), (c1 + hw, hi, c2 + hh)
            self.box(a, b, mat, cap=cap, cyl=(axis, c1, c2, r), **kw)
        return self

    def shifted(self, dy):
        m = Model(self.name)
        for bx in self.boxes:
            n = dict(bx)
            n['a'] = (bx['a'][0], bx['a'][1] + dy, bx['a'][2])
            n['b'] = (bx['b'][0], bx['b'][1] + dy, bx['b'][2])
            if bx['cyl'] and bx['cyl'][0] in 'xz':
                ax, c1, c2, r = bx['cyl']
                n['cyl'] = (ax, c1 + dy, c2, r) if ax == 'x' else (ax, c1, c2 + dy, r)
            n['skip'] = set(bx['skip'])
            m.boxes.append(n)
        return m

    def bounds(self):
        lo = [min(bx['a'][i] for bx in self.boxes) for i in range(3)]
        hi = [max(bx['b'][i] for bx in self.boxes) for i in range(3)]
        return lo, hi


def disk(r):
    """Пиксельный круг радиуса r на сетке 1/D: список прямоугольников (полуширина, полувысота)."""
    cells = int(round(r * D))
    rows = []
    for j in range(cells):
        y = (j + 0.5) / D
        w = 0
        for i in range(cells):
            x = (i + 0.5) / D
            if x * x + y * y <= r * r + 0.15 / D:
                w = i + 1
        rows.append(w / D)
    rects = []
    for j, w in enumerate(rows):
        hh = (j + 1) / D
        if j + 1 < len(rows) and rows[j + 1] == w:
            continue
        if w > 0:
            rects.append((w, hh))
    return rects


def face_rect(bx, name):
    a, b = bx['a'], bx['b']
    if name == 'north':
        return (16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1])
    if name == 'south':
        return (a[0], 16 - b[1], b[0], 16 - a[1])
    if name == 'west':
        return (a[2], 16 - b[1], b[2], 16 - a[1])
    if name == 'east':
        return (16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1])
    if name == 'up':
        return (a[0], a[2], b[0], b[2])
    return (a[0], 16 - b[2], b[0], 16 - a[2])


def texel_point(bx, name, u, v):
    a, b = bx['a'], bx['b']
    if name == 'north':
        return (16 - u, 16 - v, a[2])
    if name == 'south':
        return (u, 16 - v, b[2])
    if name == 'west':
        return (a[0], 16 - v, u)
    if name == 'east':
        return (b[0], 16 - v, 16 - u)
    if name == 'up':
        return (u, b[1], v)
    return (u, a[1], 16 - v)


def hidden(bx, name, others):
    """Грань целиком внутри (или на поверхности) другой непрозрачной коробки."""
    n = DIRS[name]
    i = [k for k in range(3) if n[k] != 0][0]
    plane = bx['b'][i] if n[i] > 0 else bx['a'][i]
    js = [k for k in range(3) if k != i]
    for o in others:
        if o is bx or not o['occlude'] or o['emit'] != bx['emit']:
            continue
        if not (o['a'][i] <= plane <= o['b'][i]):
            continue
        # грань на поверхности соседа, смотрящая наружу из него, — видна
        if (n[i] > 0 and plane == o['b'][i]) or (n[i] < 0 and plane == o['a'][i]):
            continue
        if all(o['a'][j] <= bx['a'][j] and bx['b'][j] <= o['b'][j] for j in js):
            return True
    return False


def build(model, tex_name, render_type=None, display=None, parent=None, extra=None):
    faces = []
    for bx in model.boxes:
        for name in DIRS:
            if name in bx['skip'] or hidden(bx, name, model.boxes):
                continue
            u0, v0, u1, v1 = face_rect(bx, name)
            w, h = int(round((u1 - u0) * D)), int(round((v1 - v0) * D))
            if w <= 0 or h <= 0:
                continue
            faces.append([bx, name, (u0, v0, u1, v1), w, h, None])
    # упаковка полками
    size = 16
    while True:
        size *= 2
        x = y = row = 0
        ok = True
        for f in sorted(faces, key=lambda f: (-f[4], -f[3])):
            if x + f[3] > size:
                x, y, row = 0, y + row, 0
            if f[3] > size or y + f[4] > size:
                ok = False
                break
            f[5] = (x, y)
            x += f[3]
            row = max(row, f[4])
        if ok:
            break
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    # совпадающие грани: одна плоскость, одна сторона — у разных коробок цвет должен совпадать, иначе мерцание
    seen = {}
    clashes = set()

    def cell_of(name, P):
        return (name,) + tuple(round(P[q] * D * 2) for q in range(3))

    # край — по общему силуэту плоскости (все грани той же стороны), а не по коробке: перекрытые грани красятся одинаково
    covered = set()
    for bx, name, (u0, v0, u1, v1), w, h, _ in faces:
        for j in range(h):
            for i in range(w):
                covered.add(cell_of(name, texel_point(bx, name, u0 + (i + 0.5) / D, v0 + (j + 0.5) / D)))

    def is_edge(name, P):
        c = cell_of(name, P)
        n = DIRS[name]
        for q in range(3):
            if n[q] != 0:
                continue
            for d in (-2, 2):
                nb = list(c)
                nb[q + 1] += d
                if tuple(nb) not in covered:
                    return True
        return False

    for bx, name, (u0, v0, u1, v1), w, h, (ox, oy) in faces:
        n = DIRS[name]
        mat = bx['cap'] if bx['cap'] and bx['cyl'] and n['xyz'.index(bx['cyl'][0])] != 0 else bx['mat']
        for j in range(h):
            for i in range(w):
                P = texel_point(bx, name, u0 + (i + 0.5) / D, v0 + (j + 0.5) / D)
                edge = is_edge(name, P)
                c = mat(Tx(P, n, edge, bx))
                img.putpixel((ox + i, oy + j), c)
                cell = (name, round(P[0] * D * 2), round(P[1] * D * 2), round(P[2] * D * 2))
                prev = seen.get(cell)
                if prev is None:
                    seen[cell] = (id(bx), c, bx['emit'])
                elif prev[0] != id(bx) and (prev[1] != c or prev[2] != bx['emit']):
                    clashes.add((name, bx['a'], bx['b']))
    if clashes:
        raise SystemExit(f'{tex_name}: грани в одной плоскости с разным видом (мерцание): {sorted(clashes)[:6]}')
    img.save(os.path.join(TEX, tex_name + '.png'))

    k = 16.0 / size
    elements = []
    by_box = {}
    for f in faces:
        by_box.setdefault(id(f[0]), (f[0], []))[1].append(f)
    for bx, fs in by_box.values():
        el = {'from': list(bx['a']), 'to': list(bx['b']), 'faces': {}}
        for _, name, _, w, h, (ox, oy) in fs:
            el['faces'][name] = {'uv': [r6(ox * k), r6(oy * k), r6((ox + w) * k), r6((oy + h) * k)], 'texture': '#t'}
        if not bx['shade']:
            el['shade'] = False
        if bx['emit']:
            el['shade'] = False
            el['forge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
        elements.append(el)
    out = {}
    if parent:
        out['parent'] = parent
    if render_type:
        out['render_type'] = render_type
    out['textures'] = {'t': f'rplights:item/model/{tex_name}', 'particle': f'rplights:item/model/{tex_name}'}
    out['elements'] = elements
    if display:
        out['gui_light'] = 'side'
        out['display'] = display
    if extra:
        out.update(extra)
    return out


def r6(v):
    v = round(v, 6)
    return int(v) if v == int(v) else v


# ------------------------------------------------------------------------------------------------ положение в руке

def rot_xyz(rx, ry, rz):
    def X(t):
        c, s = math.cos(t), math.sin(t)
        return [[1, 0, 0], [0, c, -s], [0, s, c]]

    def Y(t):
        c, s = math.cos(t), math.sin(t)
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

    def Z(t):
        c, s = math.cos(t), math.sin(t)
        return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

    def mm(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    r = math.radians
    return mm(mm(X(r(rx)), Y(r(ry))), Z(r(rz)))


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def place(rot, scale, point, at):
    """Перенос, чтобы точка модели point оказалась в at (единицы 1/16 от опоры)."""
    R = rot_xyz(*rot)
    p = apply(R, [(point[i] - 8) * scale for i in range(3)])
    return {'rotation': list(rot), 'translation': [r6(round(at[i] - p[i], 3)) for i in range(3)],
            'scale': [scale] * 3}


def fit(model, rot, fill, all_axes=False):
    """Масштаб и перенос, чтобы модель заняла fill единиц в кадре (иконка, рамка, земля) по центру."""
    lo, hi = model.bounds()
    R = rot_xyz(*rot)
    pts = [apply(R, [(c[i] - 8) for i in range(3)]) for c in
           [(x, y, z) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]]
    axes = (0, 1, 2) if all_axes else (0, 1)
    ext = max(max(p[i] for p in pts) - min(p[i] for p in pts) for i in axes)
    s = round(min(2.0, fill / ext), 3)
    centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
    return place(rot, s, centre, (0, 0, 0))


def gui_rot(model, forward=(0, 0, -1)):
    """Поворот иконки: «вперёд» модели — вверх-вправо по диагонали, сверху виден верх."""
    best = None
    for rx in range(-90, 91, 15):
        for ry in range(-180, 180, 15):
            for rz in range(-90, 91, 15):
                R = rot_xyz(rx, ry, rz)
                f = apply(R, forward)
                up = apply(R, (0, 1, 0))
                cost = (f[0] - 0.55) ** 2 + (f[1] - 0.5) ** 2 + (f[2] - 0.67) ** 2
                if up[1] < 0.35:
                    cost += 1.0
                cost += max(0.0, 0.3 - up[2]) * 0.5
                if best is None or cost < best[0]:
                    best = (cost, (rx, ry, rz))
    return best[1]


def held(model, grip, fp_scale=0.68, tp_scale=0.62, fp_at=(0.0, 2.0, 0.0), tp_at=(0.0, 0.0, 0.5),
         fp_rot=(3, 7, 0), gui_fill=15.0, gui_rotation=None):
    """Фонарь «вперёд» по −Z, верх +Y. Третье лицо: кадр руки — +Y вперёд, +Z вверх по руке → поворот X 90."""
    gr = gui_rotation or gui_rot(model)
    gui = fit(model, gr, gui_fill)
    fixed_rot = (0, 180, 0)
    fr = (gr[0], gr[1] + 180, gr[2])
    d = {
        'thirdperson_righthand': place((90, 0, 0), tp_scale, grip, tp_at),
        'thirdperson_lefthand': place((90, 0, 0), tp_scale, grip, tp_at),
        'firstperson_righthand': place(fp_rot, fp_scale, grip, fp_at),
        'firstperson_lefthand': place(fp_rot, fp_scale, grip, fp_at),
        'gui': gui,
        'ground': fit(model, (0, 0, 0), 7.0, True),
        'fixed': fit(model, fr, 12.0),
        'head': fit(model, (0, 180, 0), 10.0, True),
    }
    d['ground']['translation'][1] = r6(d['ground']['translation'][1] + 2)
    return d


def thing(model, gui_rotation=(30, 225, 0), gui_fill=14.0):
    """Небольшая вещь (батарейка, лампочка, тестер): держится как блок, иконка — «блоком»."""
    lo, hi = model.bounds()
    centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
    return {
        'thirdperson_righthand': place((75, 45, 0), 0.5, centre, (0, 2.5, 0)),
        'thirdperson_lefthand': place((75, 45, 0), 0.5, centre, (0, 2.5, 0)),
        'firstperson_righthand': place((0, 45, 0), 0.6, centre, (0, 2.5, 0)),
        'firstperson_lefthand': place((0, 45, 0), 0.6, centre, (0, 2.5, 0)),
        'gui': fit(model, gui_rotation, gui_fill),
        'ground': place((0, 0, 0), 0.5, centre, (0, 2, 0)),
        'fixed': fit(model, (0, 180, 0), 11.0),
        'head': fit(model, (0, 180, 0), 10.0, True),
    }


def jdump(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write('\n')


# ------------------------------------------------------------------------------------------------ фонари

BLACK = paint('#232427', noise=0.05, edge=0.12, k=0.25)
BLACK_RUBBER = paint('#1B1B1C', noise=0.08, edge=0.06, k=0.2)
STEEL = paint('#6D747B', noise=0.06, edge=-0.12, k=0.35)
DARK_STEEL = paint('#3E4348', noise=0.06, edge=0.1, k=0.3)
BRASS = paint('#B48A3C', noise=0.08, edge=-0.12, k=0.35)


def hand_lantern(on):
    """Ручной фонарь 6 В: жёлтый корпус, чёрная голова с круглым стеклом, ручка сверху."""
    m = Model('hand_lantern')
    yellow = paint('#E7B521', noise=0.04, edge=-0.14, wear=0.035, wear_col='#5A4A26', k=0.2)
    # корпус (батарейный ящик) со скруглёнными рёбрами
    m.box((5.0, 3.0, 7.5), (11.0, 9.0, 14.0), yellow)
    m.box((4.5, 3.5, 7.5), (11.5, 8.5, 14.0), yellow)
    m.box((5.0, 2.5, 8.0), (11.0, 9.5, 13.5), yellow)
    # тыльная крышка
    m.box((5.5, 3.5, 14.0), (10.5, 8.5, 14.5), paint('#C99A17', noise=0.04, edge=-0.15))
    # голова: чёрный обод и раструб
    m.cyl('z', 8.0, 6.0, 3.5, 5.0, 7.5, BLACK, cap=BLACK)
    m.cyl('z', 8.0, 6.0, 4.0, 4.0, 5.0, BLACK, cap=BLACK)
    m.cyl('z', 8.0, 6.0, 3.5, 3.5, 4.0, BLACK,
          cap=lens(off=not on, tint='#FFD9A0', glass='#B9C2C4', reflector='#D9DDDF', core=0.5),
          emit=on)
    # ручка: стойки и перекладина с рёбрами
    m.box((7.0, 9.5, 8.5), (9.0, 11.5, 9.5), BLACK)
    m.box((7.0, 9.5, 12.0), (9.0, 11.5, 13.0), BLACK)
    m.box((7.0, 11.5, 8.5), (9.0, 13.0, 13.0), ribs(BLACK, period=2, along='z', dark=0.8))
    # выключатель
    m.box((7.5, 9.5, 10.5), (8.5, 10.0, 11.5), paint('#B23A2E', noise=0.05))
    return m


def old_flashlight(on):
    """Старый хромированный: трубка с насечкой, кнопка, раструб, светодиод за стеклом."""
    m = Model('old_flashlight')
    ch = chrome()
    m.cyl('z', 8.0, 8.0, 1.5, 14.5, 15.5, ch, cap=paint('#8E979F', noise=0.06))       # тыльный колпачок
    m.cyl('z', 8.0, 8.0, 1.0, 15.5, 16.0, ch, cap=paint('#B9C1C8', noise=0.05))       # кольцо под темляк
    m.cyl('z', 8.0, 8.0, 1.5, 7.5, 14.5, knurl(ch, 0.8), cap=ch)                       # трубка
    m.cyl('z', 8.0, 8.0, 2.0, 5.0, 7.5, ch, cap=ch)                                    # шейка
    m.cyl('z', 8.0, 8.0, 2.5, 3.0, 5.0, ch, cap=ch)                                    # голова
    m.cyl('z', 8.0, 8.0, 2.5, 2.5, 3.0, ch,
          cap=lens(off=not on, tint='#EEF4FF', glass='#AFC0CC', reflector='#E7ECF0', core=0.32), emit=on)
    m.box((7.5, 9.5, 9.0), (8.5, 10.0, 10.5), paint('#2E2F33', noise=0.06))           # кнопка
    return m


def headlamp_parts(m, on, cx, cy, z_front, strap_mat):
    """Корпус налобного: лампа с линзой, лицом к −Z, центр линзы (cx, cy), перед на z_front."""
    housing = paint('#3A3D42', noise=0.05, edge=0.1, k=0.2)
    m.box((cx - 2.0, cy - 1.5, z_front + 0.5), (cx + 2.0, cy + 1.5, z_front + 2.5), housing)
    m.box((cx - 1.5, cy - 2.0, z_front + 0.5), (cx + 1.5, cy + 2.0, z_front + 2.5), housing)
    m.cyl('z', cx, cy, 1.5, z_front, z_front + 0.5, housing,
          cap=lens(off=not on, tint='#FFF1DC', glass='#A8B8C2', reflector='#DCE2E6', core=0.4), emit=on)
    m.box((cx + 0.5, cy + 2.0, z_front + 1.0), (cx + 1.5, cy + 2.5, z_front + 2.0), paint('#C9452E', noise=0.05))


def headlamp(on):
    """Предмет: петля резинки и лампа спереди."""
    m = Model('headlamp')
    strap = ribs(paint('#2F3B45', noise=0.08, edge=0.0, k=0.1), period=3, along='x', dark=0.82)
    strap_z = ribs(paint('#2F3B45', noise=0.08, edge=0.0, k=0.1), period=3, along='z', dark=0.82)
    m.box((3.0, 7.0, 4.5), (13.0, 9.0, 5.0), strap)
    m.box((3.0, 7.0, 13.5), (13.0, 9.0, 14.0), strap)
    m.box((3.0, 7.0, 5.0), (3.5, 9.0, 13.5), strap_z)
    m.box((12.5, 7.0, 5.0), (13.0, 9.0, 13.5), strap_z)
    # батарейный блок сзади
    m.box((6.0, 6.5, 14.0), (10.0, 9.5, 15.5), paint('#3A3D42', noise=0.05, edge=0.1))
    headlamp_parts(m, on, 8.0, 8.0, 2.0, strap)
    return m


def headlamp_worn_head(on):
    """На голове (координаты головы игрока: x 4..12, y 8..16, z 4..12, лицо к −Z)."""
    m = Model('headlamp_worn_head')
    strap = ribs(paint('#2F3B45', noise=0.08, edge=0.0, k=0.1), period=3, along='x', dark=0.82)
    strap_z = ribs(paint('#2F3B45', noise=0.08, edge=0.0, k=0.1), period=3, along='z', dark=0.82)
    y0, y1 = 13.0, 14.5
    m.box((3.5, y0, 3.5), (12.5, y1, 4.0), strap)
    m.box((3.5, y0, 12.0), (12.5, y1, 12.5), strap)
    m.box((3.5, y0, 4.0), (4.0, y1, 12.0), strap_z)
    m.box((12.0, y0, 4.0), (12.5, y1, 12.0), strap_z)
    # верхний ремешок через макушку
    m.box((7.25 + 0.25, 16.0, 3.5), (8.5, 16.5, 12.5), strap_z)
    m.box((7.5, y1, 3.5), (8.5, 16.0, 4.0), strap)
    m.box((7.5, y1, 12.0), (8.5, 16.0, 12.5), strap)
    # батарейный блок на затылке
    m.box((6.0, 12.5, 12.5), (10.0, 15.0, 13.5), paint('#3A3D42', noise=0.05, edge=0.1))
    headlamp_parts(m, on, 8.0, 14.0, 1.0, strap)
    return m


def headlamp_worn_chest(on):
    """На груди (координаты тела: x 4..12, y −4..8, z 6..10, грудь к −Z)."""
    m = Model('headlamp_worn_chest')
    strap = ribs(paint('#2F3B45', noise=0.08, edge=0.0, k=0.1), period=3, along='y', dark=0.82)
    strap_z = ribs(paint('#2F3B45', noise=0.08, edge=0.0, k=0.1), period=3, along='z', dark=0.82)
    for x0 in (5.0, 10.0):
        m.box((x0, -1.0, 5.5), (x0 + 1.0, 8.0, 6.0), strap)          # спереди
        m.box((x0, 8.0, 5.5), (x0 + 1.0, 8.5, 10.5), strap_z)        # через плечо
        m.box((x0, 1.0, 10.0), (x0 + 1.0, 8.0, 10.5), strap)         # сзади
    m.box((5.0, 3.0, 5.0), (11.0, 4.0, 5.5), strap)                  # поперечная
    headlamp_parts(m, on, 8.0, 4.0, 2.5, strap)
    return m


def dynamo(on):
    """Динамо-фонарь «жучок»: овальный корпус, рычаг снизу, линза спереди."""
    m = Model('dynamo_flashlight')
    body = paint('#4B5A4A', noise=0.05, edge=-0.12, k=0.25, wear=0.02)
    m.box((6.0, 6.5, 7.0), (10.0, 10.0, 13.0), body)
    m.box((5.5, 7.0, 7.5), (10.5, 9.5, 12.5), body)
    m.box((6.5, 6.0, 7.5), (9.5, 10.5, 12.5), body)
    m.cyl('z', 8.0, 8.25 - 0.25, 2.0, 5.5, 7.0, DARK_STEEL, cap=DARK_STEEL)
    m.cyl('z', 8.0, 8.0, 2.0, 5.0, 5.5, DARK_STEEL,
          cap=lens(off=not on, tint='#FFDDB0', glass='#B6BFC2', reflector='#D8DDDF', core=0.45), emit=on)
    # рычаг: ось спереди, лапка вдоль низа
    lever = chrome(dark='#55595D', light='#E3E7EA', mid='#9DA3A8')
    m.box((6.5, 5.0, 7.0), (9.5, 6.0, 8.0), DARK_STEEL)
    m.box((6.5, 4.5, 8.0), (9.5, 5.5, 13.5), ribs(lever, period=2, along='z', dark=0.85))
    m.box((7.0, 5.5, 12.5), (9.0, 6.0, 13.5), lever)
    # петля для пальца сзади
    m.box((7.0, 7.5, 13.0), (9.0, 9.0, 14.0), body)
    return m


def lamp_model(on, name='hurricane_lamp'):
    """«Летучая мышь» в размере блока: бак, стекло в проволоке, колпак, боковые трубки, дужка."""
    m = Model(name)
    tin = paint('#3F5E44', noise=0.05, edge=-0.12, k=0.3, wear=0.05, wear_col='#7C7468')
    tin_dark = paint('#2E4532', noise=0.05, edge=-0.1, k=0.3, wear=0.04, wear_col='#6E665B')
    wire = paint('#5D6266', noise=0.05, edge=0.0, k=0.2)
    # бак
    m.cyl('y', 8.0, 8.0, 3.0, 0.0, 2.0, tin, cap=paint('#365239', noise=0.05, k=0.0))
    m.cyl('y', 8.0, 8.0, 2.5, 2.0, 2.5, tin_dark, cap=tin_dark)
    # горелка, фитиль
    m.cyl('y', 8.0, 8.0, 1.0, 2.5, 3.0, BRASS, cap=BRASS)
    m.box((7.5, 3.0, 7.5), (8.5, 3.5, 8.5), paint('#2B2722', noise=0.1))
    # стекло
    lamp_glass = glass('#E6E2D2', alpha=70, streak=200)
    m.cyl('y', 8.0, 8.0, 2.0, 3.0, 7.0, lamp_glass, occlude=False)
    # проволочная защита стекла
    for x0, z0 in ((5.5, 7.75 - 0.25), (10.0, 7.75 - 0.25), (7.75 - 0.25, 5.5), (7.75 - 0.25, 10.0)):
        m.box((x0, 3.0, z0), (x0 + 0.5, 5.0, z0 + 0.5), wire)       # ниже и выше кольца
        m.box((x0, 5.5, z0), (x0 + 0.5, 7.0, z0 + 0.5), wire)
    for a, b in (((5.5, 5.0, 5.5), (10.5, 5.5, 6.0)), ((5.5, 5.0, 10.0), (10.5, 5.5, 10.5)),
                 ((5.5, 5.0, 6.0), (6.0, 5.5, 10.0)), ((10.0, 5.0, 6.0), (10.5, 5.5, 10.0))):
        m.box(a, b, wire)                                               # проволочное кольцо вокруг стекла
    # колпак и дымоход
    m.cyl('y', 8.0, 8.0, 2.5, 7.0, 8.0, tin, cap=tin)
    m.cyl('y', 8.0, 8.0, 1.5, 8.0, 8.5, tin_dark, cap=tin_dark)
    m.box((7.0, 8.5, 7.0), (9.0, 9.0, 9.0), tin_dark)
    # боковые трубки: от бака вверх к колпаку
    for x0 in (3.5, 12.0):
        m.box((x0, 1.0, 7.5), (x0 + 0.5, 8.0, 8.5), tin)
    m.box((4.0, 7.5, 7.5), (5.5, 8.0, 8.5), tin)
    m.box((10.5, 7.5, 7.5), (12.0, 8.0, 8.5), tin)
    m.box((4.0, 1.0, 7.5), (5.0, 1.5, 8.5), tin)
    m.box((11.0, 1.0, 7.5), (12.0, 1.5, 8.5), tin)
    # дужка
    m.box((3.5, 8.0, 7.5), (4.0, 10.5, 8.0), wire)
    m.box((12.0, 8.0, 7.5), (12.5, 10.5, 8.0), wire)
    m.box((3.5, 10.5, 7.5), (12.5, 11.0, 8.0), wire)
    m.box((6.5, 10.0, 7.0), (9.5, 11.5, 8.5), paint('#8E6A3A', noise=0.08))   # деревянная ручка вокруг дужки
    # ручка фитиля
    m.box((10.5, 2.0, 7.5), (11.5, 3.0, 8.5), BRASS)
    if on:
        flame_core = flat('#FFF1C2')
        flame = paint('#FFB347', noise=0.12, edge=0.0, k=0.0)
        m.box((7.5, 3.5, 7.5), (8.5, 5.0, 8.5), flame, emit=True, occlude=False)
        m.box((7.5, 5.0, 8.0), (8.0, 6.0, 8.5), flame_core, emit=True, occlude=False)
    return m


def uv_flashlight(on):
    m = Model('uv_flashlight')
    alu = paint('#1E1F24', noise=0.04, edge=0.15, k=0.4)
    m.cyl('z', 8.0, 8.0, 1.5, 14.5, 15.5, alu, cap=paint('#3A2F55', noise=0.04))      # кнопка-колпачок
    m.cyl('z', 8.0, 8.0, 1.5, 7.0, 14.5, ribs(alu, period=3, along='z', dark=0.75), cap=alu)
    m.cyl('z', 8.0, 8.0, 2.0, 3.5, 7.0, alu, cap=alu)
    m.cyl('z', 8.0, 8.0, 2.0, 3.0, 3.5, alu,
          cap=lens(off=not on, tint='#B07CFF', glass='#4A3B6E', reflector='#7E6AA8', core=0.5), emit=on)
    m.box((9.5, 7.5, 8.0), (10.0, 8.5, 13.0), paint('#9AA2AA', noise=0.04))           # клипса
    m.box((7.5, 10.0, 5.0), (8.5, 10.5, 6.0), paint('#8F63E0', noise=0.04))           # фиолетовое кольцо-метка
    return m


def rgb_flashlight(on):
    m = Model('rgb_flashlight')
    body = paint('#E4E4E0', noise=0.03, edge=-0.1, k=0.3)
    rainbow = ['#E8503A', '#F2A33A', '#F2DD3A', '#5CCB5A', '#3AA7E8', '#8A5AE8']

    def ring(tx):
        h = int((math.atan2(tx.P[1] - 8, tx.P[0] - 8) + math.pi) / (2 * math.pi) * 6) % 6
        return mul(hexc(rainbow[h]), roundness(tx, 0.2))

    def rgb_lens(tx):
        ax = tx.axial()
        _, (a, b), d, r = ax
        if d > r - 0.5:
            return hexc('#2A2A2A')
        h = int((math.atan2(b, a) + math.pi) / (2 * math.pi) * 6) % 6
        c = hexc(rainbow[h])
        return mix(c, hexc('#FFFFFF'), 0.55 if on else 0.15) if d < r * 0.35 else (c if on else mul(c, 0.7))
    m.cyl('z', 8.0, 8.0, 1.5, 14.5, 15.5, body, cap=paint('#CFCFCB', noise=0.03))
    m.cyl('z', 8.0, 8.0, 1.5, 7.0, 14.5, body, cap=body)
    m.cyl('z', 8.0, 8.0, 2.0, 5.0, 7.0, ring, cap=ring)
    m.cyl('z', 8.0, 8.0, 2.0, 3.0, 5.0, body, cap=body)
    m.cyl('z', 8.0, 8.0, 2.0, 2.5, 3.0, body, cap=rgb_lens, emit=on)
    m.box((7.5, 9.5, 9.0), (8.5, 10.0, 10.5), paint('#E8503A', noise=0.05))           # кнопка режима
    return m


# ------------------------------------------------------------------------------------------------ мелочи

def accumulator():
    m = Model('accumulator')
    wrap = label('#1F4E8C', '#E8E4D6', 6.0, 8.5, text_col='#1F4E8C')
    m.cyl('y', 8.0, 8.0, 2.0, 2.0, 11.0, wrap, cap=paint('#9AA2AA', noise=0.05, k=0.0))
    m.cyl('y', 8.0, 8.0, 1.5, 1.5, 2.0, STEEL, cap=STEEL)
    m.cyl('y', 8.0, 8.0, 0.5, 11.0, 12.0, STEEL, cap=paint('#C5CCD2', noise=0.04, k=0.0))
    return m


def bulb(burnt):
    m = Model('burnt_bulb' if burnt else 'bulb')
    thread = ribs(paint('#B9B2A0', noise=0.05, k=0.35), period=2, along='y', dark=0.72)
    m.cyl('y', 8.0, 8.0, 1.0, 2.0, 2.5, paint('#3A3A3A', noise=0.05), cap=paint('#B48A3C', noise=0.05))   # контакт
    m.cyl('y', 8.0, 8.0, 1.5, 2.5, 5.5, thread, cap=thread)
    # колба
    g0 = glass('#E9EEF0' if not burnt else '#BFC3C4', alpha=80, streak=200)
    soot = glass('#3B3530', alpha=150, streak=150)

    def g(tx):
        # у перегоревшей верх колбы в копоти
        return soot(tx) if burnt and tx.P[1] > 8.5 else g0(tx)
    m.cyl('y', 8.0, 8.0, 2.0, 5.5, 6.5, g, occlude=False)
    m.cyl('y', 8.0, 8.0, 3.0, 6.5, 10.0, g, occlude=False)
    m.cyl('y', 8.0, 8.0, 2.0, 10.0, 11.0, g, occlude=False)
    # нить
    fil = flat('#5B4A33') if not burnt else flat('#2A2622')
    m.box((7.5, 6.0, 8.0), (8.0, 9.0, 8.5), paint('#6E6A62', noise=0.05))
    m.box((8.0, 6.0, 7.5), (8.5, 9.0, 8.0), paint('#6E6A62', noise=0.05))
    if not burnt:
        m.box((7.5, 9.0, 7.5), (8.5, 9.5, 8.5), fil)
    else:
        m.box((7.5, 9.0, 7.5), (8.0, 9.5, 8.0), fil)
    return m, 'minecraft:translucent'


def tester():
    m = Model('battery_tester')
    case = paint('#C9C3B3', noise=0.04, edge=-0.12, k=0.0)
    m.box((4.0, 0.0, 3.0), (12.0, 2.5, 13.0), case)
    m.box((4.5, 2.5, 3.5), (11.5, 3.0, 8.0), paint('#3B3E42', noise=0.04, edge=0.1))      # рамка шкалы

    def dial(tx):
        x, z = tx.P[0], tx.P[2]
        c = hexc('#F2EEDF')
        # дуга шкалы: красный — зелёный
        dx, dz = x - 8.0, z - 7.75
        d = math.hypot(dx, dz)
        if 2.6 < d < 3.2 and dz < 0:
            c = hexc('#C4453A') if dx < -0.6 else (hexc('#E0B43A') if dx < 0.6 else hexc('#4FA552'))
        # стрелка
        ang = math.atan2(dz, dx)
        if d < 3.2 and dz < 0 and abs(ang + math.pi * 0.62) < 0.18:
            c = hexc('#1E1E1E')
        return c
    m.box((5.0, 3.0, 4.0), (11.0, 3.5, 7.5), dial, skip=('down',))
    # гнездо батарейки и щупы
    m.box((5.0, 2.5, 9.0), (11.0, 3.0, 12.0), paint('#2B2D30', noise=0.05))
    m.box((5.5, 2.5, 9.5), (6.5, 3.5, 11.5), paint('#B23A2E', noise=0.05))
    m.box((9.5, 2.5, 9.5), (10.5, 3.5, 11.5), paint('#2B2B2B', noise=0.05))
    return m


# обычные маркеры: цвет чернил (корпус-полоса, колпачок, стержень)
MARKER_COLOURS = {'black': '#26262A', 'red': '#C8302A', 'blue': '#2A52C8', 'green': '#2E8B3A',
                  'yellow': '#E6C62E', 'orange': '#E8822A', 'violet': '#7A3FB8', 'white': '#ECECEC'}


def uv_marker():
    """УФ-маркер: тонкий светлый корпус с фиолетовой полосой «УФ», фиолетовый колпачок. Всё рисунком, без накладок."""
    m = Model('uv_marker')
    violet = '#6B46B8'

    def body(tx):
        z = tx.P[2]
        if tx.n[2] == 0 and 5.0 <= z <= 6.5:
            c = paint(violet, noise=0.03, k=0.3)(tx)
            if 5.5 <= z <= 6.0 and tx.noise(5) < 0.3:
                c = hexc('#F2EEFF')
            return c
        return paint('#D8D8D2', noise=0.03, k=0.3)(tx)
    cap = paint(violet, noise=0.04, k=0.3)
    m.cyl('z', 8.0, 8.0, 1.0, 3.5, 13.0, body, cap=body)
    m.cyl('z', 8.0, 8.0, 1.5, 13.0, 15.0, cap, cap=cap)
    m.box((7.5, 9.5, 12.5), (8.5, 10.0, 14.5), cap)                                  # клипса над колпачком
    m.cyl('z', 8.0, 8.0, 0.5, 2.0, 3.5, paint('#3E2A6E', noise=0.08), cap=paint('#3E2A6E', noise=0.08))
    return m


def marker(colour):
    """Обычный маркер: толще, белый корпус с полосой цвета, колпачок и стержень цвета чернил."""
    m = Model('marker_' + colour)
    ink = MARKER_COLOURS[colour]
    trim = '#3A3A3E' if colour == 'white' else ink

    def body(tx):
        z = tx.P[2]
        if tx.n[2] == 0 and 6.0 <= z <= 11.0:
            return paint(trim, noise=0.03, k=0.3)(tx)
        return paint('#E6E6E0', noise=0.03, k=0.3)(tx)
    cap = paint(ink, noise=0.04, k=0.3)
    m.cyl('z', 8.0, 8.0, 1.5, 3.5, 12.5, body, cap=body)
    m.cyl('z', 8.0, 8.0, 2.0, 12.5, 15.5, cap, cap=cap)
    m.box((7.5, 10.0, 12.5), (8.5, 10.5, 15.0), cap)                                 # клипса над колпачком
    m.cyl('z', 8.0, 8.0, 1.0, 2.5, 3.5, paint('#BDBDB6', noise=0.05), cap=paint('#BDBDB6', noise=0.05))
    m.box((7.5, 7.5, 1.0), (8.5, 8.5, 2.5), paint(ink, noise=0.06, k=0.0))           # стержень-«долото»
    return m


def marker_case():
    """Пенал: тканевый брусок с молнией по верху, нашивка и бегунок."""
    m = Model('marker_case')
    cloth = paint('#3F4B3A', noise=0.06, k=0.3)
    zip_m = paint('#B8BCC0', noise=0.04, k=0.0)
    m.box((5.0, 5.0, 2.0), (11.0, 8.5, 14.0), cloth)
    m.box((5.5, 8.5, 2.5), (10.5, 9.0, 13.5), cloth)
    m.box((7.5, 9.0, 3.0), (8.5, 9.5, 13.0), zip_m)                                   # молния
    m.box((7.0, 9.0, 3.5), (9.0, 10.0, 4.5), paint('#8A8F94', noise=0.05, k=0.2))     # бегунок
    m.box((4.5, 6.0, 7.0), (5.0, 7.5, 9.0), paint('#C8302A', noise=0.05, k=0.2))      # нашивка
    return m


def glow_ring():
    """Кольцо для ХИС: восьмиугольная резиновая петля, снизу три цветные палочки, сверху карабин."""
    m = Model('glow_ring')
    rub = paint('#2C2C30', noise=0.06, k=0.3)
    for a, b in (((6.5, 11.5), (9.5, 13.0)), ((6.5, 3.0), (9.5, 4.5)), ((3.0, 6.5), (4.5, 9.5)),
                 ((11.5, 6.5), (13.0, 9.5)), ((4.5, 9.5), (6.5, 11.5)), ((9.5, 9.5), (11.5, 11.5)),
                 ((4.5, 4.5), (6.5, 6.5)), ((9.5, 4.5), (11.5, 6.5))):
        m.box((a[0], a[1], 6.0), (b[0], b[1], 8.0), rub)
    m.box((7.5, 13.0, 6.5), (8.5, 14.5, 7.5), chrome())                                  # карабин
    for x, col in ((6.0, '#3BD16F'), (7.5, '#E8D43A'), (9.0, '#E8472A')):
        m.box((x, 5.0, 6.5), (x + 1.0, 11.0, 7.5), flat(col), emit=True)                 # палочки в кольце
    return m


def sponge():
    """Губка: жёлтый поролон с порами и зелёный жёсткий слой."""
    m = Model('ink_sponge')
    foam_base = paint('#E9C94A', noise=0.1, edge=-0.08, k=0.0)

    def foam(tx):
        c = foam_base(tx)
        if tx.noise(11) < 0.18:
            c = mul(c, 0.72)
        return c
    m.box((4.0, 0.0, 5.0), (12.0, 1.0, 11.0), paint('#3C7A3A', noise=0.12, edge=-0.06, k=0.0))
    m.box((4.0, 1.0, 5.0), (12.0, 4.0, 11.0), foam)
    m.box((4.5, 4.0, 5.5), (11.5, 4.5, 10.5), foam)
    return m


def charger(cells):
    """Зарядка (блок): корпус, 4 гнезда, аккумуляторы по числу, огонёк."""
    m = Model('charger')
    case = paint('#4A4E52', noise=0.04, edge=0.12, k=0.0)
    m.box((1.0, 0.0, 3.0), (15.0, 3.0, 13.0), case)
    m.box((1.5, 3.0, 3.5), (14.5, 3.5, 12.5), paint('#3A3D41', noise=0.04, edge=0.1))
    m.box((10.0, 0.5, 2.5), (14.0, 2.0, 3.0), paint('#2B2D30', noise=0.04))      # табличка
    # шнур сзади
    m.box((7.5, 0.5, 13.0), (8.5, 1.5, 16.0), paint('#1E1E1E', noise=0.06))
    wrap = label('#1F4E8C', '#E8E4D6', 6.5, 8.5, text_col='#1F4E8C')
    for i in range(cells):
        x = 3.5 + i * 3.0
        m.cyl('y', x, 9.0, 1.5, 3.0, 9.5, wrap, cap=paint('#9AA2AA', noise=0.05, k=0.0))
        m.cyl('y', x, 9.0, 0.5, 9.5, 10.0, STEEL, cap=paint('#C5CCD2', noise=0.04, k=0.0))
    for i in range(cells, 4):
        x = 3.5 + i * 3.0
        m.box((x - 1.0, 3.5, 8.0), (x + 1.0, 4.0, 10.0), paint('#25272A', noise=0.05))   # пустое гнездо
    return m


def charger_led(col):
    m = Model('charger_led_' + col)
    m.box((3.0, 1.0, 2.5), (4.0, 2.0, 3.0), flat('#E0443A' if col == 'red' else '#5BD46A'), emit=True)
    return m


# ------------------------------------------------------------------------------------------------ сборка

def light(name, fn, grip, **kw):
    off = fn(False)
    on = fn(True)
    disp = held(off, grip, **kw)
    m_off = build(off, name, display=disp)
    m_off['overrides'] = [{'predicate': {'rplights:on': 1}, 'model': f'rplights:item/{name}_on'}]
    jdump(os.path.join(MOD_ITEM, name + '.json'), m_off)
    jdump(os.path.join(MOD_ITEM, name + '_on.json'), build(on, name + '_on', display=disp))


def main():
    light('hand_lantern', hand_lantern, grip=(8.0, 12.25, 10.75), fp_at=(0.0, -1.0, 0.0), tp_at=(0.0, 0.5, 0.0),
          gui_rotation=(25, -135, 0), gui_fill=15.0)
    light('old_flashlight', old_flashlight, grip=(8.0, 8.0, 11.5))
    light('dynamo_flashlight', dynamo, grip=(8.0, 7.0, 10.5), fp_at=(0.0, 1.5, 0.0), gui_rotation=(25, -135, 0))
    light('uv_flashlight', uv_flashlight, grip=(8.0, 8.0, 11.0))
    light('rgb_flashlight', rgb_flashlight, grip=(8.0, 8.0, 11.0))
    light('headlamp', headlamp, grip=(3.25, 8.0, 9.25), fp_at=(1.0, 1.0, -1.0), fp_rot=(0, 30, 0))

    # налобный на игроке: без display, рисуется из своего слоя Curios
    for fn, nm in ((headlamp_worn_head, 'headlamp_worn_head'), (headlamp_worn_chest, 'headlamp_worn_chest')):
        jdump(os.path.join(MOD_ITEM, nm + '.json'), build(fn(False), nm))
        jdump(os.path.join(MOD_ITEM, nm + '_on.json'), build(fn(True), nm + '_on'))

    # лампа: предмет и блок (стоит / висит)
    lamp_off, lamp_on = lamp_model(False), lamp_model(True)
    disp = held(lamp_off, grip=(8.0, 11.25, 8.0), fp_at=(0.0, 5.0, 0.0), tp_at=(0.0, 0.0, 1.0),
                fp_scale=0.85, tp_scale=0.8, fp_rot=(0, 0, 0), gui_rotation=(20, 225, 0), gui_fill=15.5)
    disp['ground'] = place((0, 0, 0), 0.6, (8.0, 5.5, 8.0), (0, 3.5, 0))
    rt = 'minecraft:translucent'
    for on, mdl in ((False, lamp_off), (True, lamp_on)):
        sfx = '_on' if on else ''
        block = build(mdl, 'hurricane_lamp' + sfx, render_type=rt)
        jdump(os.path.join(MOD_BLOCK, 'hurricane_lamp' + sfx + '.json'), block)
        hang = build(mdl.shifted(4.5), 'hurricane_lamp' + sfx, render_type=rt)
        jdump(os.path.join(MOD_BLOCK, 'hurricane_lamp_hanging' + sfx + '.json'), hang)
        item = {'parent': f'rplights:block/hurricane_lamp{sfx}', 'gui_light': 'side', 'display': disp}
        if not on:
            item['overrides'] = [{'predicate': {'rplights:on': 1}, 'model': 'rplights:item/hurricane_lamp_on'}]
        jdump(os.path.join(MOD_ITEM, 'hurricane_lamp' + sfx + '.json'), item)
    variants = {}
    for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for hanging in ('false', 'true'):
            for lit in ('false', 'true'):
                mdl = 'rplights:block/hurricane_lamp' + ('_hanging' if hanging == 'true' else '') + ('_on' if lit == 'true' else '')
                v = {'model': mdl}
                if rot:
                    v['y'] = rot
                variants[f'facing={facing},hanging={hanging},lit={lit}'] = v
    jdump(os.path.join(STATES, 'hurricane_lamp.json'), {'variants': variants})

    # мелочи
    acc = accumulator()
    jdump(os.path.join(MOD_ITEM, 'accumulator.json'), build(acc, 'accumulator', display=thing(acc, gui_fill=12.0)))
    for burnt in (False, True):
        b, rtb = bulb(burnt)
        nm = 'burnt_bulb' if burnt else 'bulb'
        jdump(os.path.join(MOD_ITEM, nm + '.json'), build(b, nm, render_type=rtb, display=thing(b, gui_fill=12.0)))
    t = tester()
    jdump(os.path.join(MOD_ITEM, 'battery_tester.json'),
          build(t, 'battery_tester', display=thing(t, gui_rotation=(30, 200, 0), gui_fill=15.0)))
    mk = uv_marker()
    jdump(os.path.join(MOD_ITEM, 'uv_marker.json'), build(mk, 'uv_marker', display=held(mk, grip=(8.0, 8.0, 10.0))))
    for colour in MARKER_COLOURS:
        mk = marker(colour)
        nm = 'marker_' + colour
        jdump(os.path.join(MOD_ITEM, nm + '.json'), build(mk, nm, display=held(mk, grip=(8.0, 8.0, 10.0))))
    mc = marker_case()
    jdump(os.path.join(MOD_ITEM, 'marker_case.json'), build(mc, 'marker_case', display=thing(mc, gui_fill=14.0)))
    gr = glow_ring()
    jdump(os.path.join(MOD_ITEM, 'glow_ring.json'), build(gr, 'glow_ring', display=thing(gr, gui_rotation=(15, 200, 0), gui_fill=14.0)))
    sp = sponge()
    jdump(os.path.join(MOD_ITEM, 'ink_sponge.json'), build(sp, 'ink_sponge', display=thing(sp, gui_fill=13.0)))

    # зарядка
    for n in range(5):
        c = charger(n)
        jdump(os.path.join(MOD_BLOCK, f'charger_{n}.json'),
              build(c, f'charger_{n}', parent='block/block'))
    for col in ('red', 'green'):
        jdump(os.path.join(MOD_BLOCK, f'charger_led_{col}.json'), build(charger_led(col), f'charger_led_{col}'))
    parts = []
    for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        def p(model, when):
            a = {'model': model}
            if rot:
                a['y'] = rot
            parts.append({'when': dict({'facing': facing}, **when), 'apply': a})
        for n in range(5):
            p(f'rplights:block/charger_{n}', {'cells': str(n)})
        p('rplights:block/charger_led_red', {'charging': 'true'})
        p('rplights:block/charger_led_green', {'charging': 'false', 'cells': '1|2|3|4'})
    jdump(os.path.join(STATES, 'charger.json'), {'multipart': parts})
    jdump(os.path.join(MOD_ITEM, 'charger.json'), {'parent': 'rplights:block/charger_2'})


if __name__ == '__main__':
    main()
