package dev.jagritthukral.KingdomCome.service;

import dev.jagritthukral.KingdomCome.models.Claim;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimService {
    ConcurrentHashMap<UUID,ConcurrentHashMap<Long,Claim>> worldsClaimsCache;

    public ClaimService() {
        this.worldsClaimsCache =
    }
}
