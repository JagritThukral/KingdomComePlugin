package dev.jagritthukral.KingdomCome.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.KingdomCome.database.repository.KingdomRepository;
import dev.jagritthukral.KingdomCome.exception.DuplicateKingdomException;
import dev.jagritthukral.KingdomCome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
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
                                .executes(context -> createKingdom(
                                        context,
                                        StringArgumentType.getString(context, "KingdomName"),
                                        StringArgumentType.getString(context,"leader_player")
                                ))
                        )
                );
    }

    private int createKingdom(CommandContext<CommandSourceStack> context, String name,String leader) {
        CommandSourceStack source = context.getSource();

        Player player = Bukkit.getPlayer(leader);

        if (player == null){
            source.getSender().sendMessage("Unable to get that player. Player must be online");
            return 0;
        }


        CommandAsyncUtils.handle(plugin, player, kingdomRepository.createKingdomWithLeader(name, player.getUniqueId().toString()))
                .onSuccess(_ -> player.sendMessage("Kingdom '" + name + "' created successfully!"))
                .on(DuplicateKingdomException.class, "That kingdom name is already taken!")
                .run();

        return 1;
    }
}