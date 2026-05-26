package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.mount.MountCamera;
import xyz.mashtoolz.wtz.features.mount.MountStatsOverlay;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void WTZ_onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        int slot = hand == Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
        MountStatsOverlay.onItemUsed(slot);
        MountCamera.getInstance().onItemUsed(player.getStackInHand(hand));
    }
}
