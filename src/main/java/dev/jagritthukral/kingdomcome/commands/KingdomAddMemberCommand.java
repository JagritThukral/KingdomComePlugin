package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jagritthukral.kingdomcome.exception.AlreadyInKingdomException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.service.MemberService;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KingdomAddMemberCommand {
    private final JavaPlugin plugin;
    private final MemberService memberService;

    public KingdomAddMemberCommand(JavaPlugin plugin, MemberService memberService) {
        this.plugin = plugin;
        this.memberService = memberService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("add")
                .then(Commands.argument("PlayerName", ArgumentTypes.player())
                        .executes(this::addPlayer));
    }

    private int addPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender()
                    .sendMessage("Only players can add other players to their kingdom");
            return 0;
        }
        UUID executorUuid = executorPlayer.getUniqueId();
        PlayerSelectorArgumentResolver resolver = context.getArgument("PlayerName", PlayerSelectorArgumentResolver.class);
        Player targetPlayer = resolver.resolve(context.getSource())
                .getFirst();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();
        CompletableFuture<Void> future = memberService.addKingdomMember(executorUuid, targetPlayerUuid, targetPlayer.getName());
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("Added " + targetPlayer.getName() + " to your kingdom successfully!"))
                .on(MissingKingdomMembershipException.class, _ -> executorPlayer.sendMessage("You need to be a leader or duke of a kingdom to add members to the kingdom."))
                .on(MissingKingdomPermissionException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .on(AlreadyInKingdomException.class, AlreadyInKingdomException.message)
                .run();
        return Command.SINGLE_SUCCESS;
    }
}
