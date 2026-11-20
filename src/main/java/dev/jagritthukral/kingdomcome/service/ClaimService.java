package dev.jagritthukral.kingdomcome.service;

import dev.jagritthukral.kingdomcome.database.repository.ClaimRepository;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException;
import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.ClaimStatus;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import org.jctools.maps.NonBlockingHashMapLong;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.CLAIM_CHUNK;
import static dev.jagritthukral.kingdomcome.exception.MissingKingdomPermissionException.MissingPermission.UNCLAIM_CHUNK;

public class ClaimService {
    // use jctools 's nonblocking-hashmap-long so lookups don't allocate a Long per call (except 127 to -128 which is cached by Long.valueOf())  bcs long is autoboxed to Long by compiler (concurrentHashMap only accept objects) using Long.valueOf()
    private final ConcurrentHashMap<UUID, NonBlockingHashMapLong<Claim>> worldsClaimsCache;
    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;

    public ClaimService(List<String> worldsUuidList, ArrayList<Claim> claimsList, MemberRepository memberRepository, ClaimRepository claimRepository) {
        this.worldsClaimsCache = new ConcurrentHashMap<>();
        this.memberRepository = memberRepository;
        this.claimRepository = claimRepository;

        for (String worldUuidString : worldsUuidList) {
            worldsClaimsCache.put(UUID.fromString(worldUuidString), new NonBlockingHashMapLong<>());
        }
        for (Claim claim : claimsList) {
            addClaimIntoCache(claim);
        }
    }

    // convert x to long and shift by 32 bit, convert z into long and prevent sign extension ruining the value by using bit masking
    // F - 15 in hexadecimal, 0xFFFFFFFF is 4,294,967,295 (2³² - 1) or 32 bit with each bit 1, L at the end convert it into a long (64bit) preceding 32 0 bit and 32 1 bit
    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);

    }

    public Claim getClaimFromCache(UUID worldUuid, int chunkX, int chunkZ) {
        NonBlockingHashMapLong<Claim> worldMap = worldsClaimsCache.get(worldUuid);
        return worldMap != null ? worldMap.get(getChunkKey(chunkX, chunkZ)) : null;
    }

    public void addClaimIntoCache(Claim claim) {
        worldsClaimsCache
                .computeIfAbsent(claim.worldUuid(), _ -> new NonBlockingHashMapLong<>())
                .put(getChunkKey(claim.chunkX(), claim.chunkZ()), claim);
    }

    public void removeClaimFromCache(Claim claim) {
        worldsClaimsCache
                .computeIfAbsent(claim.worldUuid(), _ -> new NonBlockingHashMapLong<>())
                .remove(getChunkKey(claim.chunkX(), claim.chunkZ()));
    }

    public CompletableFuture<ClaimStatus> claimChunk(UUID playerUuid, int chunkX, int chunkZ, UUID worldUuid) {
        // optionally I can add fast check from claimsCache but member cache will also be needed for it

        return memberRepository.getKingdomMember(playerUuid.toString())
                .thenCompose(optionalKingdomMember -> {
                    KingdomMember member = optionalKingdomMember.orElseThrow(MissingKingdomMembershipException::new);
                    if (!member.role().canClaimOrUnclaimChunks())
                        throw new MissingKingdomPermissionException(CLAIM_CHUNK);
                    return claimRepository.claimChunk(member.kingdomId(), chunkX, chunkZ, worldUuid.toString())
                            .thenApply((status) -> {
                                // only add to cache if db succeeded
                                if (status == ClaimStatus.CLAIMED) {
                                    addClaimIntoCache(new Claim(member.kingdomId(), chunkX, chunkZ, worldUuid));
                                }
                                return status;
                            });
                });
    }

    public CompletableFuture<ClaimStatus> unclaimChunk(UUID playerUuid, int chunkX, int chunkZ, UUID worldUuid) {

        return memberRepository.getKingdomMember(playerUuid.toString())
                .thenCompose(optionalClaim -> {
                    KingdomMember member = optionalClaim.orElseThrow(MissingKingdomMembershipException::new);
                    if (!member.role().canClaimOrUnclaimChunks())
                        throw new MissingKingdomPermissionException(UNCLAIM_CHUNK);
                    return claimRepository.unclaimChunk(member.kingdomId(), chunkX, chunkZ, worldUuid.toString())
                            .thenApply(status -> {
                                if (status == ClaimStatus.UNCLAIMED) {
                                    removeClaimFromCache(new Claim(member.kingdomId(), chunkX, chunkZ, worldUuid));
                                }
                                return status;
                            });
                });
    }
}
