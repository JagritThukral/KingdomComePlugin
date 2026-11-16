package dev.jagritthukral.KingdomCome.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jagritthukral.KingdomCome.database.repository.KingdomRepository;
import dev.jagritthukral.KingdomCome.exception.DuplicateKingdomException;
import dev.jagritthukral.KingdomCome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class KingdomCreateCommand {
    private final JavaPlugin plugin;
    private final KingdomRepository kingdomRepository;

    public KingdomCreateCommand(JavaPlugin plugin, KingdomRepository kingdomRepository) {
        this.plugin = plugin;
        this.kingdomRepository = kingdomRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("create")
                .then(Commands.argument("KingdomName", StringArgumentType.string())
                        .then(Commands.argument("leader_player", ArgumentTypes.player())
                                .executes(this::createKingdom))

                );
    }

    private int createKingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final String kingdomName = context.getArgument("KingdomName", StringArgumentType.class).toString();
        final PlayerSelectorArgumentResolver resolver = context.getArgument("leader_player", PlayerSelectorArgumentResolver.class);
        final Player leaderPlayer = resolver.resolve(context.getSource()).getFirst();
        final String leaderPlayerUuid = leaderPlayer.getUniqueId().toString();
        CommandAsyncUtils.handle(plugin, leaderPlayer, kingdomRepository.createKingdomWithLeader(kingdomName, leaderPlayerUuid))
                .onSuccess(_ -> leaderPlayer.sendMessage("Kingdom '" + kingdomName + "' created successfully!"))
                .on(DuplicateKingdomException.class, "That kingdom name is already taken!")
                .run();

        return 1;
    }
}