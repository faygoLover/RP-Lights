"""Фонари RP Lights в списках «что светит в руках» сборки S5: RP Psyche (light_items) и Zone Artifacts (lightItems).

Светит, только пока включён (NBT LightOn). УФ-фонарик не считается: фиолетовый и тусклый.
Повторный запуск не дублирует. Запуск: python tools/pack_lights.py [путь к S5 Packs]
"""
import re
import sys
from pathlib import Path

PACK = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'S5 Packs'
ITEMS = ['rplights:hand_lantern', 'rplights:old_flashlight', 'rplights:headlamp', 'rplights:dynamo_flashlight',
         'rplights:hurricane_lamp', 'rplights:rgb_flashlight']
ADD = [f'{i}{{LightOn}}' for i in ITEMS]

FILES = [
    ('layers/server/files/serverconfig/rppsyche-server.toml', 'light_items'),
    ('layers/server/files/config/fl_zone_arts-common.toml', 'lightItems'),
    ('layers/client/files/config/fl_zone_arts-common.toml', 'lightItems'),
]


def main():
    for rel, key in FILES:
        f = PACK / rel
        if not f.exists():
            print('нет', rel)
            continue
        text = f.read_bytes().decode('utf-8')
        m = re.search(r'^([ \t]*' + key + r'[ \t]*=[ \t]*\[)(.*?)(\])[ \t]*(?=\r?$)', text, re.M)
        if not m:
            print('нет ключа', key, 'в', rel)
            continue
        items = [s.strip().strip('"') for s in m.group(2).split('",') if s.strip()]
        items = [i.strip('"').strip() for i in items]
        items = [i for i in items if not i.startswith('rplights:')] + ADD
        line = m.group(1) + ', '.join(f'"{i}"' for i in items) + m.group(3)
        text = text[:m.start()] + line + text[m.end():]
        f.write_bytes(text.encode('utf-8'))
        print(rel, len(items))


if __name__ == '__main__':
    main()
