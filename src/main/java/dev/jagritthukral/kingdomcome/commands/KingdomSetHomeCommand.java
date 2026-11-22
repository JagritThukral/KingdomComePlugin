package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.KingdomHome;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.KingdomService;
import dev.jagritthukral.kingdomcome.service.MemberService;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.SET_HOME;

public class KingdomSetHomeCommand {
    private final JavaPlugin plugin;
    private final KingdomService kingdomService;
    private final MemberService memberService;

    public KingdomSetHomeCommand(JavaPlugin plugin, KingdomService kingdomService, MemberService memberService) {
        this.plugin = plugin;
        this.kingdomService = kingdomService;
        this.memberService = memberService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("set_home")
                .executes(this::setHomeCords);
    }

    private int setHomeCords(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender()
                    .sendMessage("Only players can set kingdom's home location");
            return 0;
        }

        UUID executorPlayerUuid = executorPlayer.getUniqueId();
        Location location = executorPlayer.getLocation();
        CompletableFuture<Void> future = memberService.getKingdomMember(executorPlayerUuid)
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember kingdomMember = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                    if (!kingdomMember.role()
                            .canSetHome()) throw new MissingKingdomPermissionException(SET_HOME);
                    return kingdomService.setHome(kingdomMember.kingdomId(), new KingdomHome(location));
                });
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("set home for your kingdom successfully"))
                .on(MissingKingdomMembershipException.class, "You need to be leader or duke of a kingdom to set home for the kingdom")
                .on(MissingKingdomPermissionException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .run();

        return Command.SINGLE_SUCCESS;
    }
}
