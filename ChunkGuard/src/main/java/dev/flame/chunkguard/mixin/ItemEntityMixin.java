package dev.flame.chunkguard.mixin;

import dev.flame.chunkguard.ChunkGuardMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dropped item's full stack goes out in its entity data, so a single oversized item on the
 * ground bans the chunk for anyone who renders it. Check once on the entity's first tick.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Unique
    private boolean chunkguard$sizeChecked;

    @Inject(method = "tick", at = @At("HEAD"))
    private void chunkguard$discardOversized(CallbackInfo ci) {
        if (chunkguard$sizeChecked) {
            return;
        }
        chunkguard$sizeChecked = true;
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getEntityWorld().isClient()) {
            return;
        }
        ItemStack stack = self.getStack();
        if (ChunkGuardMod.isOversized(stack)) {
            ChunkGuardMod.logOversized("drop:" + self.getBlockPos().asLong(),
                    "discarded dropped " + ChunkGuardMod.shortId(stack)
                            + " at " + self.getBlockPos().toShortString() + " (" + (ChunkGuardMod.itemBytes(stack) / 1024) + " KB)");
            self.discard();
        }
    }

    // never lets one into an inventory, creative or not; the entity itself is the hazard
    @Inject(method = "onPlayerCollision", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;insertStack(Lnet/minecraft/item/ItemStack;)Z"), cancellable = true)
    private void chunkguard$refusePickup(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity)) return;
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getStack();
        if (ChunkGuardMod.isOversized(stack)) {
            ChunkGuardMod.logOversized("pickup:" + player.getName().getString(),
                    "discarded oversized " + ChunkGuardMod.shortId(stack) + " " + player.getName().getString() + " tried to pick up");
            self.discard();
            ci.cancel();
        }
    }
}
