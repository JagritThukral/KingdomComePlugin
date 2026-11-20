package dev.jagritthukral.kingdomcome.models;

import java.util.Optional;

public record Kingdom(Integer kingdomId, String leaderUuid, String name, long createdAt, Optional<KingdomHome> home) {
}
