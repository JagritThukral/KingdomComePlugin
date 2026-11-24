package dev.jagritthukral.kingdomcome.service;

import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.events.MembershipChangeListener;
import dev.jagritthukral.kingdomcome.exception.CantKickYourselfException;
import dev.jagritthukral.kingdomcome.exception.DuplicateMemberNameException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.models.KingdomPlayerRole;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.*;

public class MemberService {
    private final ConcurrentHashMap<UUID, KingdomMember> membersCache;
    private final JavaPlugin plugin;
    private final MemberRepository memberRepository;
    private final List<MembershipChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    public MemberService(JavaPlugin plugin, ArrayList<KingdomMember> membersList, MemberRepository memberRepository) {
        this.plugin = plugin;
        this.membersCache = new ConcurrentHashMap<>();
        this.memberRepository = memberRepository;
        for (KingdomMember member : membersList) {
            membersCache.put(member.playerUUID(), member);
        }
    }

    public void addChangeListener(MembershipChangeListener listener) {
        changeListeners.add(listener);
    }

    private void notifyMembershipChanged(UUID playerUuid) {
        if (changeListeners.isEmpty()) return;

        // Ensure Bukkit API calls inside listeners (BossBar, Player#getLocation) run on the main thread
        Bukkit.getScheduler()
                .runTask(plugin, () -> {
                    for (MembershipChangeListener listener : changeListeners) {
                        listener.onMembershipChanged(playerUuid);
                    }
                });
    }

    public KingdomMember getMemberFromCache(UUID playerUuid) {
        return membersCache.get(playerUuid);
    }

    public List<KingdomMember> getAllKingdomMembersFromCache(long kingdomId) {
        return membersCache.values()
                .stream()
                .filter(member -> member.kingdomId() == kingdomId)
                .toList();
    }

    void addMemberToCache(UUID playerUuid, KingdomMember member) {
        membersCache.put(playerUuid, member);
        notifyMembershipChanged(playerUuid);
    }

    void removeMemberFromCache(UUID playerUuid) {
        membersCache.remove(playerUuid);
    }

    void removeKingdomMembersFromCache(long kingdomId) {
        List<UUID> removedPlayers = new ArrayList<>();
        membersCache.values()
                .removeIf(member -> {
                    if (member.kingdomId() == kingdomId) {
                        removedPlayers.add(member.playerUUID());
                        return true;
                    }
                    return false;
                });
        removedPlayers.forEach(this::notifyMembershipChanged);
    }

    void updateMemberNameInCache(UUID playerUuid, String newName) {
        membersCache.computeIfPresent(playerUuid, ((_, member) -> new KingdomMember(member.kingdomId(), member.playerUUID(), newName, member.role(), member.discordId())));
    }

    public CompletableFuture<Void> updateMemberName(UUID playerUuid, String newName) {
        return memberRepository.updateMemberName(playerUuid, newName)
                .thenApply(_ -> {
                    updateMemberNameInCache(playerUuid, newName);
                    return null;
                });
    }

    public CompletableFuture<Optional<KingdomMember>> getKingdomMember(UUID playerUuid) {
        // fast fetch from cache hit
        KingdomMember cachedMember = getMemberFromCache(playerUuid);
        if (cachedMember != null) {
            return CompletableFuture.completedFuture(Optional.of(cachedMember));
        }

        return memberRepository.getKingdomMember(playerUuid);
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
                                            .filter(m -> m.kingdomId() == executorKingdomMember.kingdomId())
                                            .orElseThrow(() -> new MissingKingdomMembershipException("This player is not a member of your kingdom"));
                                    return executeKick(executorKingdomMember, targetMember);
                                })
                );
    }

    private CompletableFuture<KingdomMember> validateAndGetExecutor(UUID executorPlayerUuid) {
        return memberRepository.getKingdomMember(executorPlayerUuid)
                .thenApply(optionalKingdomMember -> {
                    KingdomMember executor = optionalKingdomMember
                            .orElseThrow(() -> new MissingKingdomMembershipException("You need to be a leader or duke of a kingdom to kick members"));
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
                    notifyMembershipChanged(target.playerUUID());
                    return null;
                });
    }

}
