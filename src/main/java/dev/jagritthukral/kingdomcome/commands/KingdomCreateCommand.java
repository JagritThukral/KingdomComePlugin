package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.exception.DuplicateKingdomException;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class KingdomCreateCommand {
    private final JavaPlugin plugin;
    private final KingdomRepository kingdomRepository;

    public KingdomCreateCommand(JavaPlugin plugin, KingdomRepository kingdomRepository) {
        this.plugin = plugin;
        this.kingdomRepository = kingdomRepository;
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
        CommandAsyncUtils.handle(plugin, executorPlayer, kingdomRepository.createKingdomWithLeader(kingdomName, leaderPlayerUuid, leaderPlayer.getName()))
                .onSuccess(_ -> executorPlayer.sendMessage("Kingdom '" + kingdomName + "' created successfully!"))
                .on(DuplicateKingdomException.class, "That kingdom name is already taken!")
                .run();

        return Command.SINGLE_SUCCESS;
    }
}
