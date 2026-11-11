"""Иконки (16x16), модели предметов и блока зарядки RP Lights.

Рисуется простым растеризатором: капсулы, эллипсы, прямоугольники с тенью поперёк формы и тёмным контуром.
Запуск из корня репозитория: python tools/gen_assets.py
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rplights')
TEX_ITEM = os.path.join(ROOT, 'textures', 'item')
TEX_BLOCK = os.path.join(ROOT, 'textures', 'block')
MOD_ITEM = os.path.join(ROOT, 'models', 'item')
MOD_BLOCK = os.path.join(ROOT, 'models', 'block')
STATES = os.path.join(ROOT, 'blockstates')
for d in (TEX_ITEM, TEX_BLOCK, MOD_ITEM, MOD_BLOCK, STATES):
    os.makedirs(d, exist_ok=True)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


class Canvas:
    def __init__(self, size=16):
        self.n = size
        self.px = [[None] * size for _ in range(size)]

    def set(self, x, y, c):
        if 0 <= x < self.n and 0 <= y < self.n and c is not None:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.n and 0 <= y < self.n else None

    def shape(self, inside, shade, outline=None):
        """inside(cx, cy) -> (bool, across in -1..1). Тень: светлая сторона — across < 0."""
        filled = [[False] * self.n for _ in range(self.n)]
        for y in range(self.n):
            for x in range(self.n):
                ok, s = inside(x + 0.5, y + 0.5)
                if ok:
                    filled[y][x] = True
                    self.set(x, y, shade(s))
        if outline:
            for y in range(self.n):
                for x in range(self.n):
                    if filled[y][x]:
                        continue
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        xx, yy = x + dx, y + dy
                        if 0 <= xx < self.n and 0 <= yy < self.n and filled[yy][xx]:
                            if self.get(x, y) is None:
                                self.set(x, y, outline)
                            break
        return filled

    def capsule(self, ax, ay, bx, by, r, dark, light, outline=None, hi=-0.45):
        dx, dy = bx - ax, by - ay
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / ln, dx / ln

        def inside(px, py):
            t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (ln * ln)))
            qx, qy = ax + dx * t, ay + dy * t
            d = math.hypot(px - qx, py - qy)
            s = ((px - ax) * nx + (py - ay) * ny) / r
            return d <= r, s

        return self.shape(inside, lambda s: tube(dark, light, s, hi), outline)

    def ellipse(self, cx, cy, rx, ry, dark, light, outline=None, hi=-0.45, axis='x'):
        def inside(px, py):
            u, v = (px - cx) / rx, (py - cy) / ry
            return u * u + v * v <= 1.0, (u if axis == 'x' else v)

        return self.shape(inside, lambda s: tube(dark, light, s, hi), outline)

    def rect(self, x0, y0, x1, y1, dark, light, outline=None, hi=-0.45, axis='x'):
        def inside(px, py):
            ok = x0 <= px <= x1 and y0 <= py <= y1
            s = ((px - x0) / max(x1 - x0, 1e-3) * 2 - 1) if axis == 'x' else ((py - y0) / max(y1 - y0, 1e-3) * 2 - 1)
            return ok, s

        return self.shape(inside, lambda s: tube(dark, light, s, hi), outline)

    def save(self, path):
        img = Image.new('RGBA', (self.n, self.n), (0, 0, 0, 0))
        for y in range(self.n):
            for x in range(self.n):
                c = self.px[y][x]
                if c:
                    img.putpixel((x, y), c)
        img.save(path)


def tube(dark, light, s, hi):
    """Цилиндр: светлее у hi, к краям темнее."""
    t = 1.0 - min(1.0, abs(s - hi) / 1.3)
    return lerp(dark, light, t)


OUT = hexc('#141414')

# ---------------------------------------------------------------------------------------------------- фонари

def hand_lantern(on):
    c = Canvas()
    # ручка-дуга сверху
    c.capsule(7.0, 3.6, 13.6, 3.6, 0.9, hexc('#9E7410'), hexc('#F2C744'), OUT)
    c.capsule(7.2, 3.6, 7.2, 6.0, 0.9, hexc('#9E7410'), hexc('#F2C744'), OUT)
    c.capsule(13.4, 3.6, 13.4, 6.0, 0.9, hexc('#9E7410'), hexc('#F2C744'), OUT)
    # корпус
    c.rect(5.0, 6.0, 15.0, 12.5, hexc('#A9780E'), hexc('#F5CB3D'), OUT, hi=-0.5, axis='y')
    c.set(8, 5, hexc('#1E1E1E'))
    # чёрный обод с линзой
    c.ellipse(3.6, 9.2, 3.3, 4.3, hexc('#151515'), hexc('#4C4C4C'), OUT, axis='y')
    lens_d, lens_l = (hexc('#E9C46A'), hexc('#FFF6D6')) if on else (hexc('#6F7C86'), hexc('#C7D2DA'))
    c.ellipse(3.9, 9.2, 2.0, 3.0, lens_d, lens_l, None, hi=-0.6, axis='y')
    return c


def tube_light(body_d, body_l, head_d, head_l, lens_off, lens_on, on, band=None, button=None, short=False):
    c = Canvas()
    tail = (3.2, 12.8) if not short else (4.2, 11.8)
    c.capsule(tail[0], tail[1], 10.0, 6.0, 1.55, body_d, body_l, OUT)
    if band:
        for i, col in enumerate(band):
            t = 0.35 + i * 0.12
            x = tail[0] + (10.0 - tail[0]) * t
            y = tail[1] + (6.0 - tail[1]) * t
            c.capsule(x - 0.3, y + 0.3, x + 0.3, y - 0.3, 1.6, col, lerp(col, hexc('#FFFFFF'), 0.4), None)
    c.capsule(10.2, 5.8, 12.4, 3.6, 2.15, head_d, head_l, OUT)
    lens = lens_on if on else lens_off
    c.ellipse(12.9, 3.1, 1.6, 1.6, lens[0], lens[1], None, hi=-0.5)
    if button:
        c.set(7, 8, button)
        c.set(8, 8, lerp(button, hexc('#000000'), 0.3))
    return c


def old_flashlight(on):
    chrome_d, chrome_l = hexc('#5E6469'), hexc('#EEF2F5')
    return tube_light(chrome_d, chrome_l, hexc('#6B7176'), hexc('#F6F8FA'),
                      (hexc('#5E7F78'), hexc('#B9D3CD')), (hexc('#CFE2FF'), hexc('#FFFFFF')), on, button=hexc('#C3312B'))


def uv_flashlight(on):
    return tube_light(hexc('#1D1826'), hexc('#4F4466'), hexc('#241D30'), hexc('#5A4E74'),
                      (hexc('#3A2E55'), hexc('#6D5C99')), (hexc('#8E5CFF'), hexc('#E3D2FF')), on, short=True)


def rgb_flashlight(on):
    c = tube_light(hexc('#151515'), hexc('#454545'), hexc('#1B1B1B'), hexc('#4E4E4E'),
                   (hexc('#555555'), hexc('#9A9A9A')), (hexc('#FF7AD9'), hexc('#9AF7FF')), on,
                   band=[hexc('#D9534F'), hexc('#5CB85C'), hexc('#4A90D9')], short=True)
    return c


def headlamp(on):
    c = Canvas()
    # резинка: кольцо
    def strap(px, py):
        u, v = (px - 8.0) / 6.6, (py - 8.6) / 4.2
        r = math.sqrt(u * u + v * v)
        return 0.78 <= r <= 1.0, v
    c.shape(strap, lambda s: lerp(hexc('#2B3038'), hexc('#5C6673'), 0.5 - s * 0.4), OUT)
    c.rect(4.6, 8.4, 11.4, 13.4, hexc('#8E9297'), hexc('#E2E4E6'), OUT, hi=-0.4)
    lens = (hexc('#FFE9A8'), hexc('#FFFDF4')) if on else (hexc('#5F6E7B'), hexc('#AEBBC6'))
    c.ellipse(8.0, 10.9, 2.0, 1.9, lens[0], lens[1], None, hi=-0.5)
    c.set(10, 9, hexc('#C3312B'))
    return c


def dynamo(on):
    c = Canvas()
    c.capsule(4.0, 11.6, 11.6, 13.4, 0.95, hexc('#2A2A2A'), hexc('#6A6A6A'), OUT)
    c.capsule(11.0, 9.5, 11.6, 13.4, 0.8, hexc('#2A2A2A'), hexc('#6A6A6A'), OUT)
    c.ellipse(8.6, 8.0, 5.6, 3.6, hexc('#3E5530'), hexc('#8FB067'), OUT, axis='y')
    lens = (hexc('#F2C46B'), hexc('#FFF0C8')) if on else (hexc('#6F7C86'), hexc('#C7D2DA'))
    c.ellipse(3.2, 8.0, 1.2, 2.3, lens[0], lens[1], OUT, axis='y')
    c.set(9, 6, hexc('#C9D7BC'))
    c.set(10, 6, hexc('#C9D7BC'))
    return c


def hurricane_lamp(on):
    c = Canvas()
    # ручка-дуга
    def handle(px, py):
        d = math.hypot(px - 8.0, py - 4.6)
        return 3.6 <= d <= 4.5 and py <= 4.6, 0.0
    c.shape(handle, lambda s: hexc('#6A6E73'), OUT)
    glass_d, glass_l = (hexc('#E0A050', 200), hexc('#FFE9B0', 220)) if on else (hexc('#7F98A6', 150), hexc('#DCE8EE', 190))
    c.ellipse(8.0, 8.0, 3.3, 3.4, glass_d, glass_l, OUT, hi=-0.5)
    if on:
        c.ellipse(8.0, 8.4, 0.9, 1.7, hexc('#F08A24'), hexc('#FFF2A6'), None, hi=0.3, axis='y')
    else:
        c.set(8, 9, hexc('#3B2A1A'))
    # проволочная рамка
    for y in range(4, 13):
        c.set(3, y, hexc('#3C3F43'))
        c.set(12, y, hexc('#3C3F43'))
    c.rect(4.6, 3.4, 11.4, 4.6, hexc('#7C1F1A'), hexc('#D0473C'), OUT, axis='y')
    c.rect(3.6, 11.4, 12.4, 14.6, hexc('#7C1F1A'), hexc('#D84F42'), OUT, hi=-0.5)
    return c


# ---------------------------------------------------------------------------------------------------- прочее

def accumulator():
    c = Canvas()
    c.rect(5.0, 3.4, 11.0, 14.6, hexc('#1F5C3A'), hexc('#5DBB82'), OUT)
    c.rect(6.6, 2.0, 9.4, 3.4, hexc('#8A8D90'), hexc('#E0E2E4'), OUT)
    for x in range(5, 11):
        c.set(x, 6, lerp(hexc('#BFC4C8'), hexc('#F4F6F7'), 1 - abs(x - 6.5) / 4))
    c.set(7, 10, hexc('#E8F5EC'))
    c.set(8, 10, hexc('#E8F5EC'))
    c.set(7, 9, hexc('#E8F5EC'))
    c.set(7, 11, hexc('#E8F5EC'))
    c.set(6, 10, hexc('#E8F5EC'))
    return c


def bulb(burnt):
    c = Canvas()
    gd, gl = (hexc('#6E7376', 210), hexc('#B4BABD', 220)) if burnt else (hexc('#B9D2DE', 170), hexc('#F6FBFD', 210))
    c.ellipse(8.0, 6.2, 4.4, 4.6, gd, gl, OUT, hi=-0.5)
    c.rect(6.0, 10.4, 10.0, 11.4, hexc('#7E8184'), hexc('#C9CBCD'), OUT)
    c.rect(6.0, 11.6, 10.0, 14.4, hexc('#7E8184'), hexc('#D8DADC'), OUT)
    for x in range(6, 10):
        c.set(x, 13, hexc('#5E6164'))
    c.set(8, 15, hexc('#3A3A3A'))
    fil = hexc('#5A4426')
    if burnt:
        for (x, y) in ((6, 7), (7, 6), (9, 6), (10, 7)):
            c.set(x, y, fil)
        c.set(7, 3, hexc('#3E4144'))
        c.set(8, 3, hexc('#3E4144'))
        c.set(8, 4, hexc('#4C4F52'))
    else:
        for (x, y) in ((6, 7), (7, 6), (8, 7), (9, 6), (10, 7)):
            c.set(x, y, fil)
    for y in (8, 9, 10):
        c.set(7, y, hexc('#6B6E70'))
        c.set(9, y, hexc('#6B6E70'))
    return c


def tester():
    c = Canvas()
    c.capsule(6.0, 12.6, 3.4, 15.2, 0.6, hexc('#8E1F1A'), hexc('#E04A3F'), None)
    c.capsule(10.0, 12.6, 12.6, 15.2, 0.6, hexc('#111111'), hexc('#444444'), None)
    c.rect(2.6, 1.6, 13.4, 12.8, hexc('#22262B'), hexc('#545B63'), OUT, hi=-0.4)
    c.rect(4.0, 3.0, 12.0, 7.8, hexc('#CFC9A8'), hexc('#F3EED6'), None, hi=-0.3, axis='y')
    for (x, y) in ((8, 7), (9, 6), (9, 5), (10, 4)):
        c.set(x, y, hexc('#C3312B'))
    c.set(5, 4, hexc('#55524A'))
    c.set(11, 4, hexc('#55524A'))
    c.ellipse(8.0, 10.3, 1.4, 1.4, hexc('#0E0F10'), hexc('#3A3F44'), None)
    return c


# ---------------------------------------------------------------------------------------------------- блок зарядки

def charger_textures():
    top = Canvas()
    top.rect(0, 0, 16, 16, hexc('#2E3237'), hexc('#4A5058'), None, hi=-0.6, axis='y')
    for i in range(4):
        x0 = 2 + i * 3
        for x in range(x0, x0 + 2):
            for y in range(6, 10):
                top.set(x, y, hexc('#121416'))
    top.save(os.path.join(TEX_BLOCK, 'charger_top.png'))
    side = Canvas()
    side.rect(0, 0, 16, 16, hexc('#2A2E33'), hexc('#454B52'), None, hi=-0.6, axis='y')
    for x in range(16):
        side.set(x, 0, hexc('#5A626B'))
    side.save(os.path.join(TEX_BLOCK, 'charger_side.png'))
    cell = Canvas()
    cell.rect(0, 0, 16, 16, hexc('#1F5C3A'), hexc('#5DBB82'), None)
    for x in range(16):
        for y in range(0, 3):
            cell.set(x, y, hexc('#C9CBCD'))
        cell.set(x, 6, hexc('#E8ECEF'))
    cell.save(os.path.join(TEX_BLOCK, 'charger_cell.png'))
    for name, col in (('charger_led_red', '#E0443A'), ('charger_led_green', '#5BD46A')):
        led = Canvas()
        led.rect(0, 0, 16, 16, hexc(col), lerp(hexc(col), hexc('#FFFFFF'), 0.5), None)
        led.save(os.path.join(TEX_BLOCK, name + '.png'))


def jdump(path, data):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)


def face_all(tex, uv=None):
    f = {'texture': tex}
    if uv:
        f['uv'] = uv
    return {d: dict(f) for d in ('north', 'south', 'east', 'west', 'up', 'down')}


def charger_models():
    jdump(os.path.join(MOD_BLOCK, 'charger_base.json'), {
        'parent': 'block/block',
        'textures': {'particle': 'rplights:block/charger_side', 'top': 'rplights:block/charger_top', 'side': 'rplights:block/charger_side'},
        'elements': [{
            'from': [2, 0, 4], 'to': [14, 3, 12],
            'faces': {
                'north': {'texture': '#side', 'uv': [2, 13, 14, 16]},
                'south': {'texture': '#side', 'uv': [2, 13, 14, 16]},
                'east': {'texture': '#side', 'uv': [4, 13, 12, 16]},
                'west': {'texture': '#side', 'uv': [4, 13, 12, 16]},
                'up': {'texture': '#top', 'uv': [2, 4, 14, 12]},
                'down': {'texture': '#side', 'uv': [2, 4, 14, 12], 'cullface': 'down'},
            }}],
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 2, 0], 'scale': [0.8, 0.8, 0.8]},
            'ground': {'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
            'fixed': {'scale': [0.7, 0.7, 0.7]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.4, 0.4, 0.4]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 3, 0], 'scale': [0.4, 0.4, 0.4]},
        }})
    for i in range(4):
        x0 = 2 + i * 3
        jdump(os.path.join(MOD_BLOCK, f'charger_cell{i + 1}.json'), {
            'textures': {'cell': 'rplights:block/charger_cell', 'particle': 'rplights:block/charger_cell'},
            'elements': [{'from': [x0, 3, 6], 'to': [x0 + 2, 8, 10], 'faces': {
                'north': {'texture': '#cell', 'uv': [0, 0, 2, 5]}, 'south': {'texture': '#cell', 'uv': [2, 0, 4, 5]},
                'east': {'texture': '#cell', 'uv': [4, 0, 8, 5]}, 'west': {'texture': '#cell', 'uv': [8, 0, 12, 5]},
                'up': {'texture': '#cell', 'uv': [0, 0, 2, 2]}}}]})
    for col in ('red', 'green'):
        jdump(os.path.join(MOD_BLOCK, f'charger_led_{col}.json'), {
            'textures': {'led': f'rplights:block/charger_led_{col}', 'particle': f'rplights:block/charger_led_{col}'},
            'elements': [{'from': [12, 1, 3.6], 'to': [13, 2, 4], 'shade': False, 'faces': {
                'north': {'texture': '#led', 'uv': [0, 0, 1, 1]}, 'up': {'texture': '#led', 'uv': [0, 0, 1, 1]}}}]})
    parts = []
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for facing, y in rot.items():
        def p(model, when):
            m = {'model': model}
            if y:
                m['y'] = y
            parts.append({'when': dict(facing=facing, **when), 'apply': m})
        p('rplights:block/charger_base', {})
        for n in range(1, 5):
            p(f'rplights:block/charger_cell{n}', {'cells': '|'.join(str(k) for k in range(n, 5))})
        p('rplights:block/charger_led_red', {'charging': 'true'})
        p('rplights:block/charger_led_green', {'charging': 'false', 'cells': '1|2|3|4'})
    jdump(os.path.join(STATES, 'charger.json'), {'multipart': parts})
    jdump(os.path.join(MOD_ITEM, 'charger.json'), {'parent': 'rplights:block/charger_base'})


# ---------------------------------------------------------------------------------------------------- модели предметов

HANDHELD = {'old_flashlight', 'uv_flashlight', 'rgb_flashlight', 'dynamo_flashlight'}


def item_model(name, layer, parent='item/generated', overrides=None):
    m = {'parent': parent, 'textures': {'layer0': f'rplights:item/{layer}'}}
    if overrides:
        m['overrides'] = overrides
    jdump(os.path.join(MOD_ITEM, name + '.json'), m)


def light(name, fn):
    fn(False).save(os.path.join(TEX_ITEM, name + '.png'))
    fn(True).save(os.path.join(TEX_ITEM, name + '_on.png'))
    parent = 'item/handheld' if name in HANDHELD else 'item/generated'
    item_model(name + '_on', name + '_on', parent)
    item_model(name, name, parent, [{'predicate': {'rplights:on': 1}, 'model': f'rplights:item/{name}_on'}])


def main():
    light('hand_lantern', hand_lantern)
    light('old_flashlight', old_flashlight)
    light('headlamp', headlamp)
    light('dynamo_flashlight', dynamo)
    light('hurricane_lamp', hurricane_lamp)
    light('uv_flashlight', uv_flashlight)
    light('rgb_flashlight', rgb_flashlight)
    accumulator().save(os.path.join(TEX_ITEM, 'accumulator.png'))
    item_model('accumulator', 'accumulator')
    bulb(False).save(os.path.join(TEX_ITEM, 'bulb.png'))
    item_model('bulb', 'bulb')
    bulb(True).save(os.path.join(TEX_ITEM, 'burnt_bulb.png'))
    item_model('burnt_bulb', 'burnt_bulb')
    tester().save(os.path.join(TEX_ITEM, 'battery_tester.png'))
    item_model('battery_tester', 'battery_tester')
    charger_textures()
    charger_models()
    print('ok')


if __name__ == '__main__':
    main()
