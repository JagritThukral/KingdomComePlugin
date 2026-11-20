package dev.jagritthukral.KingdomCome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jagritthukral.KingdomCome.database.repository.MemberRepository;
import dev.jagritthukral.KingdomCome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.KingdomCome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.KingdomCome.models.KingdomMember;
import dev.jagritthukral.KingdomCome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static dev.jagritthukral.KingdomCome.exception.MissingKingdomPermissionException.MissingPermission.KICK_MEMBER;

public class KingdomKickMemberCommand {
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;

    public KingdomKickMemberCommand(JavaPlugin plugin, MemberRepository memberRepository) {
        this.plugin = plugin;
        this.memberRepository = memberRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("kick")
                .then(Commands.argument("PlayerName", ArgumentTypes.player())
                        .executes(this::kickCitizen)
                );
    }

    private int kickCitizen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender().sendMessage("Only players can kick members from a kingdom");
            return 0;
        }
        String executorPlayerUuid = executorPlayer.getUniqueId().toString();
        PlayerSelectorArgumentResolver resolver = context.getArgument("PlayerName", PlayerSelectorArgumentResolver.class);
        Player targetPlayer = resolver.resolve(context.getSource()).getFirst();
        String targetPlayerUuid = targetPlayer.getUniqueId().toString();

        if (Objects.equals(targetPlayerUuid, executorPlayerUuid)){
            executorPlayer.sendMessage("You can't kick yourself");
        }
        CompletableFuture<Void> future = memberRepository.getKingdomMember(executorPlayerUuid)
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember executorKingdomMember = optionalKingdomMember.orElseThrow(() -> new MissingKingdomMembershipException("You need to member of kingdom to kick members"));
                    if (!executorKingdomMember.role().canAddOrKickMembers())
                        throw new MissingKingdomPermissionException(KICK_MEMBER);
                    return memberRepository.kickKingdomMember(targetPlayerUuid, executorKingdomMember.kingdomId());
                });
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("Kicked " + targetPlayer.getName() + " from your kingdom successfully"))
                .on(MissingKingdomMembershipException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .on(MissingKingdomPermissionException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .run();
        return Command.SINGLE_SUCCESS;
    }
}