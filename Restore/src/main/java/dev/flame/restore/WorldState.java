package dev.flame.restore;

import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

final class WorldState extends PersistentState {
    // keeps the id Warden saved it under, so a world locked or restored before the split stays that way
    private static final PersistentStateType<WorldState> TYPE = new PersistentStateType<>(
            "warden_world", () -> new WorldState(true),
            Codec.BOOL.fieldOf("locked").xmap(WorldState::new, state -> state.locked).codec(), null);

    private boolean locked;

    private WorldState(boolean locked) {
        this.locked = locked;
    }

    static WorldState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().get(TYPE);
    }

    static void setLocked(MinecraftServer server, boolean locked) {
        WorldState state = server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
        state.locked = locked;
        state.markDirty();
    }

    boolean isLocked() {
        return locked;
    }
}
