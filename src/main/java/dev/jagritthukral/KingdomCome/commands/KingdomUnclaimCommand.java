package dev.jagritthukral.KingdomCome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.KingdomCome.database.repository.ClaimRepository;
import dev.jagritthukral.KingdomCome.database.repository.MemberRepository;

import static dev.jagritthukral.KingdomCome.exception.MissingKingdomPermissionException.MissingPermission.*;

import dev.jagritthukral.KingdomCome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.KingdomCome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.KingdomCome.models.ClaimStatus;
import dev.jagritthukral.KingdomCome.models.KingdomMember;
import dev.jagritthukral.KingdomCome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class KingdomUnclaimCommand {
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;

    public KingdomUnclaimCommand(JavaPlugin plugin, MemberRepository memberRepository, ClaimRepository claimRepository) {
        this.plugin = plugin;
        this.memberRepository = memberRepository;
        this.claimRepository = claimRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("unclaim")
                .executes(this::unclaimChunk);
    }

    private int unclaimChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player player)) {
            source.getSender().sendMessage("Only players can unclaim lands");
            return 0;
        }
        Chunk chunk = player.getChunk();
        String worldUuidString = chunk.getWorld().getUID().toString();
        String uuid = player.getUniqueId().toString();
        int chunkX = chunk.getX(), chunkZ = chunk.getZ();
        CompletableFuture<ClaimStatus> future = memberRepository.getKingdomMember(uuid)
                .thenCompose(optionalClaim -> {
                    KingdomMember member = optionalClaim.orElseThrow(MissingKingdomMembershipException::new);
                    if (!member.role().canClaimOrUnclaimChunks())
                        throw new MissingKingdomPermissionException(UNCLAIM_CHUNK);
                    return claimRepository.unclaimChunk(member.kingdomId(), chunkX, chunkZ, worldUuidString);
                });


        CommandAsyncUtils.handle(plugin, player, future)
                .onSuccess(claimStatus -> player.sendMessage(claimStatus.getMessage()))
                .on(MissingKingdomMembershipException.class, e -> player.sendMessage("You need to be member of kingdom to unclaim a chunk"))
                .on(MissingKingdomPermissionException.class, e -> player.sendMessage(e.getMessage()))
                .run();
        return Command.SINGLE_SUCCESS;
    }
}
