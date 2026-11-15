# RP Lights — указатель кода

Создано `python _family/tools/mapgen.py` 15.11.2026, версия 0.2.2-1.20.1-forge, коммит `ff9cc7f`.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 36 файлов, 3822 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.rplights` | 2 | 116 |
| `faygolover.rplights.block` | 4 | 502 |
| `faygolover.rplights.client` | 7 | 891 |
| `faygolover.rplights.compat` | 1 | 40 |
| `faygolover.rplights.gametest` | 1 | 187 |
| `faygolover.rplights.ink` | 2 | 230 |
| `faygolover.rplights.item` | 7 | 530 |
| `faygolover.rplights.light` | 5 | 757 |
| `faygolover.rplights.mixin` | 1 | 23 |
| `faygolover.rplights.mixin.client` | 2 | 35 |
| `faygolover.rplights.net` | 1 | 78 |
| `faygolover.rplights.recipe` | 1 | 138 |
| `faygolover.rplights.registry` | 1 | 110 |
| `faygolover.rplights.server` | 1 | 185 |

## 2. Классы

### `faygolover.rplights`
- `LightsConfig`, 76 — Настройки сервера (serverconfig/rplights-server.toml).
- `RpLights`, 40 — RP Lights — фонари и лампы семейства RP: ручной фонарь с лампой накаливания, старый хромированный, налобный / нагрудный, динамо, керосиновая «Летучая мышь», УФ и RGB; фонарь Survivor's Arsenal — «отличный», на тех же батарейках.

### `faygolover.rplights.block`
- `ChargerBlock`, 121 — Зарядное устройство на 4 аккумулятора.
- `ChargerBlockEntity`, 110 — Аккумуляторы в зарядном устройстве.
- `LampBlock`, 193 — Поставленная «Летучая мышь»: на пол или под потолок (ставится ПКМ лампой по блоку).
- `LampBlockEntity`, 78 — Поставленная лампа: сама лампа (предмет со всем NBT), раз в секунду горит керосин, блок светит по ней.

### `faygolover.rplights.client`
- `ClientBeams`, 171 — Лучи фонарей RP Lights в этом кадре: у игроков ближе 72 блоков — в руках, на голове, на груди, на поясе.
- `Glare`, 112 — Ослепление: чужой фонарь светит тебе в глаза (ты в его луче и смотришь на лампу, стены между вами нет) — экран заливает светом его цвета; сильнее близко, у оси луча и когда смотришь прямо на лампу.
- `InkClient`, 227 — Чернила у клиента: точки чанков с сервера.
- `InkRenderTypes`, 29 — Слои чернил: обычные — как краска (свет мира, полупрозрачно), УФ — светятся (складываются со светом).
- `LightsClient`, 103 — Клиент: модель «горит», клавиша надетого фонаря, лучи для FL Render, экранный проход, подсказка фонаря SA, надетое на игроке (WornRenderer), ослепление (Glare), невидимые чернила в УФ (InkClient).
- `ScreenFlash`, 166 — Экранный проход фонарей RP Lights — как у фонаря SA (и его копии в RP Fixes): осветляет пиксели в конусе по глубине кадра, но у каждого луча свой цвет и форма, лампа светит вокруг.
- `WornRenderer`, 83 — Надетое на игроке (Curios): налобный — ремешок вокруг головы и лампа на лбу; на груди — лямки и лампа; «Летучая мышь» — висит на поясе справа спереди и чуть качается.

### `faygolover.rplights.compat`
- `CuriosCompat`, 40 — Фонари в слотах Curios: голова (налобный), тело (нагрудный), пояс (лампа).

### `faygolover.rplights.gametest`
- `LightsGameTests`, 187 — Логика без игрока: батарейки, сила питания, вид света, рецепт обслуживания, зарядка.

### `faygolover.rplights.ink`
- `InkData`, 190 — Чернила на блоках: точки 1/16 блока на гранях, по чанкам.
- `InkEvents`, 40 — Сервер: чернила чанка — игроку, который его увидел; сломанный / взорванный блок теряет свои чернила.

### `faygolover.rplights.item`
- `AccumulatorItem`, 33 — Аккумулятор: 70 % обычной батарейки, заряжается в зарядном устройстве, изнашивается.
- `DynamoItem`, 90 — Динамо-фонарик «жучок»: каждый жим (ПКМ) добавляет несколько секунд света, запас ограничен; яркость — по запасу, к концу желтеет.
- `DynamoSqueeze`, 23 — Время последнего жима динамо у своего игрока — для подёргивания руки (клиент).
- `InkToolItem`, 147 — Маркеры и губка: держишь ПКМ и ведёшь по блоку.
- `LampItem`, 86 — Керосиновая лампа «Летучая мышь»: светит вокруг, носится в руке или на поясе (слот Curios «пояс»).
- `LightItem`, 73 — Фонарь на батарейках: ПКМ — щелчок выключателем, Shift+ПКМ — батарейки / лампочка / режим (LightActions).
- `TesterItem`, 78 — Тестер батареек: ПКМ, во второй руке батарейка, аккумулятор или фонарь — стрелка показывает заряд с точностью до 10 % (у фонаря — каждую батарейку), у аккумулятора ещё и ёмкость, у ручного фонаря — цела ли лампочка.

### `faygolover.rplights.light`
- `Cells`, 173 — Батарейки.
- `Glow` (record), 97 — Как светит фонарь в этот тик: сила (доля от полной) и цвет.
- `LightActions`, 203 — Действия игрока с фонарём (сервер): включить, поменять батарейки, лампочку, керосин, режим.
- `LightType` (enum), 89 — Виды света: питание, как садится, луч.
- `Lights`, 195 — Состояние фонаря в NBT — общее для своих предметов и фонаря Survivor's Arsenal.

### `faygolover.rplights.mixin`
- `SaModeMixin`, 23 — Survivor's Arsenal 1.0.0: у фонаря есть ближний свет (20 блоков, 0.62), но isHighMode всегда true.

### `faygolover.rplights.mixin.client`
- `GuiGraphicsBarMixin`, 19 — Заряд фонаря на глаз не виден: полоску у фонаря Survivor's Arsenal в слоте не рисуем.
- `PostChainAccessor` (interface), 16 — Проходы своей цепочки — чтобы задать им лучи кадра.

### `faygolover.rplights.net`
- `LightsNet`, 78 — Сеть: клавиша фонаря (по умолчанию L) — включить / выключить надетый (налобный, нагрудный, иначе лампу на поясе); чернила чанков — клиенту (InkDelta).

### `faygolover.rplights.recipe`
- `LightRecipe`, 138 — Обслуживание фонаря в верстаке: фонарь + полный комплект батареек (обычных или аккумуляторов) — поменять батарейки; фонарь один — вынуть батарейки; ручной фонарь + лампочка — вкрутить новую; лампа + ведро керосина — заправить (вед…

### `faygolover.rplights.registry`
- `LightItems`, 110 — *без описания*

### `faygolover.rplights.server`
- `LightTicker`, 185 — Сервер.

Классов без описания: 1 из 36.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `BlockEvent.BreakEvent` | `InkEvents.onBreak` |
| `ChunkEvent.Unload` | `InkClient.onChunkUnload` |
| `ChunkWatchEvent.Watch` | `InkEvents.onWatch` |
| `ExplosionEvent.Detonate` | `InkEvents.onExplosion` |
| `FMLClientSetupEvent` | `LightsClient.setup` |
| `FMLCommonSetupEvent` | `RpLights.setup` |
| `ItemTooltipEvent` | `LightsClient.tooltip` |
| `LevelEvent.Unload` | `InkClient.onLevelUnload` |
| `LivingFallEvent` | `LightTicker.onFall` |
| `ModelEvent.RegisterAdditional` | `WornRenderer.registerModels` |
| `PlayerEvent.ItemCraftedEvent` | `LightTicker.onCrafted` |
| `PlayerInteractEvent.RightClickItem` | `LightTicker.onRightClickItem` |
| `RegisterKeyMappingsEvent` | `LightsClient.keys` |
| `RenderLevelStageEvent` | `InkClient.onRenderLevelStage`, `ScreenFlash.onRenderLevelStage` |
| `TickEvent.ClientTickEvent` | `LightsClient.tick` |
| `TickEvent.PlayerTickEvent` | `LightTicker.onPlayerTick` |

## 4. Сеть

Канал: `faygolover/rplights/net/LightsNet.java`, имя `main`, версия протокола `не объявлена константой`.
- `ToggleWorn` (направление не указано в регистрации)
- `InkDelta` (направление не указано в регистрации)

Классы с обработчиками пакетов (1): `LightsNet`.

## 5. Команды

Нет.

## 6. Данные

- SavedData: `InkData`, файл "rplights_ink"

## 7. Конфиги

- server: `LightsConfig`, вызовов define 22

## 8. Что берёт из RP Core и что ему даёт

Методы `RpCoreAPI` (сколько вызовов): `bodyScale` 4, `stamina` 1.

## 9. Связи с другими модами


Пакеты других модов семейства в коде: `flrender` — 5 файлов; `rpcore` — 4 файлов.

Миксины (3):
- `SaModeMixin` (mixins) → targets = "com.ogaba.sa_survival.item.common.FlashlightItem", remap = false
- `client.GuiGraphicsBarMixin` (client) → GuiGraphics.class
- `client.PostChainAccessor` (client) → PostChain.class

## 10. Зарегистрированные id

- `faygolover/rplights/registry/LightItems.java` (17): `hand_lantern` `old_flashlight` `headlamp` `uv_flashlight` `rgb_flashlight` `dynamo_flashlight` `hurricane_lamp` `uv_marker` `ink_sponge` `marker_` `accumulator` `bulb` `burnt_bulb` `battery_tester` `charger` `light_service` `lights`
