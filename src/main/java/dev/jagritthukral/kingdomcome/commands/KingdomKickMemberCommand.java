package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.exception.CantKickYourselfException;
import dev.jagritthukral.kingdomcome.exception.DuplicateMemberNameException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.MemberService;
import dev.jagritthukral.kingdomcome.utils.CommandAsyncUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class KingdomKickMemberCommand {
    private final JavaPlugin plugin;
    private final MemberService memberService;

    public KingdomKickMemberCommand(JavaPlugin plugin, MemberService memberService) {
        this.plugin = plugin;
        this.memberService = memberService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("kick")
                .then(Commands.argument("PlayerName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getExecutor() instanceof Player executorPlayer)) {
                                return builder.buildFuture();
                            }

                            UUID executorPlayerUuid = executorPlayer.getUniqueId();
                            KingdomMember executorPlayerMember = memberService.getMemberFromCache(executorPlayerUuid);

                            if (executorPlayerMember == null) return builder.buildFuture();

                            List<KingdomMember> kingdomMembers = memberService.getAllKingdomMembersFromCache(executorPlayerMember.kingdomId());
                            for (KingdomMember member : kingdomMembers) {
                                // don't suggest kicking themselves
                                if (Objects.equals(member.playerUUID(), executorPlayerUuid)) continue;
                                builder.suggest(member.lastKnownName(), MessageComponentSerializer.message()
                                        .serialize(Component.text(member.role()
                                                .name(), NamedTextColor.BLUE)));
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> kickCitizen(
                                context,
                                StringArgumentType.getString(context, "PlayerName"),
                                null
                        ))
                        .then(Commands.argument("UUID", StringArgumentType.word())
                                .executes(context -> kickCitizen(context, StringArgumentType.getString(context, "PlayerName"), StringArgumentType.getString(context, "UUID")))
                        )
                );
    }

    private int kickCitizen(CommandContext<CommandSourceStack> context, String targetPlayerName, String targetPlayerUuidString) {
        CommandSourceStack source = context.getSource();

        if (!(source.getExecutor() instanceof Player executorPlayer)) {
            source.getSender()
                    .sendMessage("Only players can kick members from a kingdom");
            return 0;
        }

        CompletableFuture<Void> future;
        // If UUID is provided, prioritize UUID lookup
        if (targetPlayerUuidString != null) {
            UUID targetUuid;
            try {
                targetUuid = UUID.fromString(targetPlayerUuidString);
            } catch (IllegalArgumentException e) {
                executorPlayer.sendMessage("Invalid UUID format provided.");
                return 0;
            }
            future = memberService.removeKingdomMember(executorPlayer.getUniqueId(), targetUuid);
        } else {
            future = memberService.removeKingdomMember(executorPlayer.getUniqueId(), targetPlayerName);
        }
        CommandAsyncUtils.handle(plugin, executorPlayer, future)
                .onSuccess(_ -> executorPlayer.sendMessage("Kicked " + targetPlayerName + " from your kingdom successfully"))
                .on(CantKickYourselfException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .on(MissingKingdomMembershipException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .on(MissingKingdomPermissionException.class, e -> executorPlayer.sendMessage(e.getMessage()))
                .on(DuplicateMemberNameException.class, e -> {
                    List<KingdomMember> duplicateMembers = e.getDuplicateMembers();
                    String memberList = duplicateMembers.stream()
                            .map(member -> "- " + member.lastKnownName() + " (" + member.playerUUID() + ") " + member.role()
                                    .name())
                            .collect(Collectors.joining("\n"));
                    executorPlayer.sendMessage(duplicateMembers.size() + " Members found with same name."
                            + "\nPlease rerun command with uuid (e.g. /k kick name uuid)"
                            + "\nName, uuid and role of the members with same name:\n"
                            + memberList);
                })
                .run();
        return Command.SINGLE_SUCCESS;
    }
}
