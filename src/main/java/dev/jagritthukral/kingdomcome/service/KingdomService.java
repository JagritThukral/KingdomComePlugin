package dev.jagritthukral.kingdomcome.service;

import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.models.KingdomHome;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.models.KingdomPlayerRole;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KingdomService {
    private final KingdomRepository kingdomRepository;
    private final MemberService memberService;
    private final ClaimService claimService;

    public KingdomService(KingdomRepository kingdomRepository, MemberService memberService, ClaimService claimService) {
        this.kingdomRepository = kingdomRepository;
        this.memberService = memberService;
        this.claimService = claimService;
    }

    public CompletableFuture<Kingdom> createKingdom(String kingdomName, UUID leaderPlayerUuid, String leaderPlayerName) {
        return kingdomRepository.createKingdomWithLeader(kingdomName, leaderPlayerUuid, leaderPlayerName)
                .thenApply(kingdom -> {
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
                    return null;
                });
    }

    public CompletableFuture<KingdomHome> getHome(int kingdomId) {
        return kingdomRepository.getKingdomHome(kingdomId);
    }

    public CompletableFuture<Void> setHome(int kingdomId, KingdomHome home) {
        return kingdomRepository.setKingdomHome(kingdomId, home);
    }
}
