package dev.jagritthukral.KingdomCome.models;

import java.util.Optional;

public record Kingdom(Integer kingdomId, String leaderUuid, String name, long createdAt, Optional<KingdomHome> home) {
}
