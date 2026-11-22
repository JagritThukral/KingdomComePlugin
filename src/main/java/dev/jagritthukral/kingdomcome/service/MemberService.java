package dev.jagritthukral.kingdomcome.service;

import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.exception.CantKickYourselfException;
import dev.jagritthukral.kingdomcome.exception.DuplicateMemberNameException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.models.KingdomPlayerRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.*;

public class MemberService {
    private final ConcurrentHashMap<UUID, KingdomMember> membersCache;
    private final MemberRepository memberRepository;

    public MemberService(ArrayList<KingdomMember> membersList, MemberRepository memberRepository) {
        this.membersCache = new ConcurrentHashMap<>();
        this.memberRepository = memberRepository;
        for (KingdomMember member : membersList) {
            membersCache.put(member.playerUUID(), member);
        }
    }

    public KingdomMember getMemberFromCache(UUID playerUuid) {
        return membersCache.get(playerUuid);
    }

    public List<KingdomMember> getAllKingdomMembersFromCache(int kingdomId) {
        return membersCache.values()
                .stream()
                .filter(member -> Objects.equals(member.kingdomId(), kingdomId))
                .toList();
    }

    void addMemberToCache(UUID playerUuid, KingdomMember member) {
        membersCache.put(playerUuid, member);
    }

    void removeMemberFromCache(UUID playerUuid) {
        membersCache.remove(playerUuid);
    }

    void removeKingdomMembersFromCache(int kingdomId) {
        membersCache.values()
                .removeIf(kingdomMember -> Objects.equals(kingdomId, kingdomMember.kingdomId()));
    }

    public CompletableFuture<Void> addKingdomMember(UUID executorPlayerUUid, UUID targetPlayerUuid, String targetPlayerName) {

        return memberRepository.getKingdomMember(executorPlayerUUid)
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember executorKingdomMember = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                    if (!executorKingdomMember.role()
                            .canAddOrKickMembers()) throw new MissingKingdomPermissionException(ADD_MEMBER);
                    return memberRepository.addKingdomMember(executorKingdomMember.kingdomId(), targetPlayerUuid, targetPlayerName)
                            .thenApply(_ -> {
                                // add to cache if db operation succeeded
                                addMemberToCache(targetPlayerUuid, new KingdomMember(executorKingdomMember.kingdomId(), targetPlayerUuid, targetPlayerName, KingdomPlayerRole.MEMBER, null));
                                return null;
                            });
                });
    }

    public CompletableFuture<Void> removeKingdomMember(UUID executorPlayerUuid, String targetPlayerName) {
        return validateAndGetExecutor(executorPlayerUuid)
                .thenCompose(executorKingdomMember ->
                        memberRepository.getKingdomMemberByName(targetPlayerName, executorKingdomMember.kingdomId())
                                .thenCompose(membersList -> {
                                    if (membersList.size() > 1) {
                                        throw new DuplicateMemberNameException(membersList);
                                    }
                                    if (membersList.isEmpty()) {
                                        throw new MissingKingdomMembershipException("This player is not a member of your kingdom");
                                    }
                                    return executeKick(executorKingdomMember, membersList.getFirst());
                                })
                );
    }

    public CompletableFuture<Void> removeKingdomMember(UUID executorPlayerUuid, UUID targetPlayerUuid) {
        return validateAndGetExecutor(executorPlayerUuid)
                .thenCompose(executorKingdomMember ->
                        memberRepository.getKingdomMember(targetPlayerUuid)
                                .thenCompose(optionalTarget -> {
                                    KingdomMember targetMember = optionalTarget
                                            .filter(m -> Objects.equals(m.kingdomId(), executorKingdomMember.kingdomId()))
                                            .orElseThrow(() -> new MissingKingdomMembershipException("This player is not a member of your kingdom"));
                                    return executeKick(executorKingdomMember, targetMember);
                                })
                );
    }

    private CompletableFuture<KingdomMember> validateAndGetExecutor(UUID executorPlayerUuid) {
        return memberRepository.getKingdomMember(executorPlayerUuid)
                .thenApply(optionalKingdomMember -> {
                    KingdomMember executor = optionalKingdomMember
                            .orElseThrow(() -> new MissingKingdomMembershipException("You need to be member of a kingdom to kick members"));
                    if (!executor.role()
                            .canAddOrKickMembers()) {
                        throw new MissingKingdomPermissionException(KICK_MEMBER);
                    }
                    return executor;
                });
    }

    private CompletableFuture<Void> executeKick(KingdomMember executor, KingdomMember target) {
        if (Objects.equals(target.playerUUID(), executor.playerUUID())) {
            throw new CantKickYourselfException();
        }
        if (target.role() == KingdomPlayerRole.LEADER) {
            throw new MissingKingdomPermissionException(CANT_KICK_LEADER);
        }
        if (executor.role() == KingdomPlayerRole.DUKE && target.role() == KingdomPlayerRole.DUKE) {
            throw new MissingKingdomPermissionException(DUKE_CANT_KICK_DUKE);
        }
        return memberRepository.kickKingdomMember(target.playerUUID(), executor.kingdomId())
                .thenApply(_ -> {
                    removeMemberFromCache(target.playerUUID());
                    return null;
                });
    }
}
