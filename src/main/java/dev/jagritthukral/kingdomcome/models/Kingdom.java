package dev.jagritthukral.kingdomcome.models;

import java.util.Optional;

public record Kingdom(int kingdomId, String leaderUuid, String name, long createdAt, Optional<KingdomHome> home) {
}
