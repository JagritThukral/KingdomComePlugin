package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.exception.KingdomHomeNotSetException;
import dev.jagritthukral.kingdomcome.exception.KingdomNotFoundException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.WorldNotLoadedException;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KingdomTpHomeCommand {
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;
    private final KingdomRepository kingdomRepository;

    public KingdomTpHomeCommand(JavaPlugin plugin, MemberRepository memberRepository, KingdomRepository kingdomRepository) {
        this.plugin = plugin;
        this.memberRepository = memberRepository;
        this.kingdomRepository = kingdomRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("tp_home")
                .executes(this::tpToKingdomHome);
    }

    private int tpToKingdomHome(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender()
                    .sendMessage("Only players can use this command to teleport to their kingdom's home coordinates.");
            return 0;
        }
        UUID executorPlayerUuid = executorPlayer.getUniqueId();
        CompletableFuture<Boolean> future = memberRepository.getKingdomMember(executorPlayerUuid)
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember kingdomMember = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                    int kingdomId = kingdomMember.kingdomId();
                    return kingdomRepository.getKingdomHome(kingdomId)
                            .thenCompose(home -> executorPlayer.teleportAsync(home.getBukkitLocation()));
                });

        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(success -> {
                    if (!success) executorPlayer.sendMessage("Teleport was cancelled or failed");
                })
                .on(KingdomNotFoundException.class, "An issue while getting your kingdom occurred")
                .on(MissingKingdomMembershipException.class, "You need to be member of a kingdom to tp to its home")
                .on(KingdomHomeNotSetException.class, "Kingdom's home location has not been set")
                .on(WorldNotLoadedException.class, "Failed to teleport because the world is not loaded or doesn't exist")
                .run();
        return Command.SINGLE_SUCCESS;
    }
}
