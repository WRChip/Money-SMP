package dev.flame.riptide;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

// riptide outside water for players tagged "riptide" (/tag <player> add riptide).
// the client refuses to even start charging a riptide trident out of water, so their
// tridents carry riptideanywhere:riptide instead: same name and sounds but no spin
// strength, which the client has no objection to. TridentItemMixin turns it back into
// a real riptide launch on release, server side.
public final class Riptide implements ModInitializer {
    public static final String TAG = "riptide";
    static final ResourceKey<Enchantment> DUMMY = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("riptideanywhere", "riptide"));

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(Riptide::tick);
    }

    public static int level(ItemStack stack) {
        for (Object2IntMap.Entry<Holder<Enchantment>> e : stack.getEnchantments().entrySet()) {
            if (e.getKey().is(DUMMY)) return e.getIntValue();
        }
        return 0;
    }

    // vanilla riptide.json: 1.5 at I, +0.75 per level
    public static float strength(int level) {
        return 1.5F + 0.75F * (level - 1);
    }

    static void tick(MinecraftServer server) {
        Registry<Enchantment> reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> vanilla = reg.getOrThrow(Enchantments.RIPTIDE);
        Holder<Enchantment> dummy = reg.getOrThrow(DUMMY);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean allowed = p.getTags().contains(TAG);
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(Items.TRIDENT)) continue;
                if (allowed) swap(s, vanilla, dummy);
                else swap(s, dummy, vanilla);
            }
        }
    }

    private static void swap(ItemStack s, Holder<Enchantment> from, Holder<Enchantment> to) {
        int lvl = EnchantmentHelper.getItemEnchantmentLevel(from, s);
        if (lvl <= 0) return;
        EnchantmentHelper.updateEnchantments(s, m -> {
            m.set(from, 0);
            m.set(to, lvl);
        });
    }
}
