package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.mount.MountCamera;
import xyz.mashtoolz.wtz.features.mount.stats.MountStatsOverlay;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListRenderer;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void WTZ_onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        int slot = hand == Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
        MountStatsOverlay.onItemUsed(slot);
        MountCamera.getInstance().onItemUsed(player.getStackInHand(hand));
    }

    @Inject(method = "interactEntity", at = @At("HEAD"))
    private void WTZ_onInteractEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ShoppingListRenderer.getInstance().onNpcInteraction();
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"))
    private void WTZ_onInteractEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hitResult, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ShoppingListRenderer.getInstance().onNpcInteraction();
    }
}
