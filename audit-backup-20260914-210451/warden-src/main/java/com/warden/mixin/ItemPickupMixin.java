package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Blocks item pickup at the collection point when the player is already at the item limit.
 * This is the primary/fast-path defence — fires before the item enters the inventory.
 * The tick-based enforcer in WardenMod acts as fallback for other acquisition paths
 * (crafting, chests, /give, etc.).
 */
@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin {

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void onPlayerCollision(PlayerEntity player, CallbackInfo ci) {
        if (!WardenMod.CONFIG.itemLimitsEnabled) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (WardenMod.isExempt(player)) return;

        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemStack stack = itemEntity.getStack();
        if (stack.isEmpty()) return;

        // counts nested contents too, so a shulker/bundle full of banned items can't be picked up
        Map<String, Integer> picked = new HashMap<>();
        WardenMod.countItemsRecursive(stack, picked);
        if (picked.isEmpty()) return;

        PlayerInventory inv = player.getInventory();
        for (Map.Entry<String, Integer> e : picked.entrySet()) {
            String id = e.getKey();
            int limit = WardenMod.CONFIG.itemLimits.get(id);
            if (WardenMod.countItemsInInventory(inv, id) + e.getValue() > limit) {
                WardenMod.sendPickupBlockedNotice(serverPlayer, id, limit);
                ci.cancel();
                return;
            }
        }
    }
}
