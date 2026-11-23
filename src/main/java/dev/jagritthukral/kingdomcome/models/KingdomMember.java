package dev.jagritthukral.kingdomcome.models;

import java.util.UUID;

public record KingdomMember(long kingdomId, UUID playerUUID, String lastKnownName, KingdomPlayerRole role,
                            String discordId) {
}
