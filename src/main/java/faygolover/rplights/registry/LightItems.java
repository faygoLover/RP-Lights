package faygolover.rplights.registry;

import faygolover.rplights.RpLights;
import faygolover.rplights.block.ChargerBlock;
import faygolover.rplights.block.ChargerBlockEntity;
import faygolover.rplights.block.LampBlock;
import faygolover.rplights.block.LampBlockEntity;
import faygolover.rplights.item.HolderItem;
import faygolover.rplights.item.InkToolItem;
import net.minecraft.world.level.material.PushReaction;
import faygolover.rplights.item.AccumulatorItem;
import faygolover.rplights.item.DynamoItem;
import faygolover.rplights.item.LampItem;
import faygolover.rplights.item.LightItem;
import faygolover.rplights.item.TesterItem;
import faygolover.rplights.light.LightType;
import faygolover.rplights.recipe.LightRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class LightItems {
    private LightItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpLights.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpLights.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RpLights.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RpLights.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpLights.MODID);

    private static Item.Properties one() {
        return new Item.Properties().stacksTo(1);
    }

    public static final RegistryObject<Item> HAND_LANTERN = ITEMS.register("hand_lantern", () -> new LightItem(LightType.HAND_LANTERN, one()));
    public static final RegistryObject<Item> OLD_FLASHLIGHT = ITEMS.register("old_flashlight", () -> new LightItem(LightType.OLD, one()));
    public static final RegistryObject<Item> HEADLAMP = ITEMS.register("headlamp", () -> new LightItem(LightType.HEADLAMP, one()));
    public static final RegistryObject<Item> UV_FLASHLIGHT = ITEMS.register("uv_flashlight", () -> new LightItem(LightType.UV, one()));
    public static final RegistryObject<Item> RGB_FLASHLIGHT = ITEMS.register("rgb_flashlight", () -> new LightItem(LightType.RGB, one()));
    public static final RegistryObject<Item> DYNAMO = ITEMS.register("dynamo_flashlight", () -> new DynamoItem(one()));
    public static final RegistryObject<Block> LAMP_BLOCK = BLOCKS.register("hurricane_lamp", () -> new LampBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN).strength(0.5f).sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.DESTROY)
            .lightLevel(LampBlock::light)));
    public static final RegistryObject<Item> LAMP = ITEMS.register("hurricane_lamp", () -> new LampItem(LAMP_BLOCK.get(), one()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<LampBlockEntity>> LAMP_BE = BLOCK_ENTITIES.register("hurricane_lamp",
            () -> BlockEntityType.Builder.of(LampBlockEntity::new, LAMP_BLOCK.get()).build(null));
    /** УФ-маркер (невидимые чернила). */
    public static final RegistryObject<Item> UV_MARKER = ITEMS.register("uv_marker",
            () -> new InkToolItem(new Item.Properties().stacksTo(1).durability(InkToolItem.INK), 0));
    /** Обычные маркеры: {@code marker_<цвет>}, вид 1..8. */
    public static final java.util.List<RegistryObject<Item>> MARKERS = markers();
    public static final RegistryObject<Item> INK_SPONGE = ITEMS.register("ink_sponge",
            () -> new InkToolItem(new Item.Properties().stacksTo(1), -1));

    private static java.util.List<RegistryObject<Item>> markers() {
        java.util.List<RegistryObject<Item>> out = new java.util.ArrayList<>();
        for (int i = 0; i < InkToolItem.COLOR_IDS.length; i++) {
            int kind = i + 1;
            out.add(ITEMS.register("marker_" + InkToolItem.COLOR_IDS[i],
                    () -> new InkToolItem(new Item.Properties().stacksTo(1).durability(InkToolItem.INK), kind)));
        }
        return java.util.List.copyOf(out);
    }


    /** Пенал для маркеров (и УФ-маркера): до 5 штук, только они. */
    public static final RegistryObject<Item> MARKER_CASE = ITEMS.register("marker_case",
            () -> new HolderItem(one(), "marker_case", 5, s -> s.getItem() instanceof InkToolItem t && !t.sponge()));
    /** Кольцо для ХИС мода Glow Sticks: до 30 штук любых цветов, только они. */
    public static final RegistryObject<Item> GLOW_RING = ITEMS.register("glow_ring",
            () -> new HolderItem(one(), "glow_ring", 30, s -> {
                net.minecraft.resources.ResourceLocation k = ForgeRegistries.ITEMS.getKey(s.getItem());
                return k != null && k.getNamespace().equals("glow_sticks") && k.getPath().startsWith("glow_stick_");
            }));

    public static final RegistryObject<Item> ACCUMULATOR = ITEMS.register("accumulator", () -> new AccumulatorItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BULB = ITEMS.register("bulb", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BURNT_BULB = ITEMS.register("burnt_bulb", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TESTER = ITEMS.register("battery_tester", () -> new TesterItem(one()));

    public static final RegistryObject<Block> CHARGER = BLOCKS.register("charger", () -> new ChargerBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY).strength(1.0f).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> CHARGER_ITEM = ITEMS.register("charger", () -> new BlockItem(CHARGER.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ChargerBlockEntity>> CHARGER_BE = BLOCK_ENTITIES.register("charger",
            () -> BlockEntityType.Builder.of(ChargerBlockEntity::new, CHARGER.get()).build(null));

    public static final RegistryObject<RecipeSerializer<LightRecipe>> LIGHT_RECIPE = SERIALIZERS.register("light_service",
            () -> new SimpleCraftingRecipeSerializer<>(LightRecipe::new));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("lights", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rplights"))
            .icon(() -> new ItemStack(HAND_LANTERN.get()))
            .displayItems((params, out) -> {
                for (RegistryObject<Item> i : ITEMS.getEntries()) out.accept(i.get());
            })
            .build());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        SERIALIZERS.register(bus);
        TABS.register(bus);
    }
}
