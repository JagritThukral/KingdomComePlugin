package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.exception.KingdomNotFoundException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomOwnershipException;
import dev.jagritthukral.kingdomcome.service.KingdomService;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


public class KingdomDisbandCommand {
    private final JavaPlugin plugin;
    private final KingdomService kingdomService;

    public KingdomDisbandCommand(JavaPlugin plugin, KingdomService kingdomService) {
        this.plugin = plugin;
        this.kingdomService = kingdomService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("disband")
                .then(Commands.argument("KingdomName", StringArgumentType.string())
                        .executes(ctx -> disbandKingdom(ctx, StringArgumentType.getString(ctx, "KingdomName")))
                )
                .requires(source -> source.getSender()
                        .isOp() || source.getSender()
                        .hasPermission("kingdomcome.disband"));

    }

    private int disbandKingdom(CommandContext<CommandSourceStack> context, String name) {
        CommandSourceStack source = context.getSource();
        try {
            Player player = source.getPlayerOrThrow();

            CommandAsyncUtils.handle(plugin, player, kingdomService.disbandKingdom(name))
                    .onSuccess(_ -> player.sendMessage("Disbanded Kingdom " + name))
                    .on(KingdomNotFoundException.class, "No kingdom named '" + name + "' was found.")
                    .on(MissingKingdomOwnershipException.class, "You are not the leader of this kingdom.\nOnly a leader can disband a kingdom.")
                    .run();
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.getSender()
                    .sendMessage("An internal error occurred while running this command.");
            plugin.getSLF4JLogger()
                    .error(e.toString());
            return 0;
        }
    }
}
