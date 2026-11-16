package dev.jagritthukral.KingdomCome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.KingdomCome.database.repository.KingdomRepository;
import dev.jagritthukral.KingdomCome.database.repository.MemberRepository;
import dev.jagritthukral.KingdomCome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.KingdomCome.exception.MissingKingdomPermissionException;

import static dev.jagritthukral.KingdomCome.exception.MissingKingdomPermissionException.MissingPermission.*;

import dev.jagritthukral.KingdomCome.models.KingdomHome;
import dev.jagritthukral.KingdomCome.models.KingdomMember;
import dev.jagritthukral.KingdomCome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class KingdomSetHomeCommand {
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;
    private final KingdomRepository kingdomRepository;

    public KingdomSetHomeCommand(JavaPlugin plugin, MemberRepository memberRepository, KingdomRepository kingdomRepository) {
        this.plugin = plugin;
        this.memberRepository = memberRepository;
        this.kingdomRepository = kingdomRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("set_home")
                .executes(this::setHomeCords);
    }

    private int setHomeCords(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender().sendMessage("Only players can set kingdom's home location");
            return 0;
        }

        String executorPlayerUuid = executorPlayer.getUniqueId().toString();
        Location location = executorPlayer.getLocation();
        CompletableFuture<Void> future = memberRepository.getKingdomMember(executorPlayerUuid).thenCompose(optionalKingdomMember -> {
            KingdomMember kingdomMember = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
            if (!kingdomMember.role().canSetHome()) throw new MissingKingdomPermissionException(SET_HOME);
            return kingdomRepository.setKingdomHome(kingdomMember.kingdomId(), new KingdomHome(location));
        });
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("set home for your kingdom successfully"))
                .on(MissingKingdomMembershipException.class, "You need to be member of a kingdom to")
                .on(MissingKingdomPermissionException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .run();

        return Command.SINGLE_SUCCESS;
    }
}