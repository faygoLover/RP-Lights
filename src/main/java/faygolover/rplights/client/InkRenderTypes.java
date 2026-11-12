package faygolover.rplights.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Слои чернил: обычные — как краска (свет мира, полупрозрачно), УФ — светятся (складываются со светом). Оба со сдвигом
 * глубины к камере (как трещины ломания блока), чтобы не мерцать с гранью.
 */
public final class InkRenderTypes extends RenderType {
    private InkRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                           boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static final RenderType PAINT = create("rplights_ink_paint", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LEASH_SHADER).setTextureState(NO_TEXTURE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setLightmapState(LIGHTMAP).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).setLayeringState(POLYGON_OFFSET_LAYERING).createCompositeState(false));

    public static final RenderType GLOW = create("rplights_ink_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));
}
