package dev.flame.riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.flame.riptide.Riptide;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the client charged a trident it thinks has no riptide, so it predicted nothing on
// release. vanilla's own riptide branch runs here on the server (stat, durability,
// spin attack, sound) and the resulting movement is pushed down to the client
@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getTridentSpinAttackStrength(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)F"))
    private float riptide$strength(float f, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) LivingEntity user) {
        int lvl = Riptide.level(stack);
        return lvl > 0 && user instanceof ServerPlayer ? Riptide.strength(lvl) : f;
    }

    @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
    private boolean riptide$anywhere(boolean wet, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) LivingEntity user) {
        return wet || user instanceof ServerPlayer p && p.getTags().contains(Riptide.TAG) && Riptide.level(stack) > 0;
    }

    // vanilla hops 1.2 blocks off the ground here, on both sides. only the server did it
    // this time, so undo it and send it as a relative teleport, which keeps velocity
    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void riptide$hop(Player player, MoverType type, Vec3 by, Operation<Void> op, @Local(argsOnly = true) ItemStack stack) {
        Vec3 from = player.position();
        op.call(player, type, by);
        if (!(player instanceof ServerPlayer p) || Riptide.level(stack) <= 0) return;
        double dy = p.getY() - from.y;
        Vec3 push = p.getDeltaMovement();
        p.setPos(from);
        p.connection.teleport(new PositionMoveRotation(new Vec3(0, dy, 0), Vec3.ZERO, 0, 0), Relative.ALL);
        // the teleport clears it server side, and riptide$sync sends whatever is left
        p.setDeltaMovement(push);
    }

    // sent straight away: waiting for the tracker lets a server tick of ground friction eat half of it
    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void riptide$sync(ItemStack stack, Level level, LivingEntity user, int left, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && user instanceof ServerPlayer p && Riptide.level(stack) > 0) {
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
    }
}
