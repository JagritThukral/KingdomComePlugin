package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.KICK_MEMBER;

public class KingdomKickMemberCommand {
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;

    public KingdomKickMemberCommand(JavaPlugin plugin, MemberRepository memberRepository) {
        this.plugin = plugin;
        this.memberRepository = memberRepository;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("kick")
                .then(Commands.argument("PlayerName", StringArgumentType.word())
                        .suggests(((context, builder) -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getExecutor() instanceof Player executorPlayer)) {
                                        return builder.buildFuture();
                                    }
                                    return memberRepository.getKingdomMember(executorPlayer.getUniqueId())
                                            .thenCompose(optionalKingdomMember -> {
                                                // Don't suggest if player doesn't exist in db or if they don't have the permission to add roles
                                                if (optionalKingdomMember.isEmpty() || !optionalKingdomMember.get()
                                                        .role()
                                                        .canAddOrKickMembers()) {
                                                    // wrap in a completed future because we are using thenCompose
                                                    return CompletableFuture.completedFuture(builder.build());
                                                }
                                                KingdomMember member = optionalKingdomMember.get();
                                                return memberRepository.getAllKingdomMembers(member.kingdomId())
                                                        .thenApply(membersList -> {
                                                            if (membersList.isEmpty()) return builder.build();
                                                            for (KingdomMember suggestionMember : membersList) {
                                                                // Don't suggest the executor themselves
                                                                if (!member.playerUUID()
                                                                        .equals(executorPlayer.getUniqueId())) {
                                                                    continue;
                                                                }
                                                                builder.suggest(suggestionMember.lastKnownName(), MessageComponentSerializer.message()
                                                                        .serialize(
                                                                                Component.text(suggestionMember.role()
                                                                                        .name(), NamedTextColor.BLUE)
                                                                        ));
                                                            }
                                                            return builder.build();
                                                        });
                                            })
                                            .exceptionally(_ -> builder.build());
                                })
                        ))
                .executes(context -> kickCitizen(context, StringArgumentType.getString(context, "PlayerName")));

    }

    private int kickCitizen(CommandContext<CommandSourceStack> context, String targetPlayerName) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender()
                    .sendMessage("Only players can kick members from a kingdom");
            return 0;
        }

        if (Objects.equals(targetPlayerName, executorPlayer.name()
                .toString())) {
            executorPlayer.sendMessage("You can't kick yourself");
        }

        CompletableFuture<Void> future = memberRepository.getKingdomMember(executorPlayer.getUniqueId())
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember executorKingdomMember = optionalKingdomMember.orElseThrow(() -> new MissingKingdomMembershipException("You need to member of kingdom to kick members"));
                    if (!executorKingdomMember.role()
                            .canAddOrKickMembers())
                        throw new MissingKingdomPermissionException(KICK_MEMBER);
                    return memberRepository.kickKingdomMember(targetPlayerName, executorKingdomMember.kingdomId());
                });
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("Kicked " + targetPlayerName + " from your kingdom successfully"))
                .on(MissingKingdomMembershipException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .on(MissingKingdomPermissionException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .run();
        return Command.SINGLE_SUCCESS;
    }
}
