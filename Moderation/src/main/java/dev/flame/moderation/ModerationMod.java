package dev.flame.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ModerationMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("Moderation");

    private static final DynamicCommandExceptionType PLAYER_NOT_FOUND =
            new DynamicCommandExceptionType(name -> prefix().append(Text.literal("Player not found or offline: " + name).formatted(Formatting.RED)));
    private static final DynamicCommandExceptionType NO_PLAYER_DATA =
            new DynamicCommandExceptionType(name -> prefix().append(Text.literal("No saved data for player: " + name).formatted(Formatting.RED)));

    @Override
    public void onInitialize() {
        Moderation.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("freeze").requires(ModerationMod::isAdmin)
                    .then(argument("player", StringArgumentType.word())
                            .suggests(ModerationMod::suggestOnlinePlayers)
                            .executes(ModerationMod::freezeToggle)));
            dispatcher.register(literal("mute").requires(ModerationMod::isAdmin)
                    .then(argument("player", StringArgumentType.word())
                            .suggests(ModerationMod::suggestOnlinePlayers)
                            .executes(ModerationMod::muteToggle)));
            dispatcher.register(literal("inv").requires(ModerationMod::isAdmin)
                    .then(argument("player", StringArgumentType.word())
                            .suggests(ModerationMod::suggestKnownPlayers)
                            .executes(ModerationMod::openInventory)));
            dispatcher.register(literal("enderchest").requires(ModerationMod::isAdmin)
                    .then(argument("player", StringArgumentType.word())
                            .suggests(ModerationMod::suggestKnownPlayers)
                            .executes(ModerationMod::openEnderChest)));
        });
    }

    static MutableText prefix() {
        return Text.literal("[")
                .append(Text.literal("MODERATION").formatted(Formatting.DARK_PURPLE, Formatting.BOLD))
                .append(Text.literal("] "));
    }

    private static boolean isAdmin(ServerCommandSource src) {
        return src.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS));
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(
                ctx.getSource().getServer().getPlayerManager().getPlayerList().stream().map(p -> p.getName().getString()).toList(), builder);
    }

    // inv/enderchest work on anyone with playerdata on disk, so suggest the offline names too
    private static CompletableFuture<Suggestions> suggestKnownPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        Set<String> names = new LinkedHashSet<>(List.of(server.getPlayerManager().getPlayerNames()));
        try (var files = Files.list(server.getSavePath(WorldSavePath.PLAYERDATA))) {
            files.map(path -> path.getFileName().toString())
                    .filter(file -> file.endsWith(".dat"))
                    .forEach(file -> parseUuid(file.substring(0, file.length() - 4))
                            .flatMap(id -> server.getApiServices().nameToIdCache().getByUuid(id))
                            .ifPresent(entry -> names.add(entry.name())));
        } catch (IOException e) {
            LOGGER.warn("[Moderation] could not list playerdata for suggestions", e);
        }
        return CommandSource.suggestMatching(names, builder);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static ServerPlayerEntity resolveOnlinePlayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "player");
        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
        if (target == null) {
            throw PLAYER_NOT_FOUND.create(name);
        }
        return target;
    }

    private static PlayerConfigEntry resolvePlayerProfile(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        if (online != null) {
            return new PlayerConfigEntry(online.getGameProfile());
        }
        return server.getApiServices().nameToIdCache().findByName(name)
                .orElseThrow(() -> NO_PLAYER_DATA.create(name));
    }

    private static int freezeToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = resolveOnlinePlayer(ctx);
        boolean frozen = Moderation.toggleFreeze(target);
        ctx.getSource().sendFeedback(() -> prefix()
                .append(Text.literal(target.getName().getString()).formatted(Formatting.AQUA))
                .append(Text.literal(frozen ? " is now frozen." : " is no longer frozen.").formatted(frozen ? Formatting.RED : Formatting.GREEN)), true);
        target.sendMessage(prefix().append(Text.literal(frozen ? "You have been frozen by an admin." : "You have been unfrozen.")
                .formatted(frozen ? Formatting.RED : Formatting.GREEN)));
        return 1;
    }

    private static int muteToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = resolveOnlinePlayer(ctx);
        boolean muted = Moderation.toggleMute(target);
        ctx.getSource().sendFeedback(() -> prefix()
                .append(Text.literal(target.getName().getString()).formatted(Formatting.AQUA))
                .append(Text.literal(muted ? " is now muted." : " is no longer muted.").formatted(muted ? Formatting.RED : Formatting.GREEN)), true);
        target.sendMessage(prefix().append(Text.literal(muted ? "You have been muted by an admin." : "You have been unmuted.")
                .formatted(muted ? Formatting.RED : Formatting.GREEN)));
        return 1;
    }

    private static int openInventory(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity moderator = ctx.getSource().getPlayerOrThrow();
        PlayerConfigEntry profile = resolvePlayerProfile(ctx);
        if (!Moderation.openInventoryView(ctx.getSource().getServer(), moderator, profile)) {
            throw NO_PLAYER_DATA.create(profile.name());
        }
        return 1;
    }

    private static int openEnderChest(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity moderator = ctx.getSource().getPlayerOrThrow();
        PlayerConfigEntry profile = resolvePlayerProfile(ctx);
        if (!Moderation.openEnderChestView(ctx.getSource().getServer(), moderator, profile)) {
            throw NO_PLAYER_DATA.create(profile.name());
        }
        return 1;
    }
}
