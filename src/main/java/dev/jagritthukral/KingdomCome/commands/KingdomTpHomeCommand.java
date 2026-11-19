package dev.jagritthukral.KingdomCome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.KingdomCome.database.repository.KingdomRepository;
import dev.jagritthukral.KingdomCome.database.repository.MemberRepository;
import dev.jagritthukral.KingdomCome.exception.KingdomHomeNotSetException;
import dev.jagritthukral.KingdomCome.exception.KingdomNotFoundException;
import dev.jagritthukral.KingdomCome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.KingdomCome.models.KingdomHome;
import dev.jagritthukral.KingdomCome.models.KingdomMember;
import dev.jagritthukral.KingdomCome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
            source.getSender().sendMessage("Only players can use this command to teleport to their kingdom's home coordinates.");
            return 0;
        }
        String executorPlayerUuid = executorPlayer.getUniqueId().toString();

        CompletableFuture<KingdomHome> future = memberRepository.getKingdomMember(executorPlayerUuid)
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember kingdomMember = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                    int kingdomId = kingdomMember.kingdomId();
                    return kingdomRepository.getKingdomHome(kingdomId);
                });
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .on(KingdomNotFoundException.class, "An issue while getting your kingdom occurred")
                .on(MissingKingdomMembershipException.class, "You need to be member of a kingdom to tp to its home")
                .on(KingdomHomeNotSetException.class, "Kingdom's home location has not been set")
                .run();
        return Command.SINGLE_SUCCESS;
    }
}