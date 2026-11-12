"""Переводы RP Lights (ru_ru, en_us). Запуск: python tools/gen_lang.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rplights', 'lang')

# ключ: (по-русски, по-английски)
T = {
    "itemGroup.rplights": ("RP Фонари", "RP Lights"),
    "key.categories.rplights": ("RP Фонари", "RP Lights"),
    "key.rplights.toggle_worn": ("Надетый фонарь: вкл/выкл", "Worn light: on/off"),
    "item.rplights.hand_lantern": ("Ручной фонарь", "Hand Lantern"),
    "item.rplights.hand_lantern.desc": (
        "Прожектор с ручкой. Лампа накаливания: тёплый свет, к концу батареек тускнеет и рыжеет.",
        "Spotlight with a handle. Incandescent bulb: warm light that dims and turns orange as the batteries run down."),
    "item.rplights.old_flashlight": ("Старый фонарик", "Old Flashlight"),
    "item.rplights.old_flashlight.desc": (
        "Хромированный, переделан под светодиод. Капризный: мигает, не всегда включается, боится воды.",
        "Chrome, converted to an LED. Moody: flickers, does not always turn on, hates water."),
    "item.rplights.headlamp": ("Налобный фонарь", "Headlamp"),
    "item.rplights.headlamp.desc": (
        "Слот Curios «голова» — светит куда смотришь, «тело» — куда повёрнут корпус. Руки свободны.",
        "Curios head slot shines where you look, body slot where your body faces. Hands stay free."),
    "item.rplights.uv_flashlight": ("УФ-фонарик", "UV Flashlight"),
    "item.rplights.uv_flashlight.desc": ("Ультрафиолет. Обычные предметы почти не освещает.",
                                         "Ultraviolet. Barely lights ordinary things."),
    "item.rplights.rgb_flashlight": ("RGB-фонарик", "RGB Flashlight"),
    "item.rplights.rgb_flashlight.desc": ("Зачем? Просто красиво.", "Why? Because it is pretty."),
    "item.rplights.dynamo_flashlight": ("Динамо-фонарик", "Dynamo Flashlight"),
    "item.rplights.dynamo_flashlight.desc": ("«Жучок»: светит, пока жмёшь рычаг. Батарейки не нужны.",
                                             "Squeeze light: shines while you pump the lever. No batteries."),
    "item.rplights.hurricane_lamp": ("Лампа «Летучая мышь»", "Hurricane Lamp"),
    "item.rplights.hurricane_lamp.desc": (
        "Керосиновая, светит вокруг. Носится в руке или на поясе (слот Curios «пояс»), ставится на пол и под потолок. В воде гаснет.",
        "Kerosene, shines all round. Carry it in hand or on the belt (Curios belt slot), or set it on a floor or under a ceiling. Goes out in water."),
    "block.rplights.hurricane_lamp": ("Лампа «Летучая мышь»", "Hurricane Lamp"),
    "item.rplights.uv_marker": ("Маркер с невидимыми чернилами", "Invisible Ink Marker"),
    "item.rplights.uv_marker.desc": ("Пишет невидимо — видно только в ультрафиолете.",
                                     "Writes invisibly - shows only under ultraviolet light."),
    "item.rplights.accumulator": ("Аккумулятор", "Rechargeable Cell"),
    "item.rplights.accumulator.desc": (
        "Заряжается в зарядном устройстве. Держит меньше обычной батарейки и со временем изнашивается.",
        "Recharges in a charger. Holds less than a regular battery and wears out over time."),
    "item.rplights.bulb": ("Лампочка накаливания", "Incandescent Bulb"),
    "item.rplights.burnt_bulb": ("Перегоревшая лампочка", "Burnt Bulb"),
    "item.rplights.battery_tester": ("Тестер батареек", "Battery Tester"),
    "item.rplights.battery_tester.desc": ("ПКМ: проверить батарейку, аккумулятор или фонарь во второй руке.",
                                          "Right-click: test the battery, cell or flashlight in your other hand."),
    "block.rplights.charger": ("Зарядное устройство", "Charger"),
    "rplights.tip.cells": ("Батареек: %s", "Batteries: %s"),
    "rplights.tip.use.hand_lantern": (
        "ПКМ — вкл/выкл. Shift+ПКМ: с батарейками во второй руке — поменять, с лампочкой — вкрутить, с пустой — вынуть батарейки.",
        "Right-click: on/off. Sneak+right-click: batteries in the other hand - swap, a bulb - screw in, empty hand - take the batteries out."),
    "rplights.tip.use.old_flashlight": (
        "ПКМ — вкл/выкл (бывает, не с первого раза). Shift+ПКМ: с батарейками во второй руке — поменять, с пустой — вынуть.",
        "Right-click: on/off (not always the first time). Sneak+right-click: batteries in the other hand - swap, empty hand - take out."),
    "rplights.tip.use.headlamp": (
        "Надетый — клавиша L. В руке: ПКМ — вкл/выкл, Shift+ПКМ с батарейками — поменять, с пустой рукой — вынуть.",
        "Worn: key L. In hand: right-click on/off, sneak+right-click with batteries - swap, empty hand - take out."),
    "rplights.tip.use.uv_flashlight": (
        "ПКМ — вкл/выкл. Shift+ПКМ: с батарейками во второй руке — поменять, с пустой — вынуть.",
        "Right-click: on/off. Sneak+right-click: batteries in the other hand - swap, empty hand - take out."),
    "rplights.tip.use.rgb_flashlight": (
        "ПКМ — вкл/выкл. Shift+ПКМ: с батарейками — поменять, с пустой рукой — следующий режим.",
        "Right-click: on/off. Sneak+right-click: batteries - swap, empty hand - next mode."),
    "rplights.tip.use.dynamo_flashlight": (
        "ПКМ — жим рычага, каждый добавляет несколько секунд света. Тратит немного сил.",
        "Right-click: pump the lever, each adds a few seconds of light. Costs a little stamina."),
    "rplights.tip.use.hurricane_lamp": (
        "ПКМ — зажечь/погасить, ПКМ по блоку — поставить. Shift+ПКМ: с ведром керосина во второй руке — заправить, с пустой — фитиль. Поставленная: ПКМ — зажечь/погасить, Shift+ПКМ пустой рукой — взять.",
        "Right-click: light/put out, right-click a block: set it down. Sneak+right-click: kerosene bucket in the other hand - refuel, empty hand - wick. Set down: right-click to light/put out, sneak+right-click with an empty hand to pick up."),
    "rplights.tip.use.uv_marker": (
        "Держи ПКМ и веди по блоку — рисует. Shift — стирает. Видно в луче УФ-фонаря.",
        "Hold right-click and move over a block to draw. Sneak to erase. Visible in a UV flashlight beam."),
    "rplights.tip.use.sa": (
        "Shift+ПКМ: с батарейкой во второй руке — поменять, с пустой — ближний/дальний свет.",
        "Sneak+right-click: battery in the other hand - swap, empty hand - low/high beam."),
    "rplights.tip.swollen": ("Вздулся", "Swollen"),
    "rplights.msg.mode_low": ("Ближний свет", "Low beam"),
    "rplights.msg.mode_high": ("Дальний свет", "High beam"),
    "rplights.msg.wick_low": ("Фитиль прикручен", "Wick turned down"),
    "rplights.msg.wick_full": ("Фитиль выкручен", "Wick turned up"),
    "rplights.msg.need_cells": ("Нужно батареек: %s", "Batteries needed: %s"),
    "rplights.msg.no_cells": ("Батареек нет", "No batteries"),
    "rplights.msg.swollen": ("Аккумулятор вздулся — заряжать опасно", "The cell is swollen - unsafe to charge"),
    "rplights.msg.charger_full": ("Все гнёзда заняты", "All slots are taken"),
    "rplights.msg.not_rechargeable": ("Обычные батарейки не заряжаются", "Regular batteries do not recharge"),
    "rplights.msg.nothing_worn": ("На голове, груди и поясе фонаря нет", "No lamp on head, chest or belt"),
    "rplights.tester.battery": ("Батарейка: ≈%s%%", "Battery: ~%s%%"),
    "rplights.tester.accumulator": ("Аккумулятор: ≈%s%%, ёмкость ≈%s%%", "Cell: ~%s%%, capacity ~%s%%"),
    "rplights.tester.light": ("Фонарь:", "Flashlight:"),
    "rplights.tester.empty": ("батареек нет", "no batteries"),
    "rplights.tester.bulb_ok": ("лампочка цела", "bulb intact"),
    "rplights.tester.bulb_open": ("лампочка: обрыв", "bulb: open circuit"),
    "rplights.tester.wet": ("контакты мокрые", "contacts wet"),
    "rplights.tester.nothing": ("Во второй руке нечего проверять", "Nothing to test in the other hand"),
}

os.makedirs(LANG, exist_ok=True)
for i, name in enumerate(('ru_ru', 'en_us')):
    with open(os.path.join(LANG, name + '.json'), 'w', encoding='utf-8') as f:
        json.dump({k: v[i] for k, v in T.items()}, f, ensure_ascii=False, indent=2)
print(len(T))
