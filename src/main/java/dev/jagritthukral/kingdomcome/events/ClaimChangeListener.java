package dev.jagritthukral.kingdomcome.events;

import java.util.UUID;

public interface ClaimChangeListener {
    // Fired when a single chunk is claimed or unclaimed
    void onChunkClaimChanged(UUID worldUuid, int chunkX, int chunkZ);

    // Fired when all claims for a kingdom are deleted (e.g., /k disband)
    void onKingdomClaimsRemoved(long kingdomId);
}
