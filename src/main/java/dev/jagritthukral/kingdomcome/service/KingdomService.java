package dev.jagritthukral.kingdomcome.service;

import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.models.KingdomHome;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.models.KingdomPlayerRole;
import org.jctools.maps.NonBlockingHashMapLong;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KingdomService {
    private final KingdomRepository kingdomRepository;
    private final MemberService memberService;
    private final ClaimService claimService;
    private final NonBlockingHashMapLong<Kingdom> kingdomCache;

    public KingdomService(ArrayList<Kingdom> kingdomsList, KingdomRepository kingdomRepository, MemberService memberService, ClaimService claimService) {
        this.kingdomRepository = kingdomRepository;
        this.memberService = memberService;
        this.claimService = claimService;
        this.kingdomCache = new NonBlockingHashMapLong<>();
        for (Kingdom kingdom : kingdomsList) {
            addKingdomToCache(kingdom.kingdomId(), kingdom);
        }
    }

    void addKingdomToCache(long kingdomId, Kingdom kingdom) {
        kingdomCache.put(kingdomId, kingdom);
    }

    void updateKingdomHome(long kingdomId, KingdomHome home) {
        kingdomCache.computeIfPresent(kingdomId, (_, kingdom) -> kingdom.withHome(home));
    }

    void removeKingdomFromCache(long kingdomId){
        kingdomCache.remove(kingdomId);
    }

    public CompletableFuture<Kingdom> createKingdom(String kingdomName, UUID leaderPlayerUuid, String leaderPlayerName) {
        return kingdomRepository.createKingdomWithLeader(kingdomName, leaderPlayerUuid, leaderPlayerName)
                .thenApply(kingdom -> {
                    addKingdomToCache(kingdom.kingdomId(), kingdom);
                    // add the leader player to members cache so members service doesn't face a cache miss
                    memberService.addMemberToCache(leaderPlayerUuid, new KingdomMember(kingdom.kingdomId(), leaderPlayerUuid, leaderPlayerName, KingdomPlayerRole.LEADER, null));

                    return kingdom;
                });
    }

    public CompletableFuture<Void> disbandKingdom(String kingdomName) {
        return kingdomRepository.disbandKingdom(kingdomName)
                .thenApply(kingdomId -> {
                    memberService.removeKingdomMembersFromCache(kingdomId);
                    claimService.removeKingdomClaimsFromCache(kingdomId);
                    removeKingdomFromCache(kingdomId);
                    return null;
                });
    }

    public CompletableFuture<KingdomHome> getHome(long kingdomId) {
        // fetch from cache if cache hits, tp home can be run at high rate so getting from cache is better
        Kingdom cachedKingdom = kingdomCache.get(kingdomId);
        if (cachedKingdom != null && cachedKingdom.home() != null) {
            return CompletableFuture.completedFuture(cachedKingdom.home());
        }
        return kingdomRepository.getKingdomHome(kingdomId);
    }

    public CompletableFuture<Void> setHome(long kingdomId, KingdomHome home) {
        return kingdomRepository.setKingdomHome(kingdomId, home)
                .thenApply(_ -> {
                    updateKingdomHome(kingdomId, home);
                    return null;
                });
    }
}
