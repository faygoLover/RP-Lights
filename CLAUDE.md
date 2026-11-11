# RP Lights

Мод Minecraft Forge 1.20.1 (Java 17), `rplights`, пакет `faygolover.rplights`. Фонари и лампы семейства RP.
**С автором общаемся по-русски.** План, решения, числа — `docs/00_plan.md`.

## Устройство
- `light/LightType` — виды света (батарейки, время, луч, цвет). `light/Lights` — состояние в NBT (общее со SA:
  `FlashlightOn`/`FlashlightBattery` пишутся из наших батареек). `light/Cells` — батарейки. `light/Glow` — как светит
  в тик (мигания по тику, без пакетов). `light/LightActions` — действия игрока.
- `server/LightTicker` — раз в секунду расход по `RpCoreAPI.bodyScale()` (RP-время), поломки; раз в 0,5 с — заряд
  найденному (все слоты `containerMenu`), сушка старого. Shift+ПКМ фонарём SA, падение, возврат при крафте.
- `recipe/LightRecipe` — обслуживание в верстаке (`rplights:light_service`).
- `block/Charger*` — зарядка. `compat/CuriosCompat` — слоты head/body/belt (`data/rplights/curios`).
- Клиент: `ClientBeams` (лучи кадра → FL Render `LightBeams`), `ScreenFlash` (свой экранный проход,
  `shaders/program/flash.fsh`), `LightsClient` (модель «горит», клавиша L, подсказка SA).
- Миксины: `SaModeMixin` (ближний свет SA, цель строкой), `GuiGraphicsBarMixin` (нет полоски у SA), `PostChainAccessor`.

## Сборка
`JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.14.7-hotspot" ./gradlew build -x test` → `build/libs`.
GameTest: `./gradlew runGameTestServer`. Иконки и модели — `python tools/gen_assets.py`, переводы — `tools/gen_lang.py`.
Сборка S5: `tools/pack_loot.py`, `tools/pack_lights.py` (повторный запуск не дублирует).
