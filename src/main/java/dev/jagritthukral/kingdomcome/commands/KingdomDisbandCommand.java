package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.exception.KingdomNotFoundException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomOwnershipException;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;


public class KingdomDisbandCommand {
    private final JavaPlugin plugin;
    private final KingdomRepository kingdomRepository;

    public KingdomDisbandCommand(JavaPlugin plugin, KingdomRepository kingdomRepository) {
        this.plugin = plugin;
        this.kingdomRepository = kingdomRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("disband")
                .then(Commands.argument("KingdomName", StringArgumentType.string())
                        .executes(ctx -> disbandKingdom(ctx, StringArgumentType.getString(ctx, "KingdomName")))
                );
    }

    private int disbandKingdom(CommandContext<CommandSourceStack> context, String name) {
        CommandSourceStack source = context.getSource();
        try {
            Player player = source.getPlayerOrThrow();
            boolean isOp = source.getSender()
                    .isOp();


            CommandAsyncUtils.handle(plugin, player, kingdomRepository.findKingdomByName(name)
                            .thenCompose(kingdomOptional -> {
                                Kingdom kingdom = kingdomOptional.orElseThrow(() -> new KingdomNotFoundException(name));
                                if (!isOp && !Objects.equals(kingdom.leaderUuid(), player.getUniqueId())) {
                                    throw new MissingKingdomOwnershipException();
                                }
                                return kingdomRepository.disbandKingdom(name);

                            }))
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
