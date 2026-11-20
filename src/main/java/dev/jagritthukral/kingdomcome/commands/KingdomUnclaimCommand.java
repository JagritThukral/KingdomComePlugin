package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.ClaimStatus;
import dev.jagritthukral.kingdomcome.service.ClaimService;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KingdomUnclaimCommand {
    private final JavaPlugin plugin;
    private final ClaimService claimService;

    public KingdomUnclaimCommand(JavaPlugin plugin, ClaimService claimService) {
        this.plugin = plugin;
        this.claimService = claimService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("unclaim")
                .executes(this::unclaimChunk);
    }

    private int unclaimChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player player)) {
            source.getSender()
                    .sendMessage("Only players can unclaim lands");
            return 0;
        }
        Chunk chunk = player.getChunk();
        UUID worldUuid = chunk.getWorld()
                .getUID();
        UUID playerUuid = player.getUniqueId();
        int chunkX = chunk.getX(), chunkZ = chunk.getZ();
        CompletableFuture<ClaimStatus> future = claimService.unclaimChunk(playerUuid, chunkX, chunkZ, worldUuid);


        CommandAsyncUtils.handle(plugin, player, future)
                .onSuccess(claimStatus -> player.sendMessage(claimStatus.getMessage()))
                .on(MissingKingdomMembershipException.class, _ -> player.sendMessage("You need to be member of kingdom to unclaim a chunk"))
                .on(MissingKingdomPermissionException.class, e -> player.sendMessage(e.getMessage()))
                .run();
        return Command.SINGLE_SUCCESS;
    }
}
