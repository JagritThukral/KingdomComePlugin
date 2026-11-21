package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jagritthukral.kingdomcome.exception.AlreadyInKingdomException;
import dev.jagritthukral.kingdomcome.exception.DuplicateKingdomException;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.service.KingdomService;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KingdomCreateCommand {
    private final JavaPlugin plugin;
    private final KingdomService kingdomService;

    public KingdomCreateCommand(JavaPlugin plugin, KingdomService kingdomService) {
        this.plugin = plugin;
        this.kingdomService = kingdomService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("create")
                .requires(source -> source.getSender()
                        .isOp() || source.getSender()
                        .hasPermission("kingdomcome.create"))
                .then(Commands.argument("KingdomName", StringArgumentType.string())
                        .then(Commands.argument("leader_player", ArgumentTypes.player())
                                .executes(context -> createKingdom(context, StringArgumentType.getString(context, "KingdomName"))))

                );
    }

    private int createKingdom(CommandContext<CommandSourceStack> context, String kingdomName) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = context.getArgument("leader_player", PlayerSelectorArgumentResolver.class);
        Player executorPlayer = context.getSource()
                .getPlayerOrThrow();
        Player leaderPlayer = resolver.resolve(context.getSource())
                .getFirst();

        UUID leaderPlayerUuid = leaderPlayer.getUniqueId();
        CompletableFuture<Kingdom> future = kingdomService.createKingdom(kingdomName, leaderPlayerUuid, leaderPlayer.getName());
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("Kingdom '" + kingdomName + "' created successfully!"))
                .on(DuplicateKingdomException.class, "That kingdom name is already taken!")
                .on(AlreadyInKingdomException.class, "The specified leader player is already in a kingdom")
                .run();

        return Command.SINGLE_SUCCESS;
    }
}
