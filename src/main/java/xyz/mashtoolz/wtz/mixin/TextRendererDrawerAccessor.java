package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.font.TextRenderer$Drawer")
public interface TextRendererDrawerAccessor {
    @Accessor("field_24240")
    TextRenderer wtz$getTextRenderer();
}
