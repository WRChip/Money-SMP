package dev.flame.restore;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RestoreMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("Restore");

    @Override
    public void onInitialize() {
        NewWorldWatcher.register();
        Restore.register();
        // /restore <countdownSeconds> <endTitle> <pvpDelayMinutes>
        // endTitle needs quotes if it has spaces, since it sits between two numeric arguments.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("restore")
                        .requires(src -> src.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .then(argument("countdownSeconds", IntegerArgumentType.integer(0))
                                .then(argument("endTitle", StringArgumentType.string())
                                        .then(argument("pvpDelayMinutes", IntegerArgumentType.integer(0))
                                                .executes(RestoreMod::restore))))));
    }

    static MutableText prefix() {
        return Text.literal("[")
                .append(Text.literal("RESTORE").formatted(Formatting.DARK_PURPLE, Formatting.BOLD))
                .append(Text.literal("] "));
    }

    private static int restore(CommandContext<ServerCommandSource> ctx) {
        if (Restore.isActive()) {
            ctx.getSource().sendFeedback(() -> prefix().append(Text.literal("A restore is already in progress.").formatted(Formatting.RED)), false);
            return 0;
        }
        int countdownSeconds = IntegerArgumentType.getInteger(ctx, "countdownSeconds");
        String endTitle = StringArgumentType.getString(ctx, "endTitle");
        int pvpDelayMinutes = IntegerArgumentType.getInteger(ctx, "pvpDelayMinutes");
        Restore.start(ctx.getSource().getServer(), countdownSeconds,
                Text.literal(endTitle).formatted(Formatting.GREEN, Formatting.BOLD), pvpDelayMinutes);
        ctx.getSource().sendFeedback(() -> prefix().append(Text.literal(
                "Restore started: " + countdownSeconds + "s countdown, PVP enables in " + pvpDelayMinutes + " min.")
                .formatted(Formatting.GREEN)), true);
        return 1;
    }
}
