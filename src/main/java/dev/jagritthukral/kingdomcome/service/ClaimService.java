package dev.jagritthukral.kingdomcome.service;

import dev.jagritthukral.kingdomcome.database.repository.ClaimRepository;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.events.ClaimChangeListener;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.ClaimStatus;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jctools.maps.NonBlockingHashMapLong;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.CLAIM_CHUNK;
import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.UNCLAIM_CHUNK;

public class ClaimService {
    // use jctools 's nonblocking-hashmap-long so lookups don't allocate a Long per call (except 127 to -128 which is cached by Long.valueOf())  bcs long is autoboxed to Long by compiler (concurrentHashMap only accept objects) using Long.valueOf()
    private final JavaPlugin plugin;
    private final List<ClaimChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<UUID, NonBlockingHashMapLong<Claim>> worldsClaimsCache;
    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;

    public ClaimService(JavaPlugin plugin, List<UUID> worldsUuidList, ArrayList<Claim> claimsList, MemberRepository memberRepository, ClaimRepository claimRepository) {
        this.plugin = plugin;
        this.worldsClaimsCache = new ConcurrentHashMap<>();
        this.memberRepository = memberRepository;
        this.claimRepository = claimRepository;

        for (UUID worldUuid : worldsUuidList) {
            worldsClaimsCache.put(worldUuid, new NonBlockingHashMapLong<>());
        }
        for (Claim claim : claimsList) {
            addClaimIntoCache(claim);
        }
    }

    public void addChangeListener(ClaimChangeListener listener) {
        changeListeners.add(listener);
    }

    // convert x to long, convert z into long and shift by 32 bit.
    // sign extension - when we convert an int into long the 32 bit msb aka sign bit is copied to rest of the new 32 bits, to preserve the value
    // prevent sign extension ruining the value by using bit masking
    // value is ruined because now we have 64bit long with leftmost 32 bits written
    // when we wanted to write other int
    // so using 0xFFFFFFFFL the first(leftmost) 32 bits 0, rest(rightmost) are 1.
    // so when we use & 0xFFFFFFFFL with an int, we can remove sign extension bcs 0 & 1 or 0 = 0
    // F - 15 in hexadecimal, 0xFFFFFFFF is 4,294,967,295 (2³² - 1)
    // or 32 bit with each bit 1, L at the end convert it into
    // a long (64bit) preceding 32 0 bit and 32 1 bit
    // use OR (|) to add them
    private long getChunkKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    public Claim getClaimFromCache(UUID worldUuid, long chunkKey) {
        NonBlockingHashMapLong<Claim> worldMap = worldsClaimsCache.get(worldUuid);
        return worldMap != null ? worldMap.get(chunkKey) : null;
    }

    public Claim getClaimFromCache(UUID worldUuid, int chunkX, int chunkZ) {
        return getClaimFromCache(worldUuid, getChunkKey(chunkX, chunkZ));
    }


    private void notifyChunkChanged(UUID worldUuid, int chunkX, int chunkZ) {
        if (changeListeners.isEmpty()) return;
        Bukkit.getGlobalRegionScheduler()
                .run(plugin, _ -> {
                    for (ClaimChangeListener listener : changeListeners) {
                        listener.onChunkClaimChanged(worldUuid, chunkX, chunkZ);
                    }
                });
    }

    private void notifyKingdomClaimsRemoved(long kingdomId) {
        if (changeListeners.isEmpty()) return;
        Bukkit.getGlobalRegionScheduler()
                .run(plugin, (_) -> {
                    for (ClaimChangeListener listener : changeListeners) {
                        listener.onKingdomClaimsRemoved(kingdomId);
                    }
                });
    }

    public void addClaimIntoCache(Claim claim) {
        worldsClaimsCache
                .computeIfAbsent(claim.worldUuid(), _ -> new NonBlockingHashMapLong<>())
                .put(getChunkKey(claim.chunkX(), claim.chunkZ()), claim);
    }

    void removeClaimFromCache(UUID worldUuid, int chunkX, int chunkZ) {
        if (worldUuid == null) return;
        NonBlockingHashMapLong<Claim> worldMap = worldsClaimsCache.get(worldUuid);
        if (worldMap != null) {
            worldMap.remove(getChunkKey(chunkX, chunkZ));
        }
    }

    void removeClaimFromCache(Claim claim) {
        if (claim != null) {
            removeClaimFromCache(claim.worldUuid(), claim.chunkX(), claim.chunkZ());
        }
    }

    void removeKingdomClaimsFromCache(long kingdom_id) {
        worldsClaimsCache.values()
                .forEach(worldMap -> worldMap.values()
                        .removeIf(claim -> claim.kingdomId() == kingdom_id));
        notifyKingdomClaimsRemoved(kingdom_id);
    }

    public CompletableFuture<ClaimStatus> claimChunk(UUID playerUuid, int chunkX, int chunkZ, UUID worldUuid) {
        // optionally I can add fast check from claimsCache but member cache will also be needed for it

        return memberRepository.getKingdomMember(playerUuid)
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember member = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                    if (!member.role()
                            .canClaimOrUnclaimChunks())
                        throw new MissingKingdomPermissionException(CLAIM_CHUNK);
                    return claimRepository.claimChunk(member.kingdomId(), chunkX, chunkZ, worldUuid)
                            .thenApply((status) -> {
                                // only add to cache if db succeeded
                                if (status == ClaimStatus.CLAIMED) {
                                    addClaimIntoCache(new Claim(member.kingdomId(), chunkX, chunkZ, worldUuid));
                                    notifyChunkChanged(worldUuid, chunkX, chunkZ);
                                }
                                return status;
                            });
                });
    }

    public CompletableFuture<ClaimStatus> unclaimChunk(UUID playerUuid, int chunkX, int chunkZ, UUID worldUuid) {

        return memberRepository.getKingdomMember(playerUuid)
                .thenCompose(optionalClaim -> {
                    KingdomMember member = optionalClaim.orElseThrow(MissingKingdomMembershipException::new);
                    if (!member.role()
                            .canClaimOrUnclaimChunks())
                        throw new MissingKingdomPermissionException(UNCLAIM_CHUNK);
                    return claimRepository.unclaimChunk(member.kingdomId(), chunkX, chunkZ, worldUuid)
                            .thenApply(status -> {
                                if (status == ClaimStatus.UNCLAIMED) {
                                    removeClaimFromCache(worldUuid, chunkX, chunkZ);
                                    notifyChunkChanged(worldUuid, chunkX, chunkZ);
                                }
                                return status;
                            });
                });
    }
}
