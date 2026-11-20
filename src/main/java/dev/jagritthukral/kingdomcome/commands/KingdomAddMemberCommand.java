package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.exception.AlreadyInKingdomException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.ADD_MEMBER;

public class KingdomAddMemberCommand {
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;

    public KingdomAddMemberCommand(JavaPlugin plugin, MemberRepository memberRepository) {
        this.plugin = plugin;
        this.memberRepository = memberRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("add")
                .then(Commands.argument("PlayerName", ArgumentTypes.player())
                        .executes(this::invitePlayer)
                );
    }

    private int invitePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender()
                    .sendMessage("Only players can add other players to their kingdom");
            return 0;
        }
        String executorUuid = executorPlayer.getUniqueId()
                .toString();
        PlayerSelectorArgumentResolver resolver = context.getArgument("PlayerName", PlayerSelectorArgumentResolver.class);
        Player targetPlayer = resolver.resolve(context.getSource())
                .getFirst();
        String targetPlayerUuid = targetPlayer.getUniqueId()
                .toString();
        CompletableFuture<Void> future = memberRepository.getKingdomMember(executorUuid)
                .thenCompose(optionalKingdomMember -> {
                            KingdomMember kingdomMember = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                            if (!kingdomMember.role()
                                    .canAddOrKickMembers())
                                throw new MissingKingdomPermissionException(ADD_MEMBER);
                            return memberRepository.addKingdomMember(kingdomMember.kingdomId(), targetPlayerUuid);
                        }
                );
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("Added " + targetPlayer.getName() + " to your kingdom successfully!"))
                .on(MissingKingdomMembershipException.class, e -> executorPlayer.sendMessage("You need to a leader or duke of a kingdom to add members to the kingdom."))
                .on(MissingKingdomPermissionException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .on(AlreadyInKingdomException.class, AlreadyInKingdomException.message)
                .run();
        return Command.SINGLE_SUCCESS;
    }
}
