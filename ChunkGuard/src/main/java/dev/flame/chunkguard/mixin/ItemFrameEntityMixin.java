package dev.flame.chunkguard.mixin;

import dev.flame.chunkguard.ChunkGuardMod;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An item frame's held stack is tracked entity data, same hazard as a dropped item. Refuse it. */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameEntityMixin {

    // interact() decrements the player's stack right after setHeldItemStack without checking it
    // took, so refusing only in the setter would delete the item; refuse the click itself first
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void chunkguard$refuseOversizedClick(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemFrameEntity self = (ItemFrameEntity) (Object) this;
        if (self.getEntityWorld().isClient() || !self.getHeldItemStack().isEmpty()) {
            return;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (!ChunkGuardMod.isOversized(stack)) {
            return;
        }
        ChunkGuardMod.logOversized("frame:" + self.getBlockPos().asLong(),
                "refused oversized " + ChunkGuardMod.shortId(stack)
                        + " in an item frame at " + self.getBlockPos().toShortString());
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "setHeldItemStack(Lnet/minecraft/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void chunkguard$refuseOversized(ItemStack stack, boolean update, CallbackInfo ci) {
        ItemFrameEntity self = (ItemFrameEntity) (Object) this;
        if (self.getEntityWorld().isClient() || !ChunkGuardMod.isOversized(stack)) {
            return;
        }
        ChunkGuardMod.logOversized("frame:" + self.getBlockPos().asLong(),
                "refused oversized " + ChunkGuardMod.shortId(stack)
                        + " in an item frame at " + self.getBlockPos().toShortString());
        ci.cancel();
    }
}
