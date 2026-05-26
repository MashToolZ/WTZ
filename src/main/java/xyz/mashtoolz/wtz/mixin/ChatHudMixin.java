package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.qol.QualityOfLife;
import xyz.mashtoolz.wtz.features.tts.ShoutTTS;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void WTZ_onAddMessage(Text message, CallbackInfo ci) {
        if (WTZ$isWelcomeMessage(message)) {
            WTZClient.onWynncraftJoin();
        }

        if (QualityOfLife.trySellAll(message.getString())) {
            ci.cancel();
            return;
        }
        if (QualityOfLife.trySearchPrompt(message.getString())) {
            ci.cancel();
            return;
        }
        if (QualityOfLife.trySuppressSearchEcho(message.getString())) {
            ci.cancel();
            return;
        }
        ShoutTTS.trySpeak(message.getString());
    }

    @Unique
    private static boolean WTZ$isWelcomeMessage(Text text) {
        if (!text.getString().contains("Welcome to Wynncraft!")) return false;

        if (text.getStyle().isBold()) return true;
        for (Text sibling : text.getSiblings()) {
            if (WTZ$hasBoldWelcome(sibling)) return true;
        }
        return false;
    }

    @Unique
    private static boolean WTZ$hasBoldWelcome(Text text) {
        if (text.getString().contains("Welcome to Wynncraft!") && text.getStyle().isBold()) return true;
        for (Text sibling : text.getSiblings()) {
            if (WTZ$hasBoldWelcome(sibling)) return true;
        }
        return false;
    }
}
