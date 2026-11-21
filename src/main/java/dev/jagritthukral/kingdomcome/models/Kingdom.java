package dev.jagritthukral.kingdomcome.models;

import java.util.Optional;
import java.util.UUID;

public record Kingdom(int kingdomId, UUID leaderUuid, String name, long createdAt, Optional<KingdomHome> home) {
}
