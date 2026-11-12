"""Лут RP Lights в ящиках Survivor's Arsenal сборки S5 (layers/*/files/config/survivorsarsenal/loot_crates).

Новые фонари кладутся в те же пулы, где лежит фонарь SA, расходники — где батарейка SA. Шанс — доля от шанса
записи SA. Повторный запуск заменяет свои записи (rplights:*), чужие не трогает.
Запуск: python tools/pack_loot.py [путь к S5 Packs]
"""
import copy
import json
import sys
from pathlib import Path

PACK = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'S5 Packs'

# предмет: доля шанса фонаря SA
LIGHTS = {
    'rplights:old_flashlight': 1.5,
    'rplights:dynamo_flashlight': 1.2,
    'rplights:hand_lantern': 1.0,
    'rplights:headlamp': 0.8,
    'rplights:hurricane_lamp': 0.7,
    'rplights:uv_flashlight': 0.35,
    'rplights:rgb_flashlight': 0.12,
    'rplights:uv_marker': 0.3,
}
# предмет: (доля шанса батарейки SA, сколько штук)
SUPPLIES = {
    'rplights:bulb': (0.6, (1, 2)),
    'rplights:accumulator': (0.3, (1, 2)),
    'rplights:battery_tester': (0.12, (1, 1)),
    'rplights:charger': (0.06, (1, 1)),
}


def patch_pool(pool):
    entries = pool.get('entries')
    if not isinstance(entries, list):
        return 0
    entries[:] = [e for e in entries if not str(e.get('item', '')).startswith('rplights:')]
    added = []
    for e in entries:
        item = e.get('item')
        if item == 'sa_combat:flashlight':
            for rid, k in LIGHTS.items():
                n = copy.deepcopy(e)
                n['item'] = rid
                n.pop('nbt', None)
                n['chance'] = round(float(e.get('chance', 1.0)) * k, 4)
                n['count'] = {'min': 1, 'max': 1}
                added.append(n)
        elif item == 'sa_core:battery':
            for rid, (k, (lo, hi)) in SUPPLIES.items():
                n = copy.deepcopy(e)
                n['item'] = rid
                n.pop('nbt', None)
                n['chance'] = round(float(e.get('chance', 1.0)) * k, 4)
                n['count'] = {'min': lo, 'max': hi}
                added.append(n)
    entries.extend(added)
    return len(added)


def main():
    files = sorted(PACK.glob('layers/*/files/config/survivorsarsenal/loot_crates/*.json'))
    total = 0
    for f in files:
        data = json.loads(f.read_text(encoding='utf-8'))
        n = sum(patch_pool(p) for p in data.get('pools', []))
        if n:
            f.write_bytes((json.dumps(data, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))
            print(f'{f.relative_to(PACK)}: +{n}')
            total += n
    print('всего', total)


if __name__ == '__main__':
    main()
