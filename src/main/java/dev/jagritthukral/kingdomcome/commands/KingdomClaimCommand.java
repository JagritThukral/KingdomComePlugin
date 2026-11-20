package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.database.repository.ClaimRepository;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.*;

import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.ClaimStatus;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class KingdomClaimCommand {
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;

    public KingdomClaimCommand(JavaPlugin plugin, MemberRepository memberRepository, ClaimRepository claimRepository) {
        this.plugin = plugin;
        this.memberRepository = memberRepository;
        this.claimRepository = claimRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("claim")
                .executes(this::claimChunk);
    }

    private int claimChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player player)) {
            source.getSender().sendMessage("Only players can claim lands");
            return 0;
        }
        Chunk chunk = player.getChunk();
        String uuid = player.getUniqueId().toString();
        int chunkX = chunk.getX(), chunkZ = chunk.getZ();
        String worldUuidString = chunk.getWorld().getUID().toString();
        CompletableFuture<ClaimStatus> future = memberRepository.getKingdomMember(uuid)
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember member = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                    if (!member.role().canClaimOrUnclaimChunks())
                        throw new MissingKingdomPermissionException(CLAIM_CHUNK);
                    return claimRepository.claimChunk(member.kingdomId(), chunkX, chunkZ, worldUuidString);
                });


        CommandAsyncUtils.handle(plugin, player, future)
                .onSuccess(result -> player.sendMessage(result.getMessage()))
                .on(MissingKingdomMembershipException.class, _ -> player.sendMessage("You need to be a leader or duke of a kingdom to claim a chunk"))
                .on(MissingKingdomPermissionException.class, e -> player.sendMessage(e.getMessage()))
                .run();
        return Command.SINGLE_SUCCESS;
    }

}
