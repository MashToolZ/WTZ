package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextRenderer.class)
public interface TextRendererAccessor {
    @Invoker("getGlyph")
    BakedGlyph wtz$getGlyph(int codepoint, Style style);
}
