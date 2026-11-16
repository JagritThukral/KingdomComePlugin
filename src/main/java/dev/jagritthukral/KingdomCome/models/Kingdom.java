package dev.jagritthukral.KingdomCome.models;

import java.time.LocalDateTime;
import java.util.Optional;


public record Kingdom(Integer kingdomId, String leaderUuid, String name, LocalDateTime createdAt,
                      Optional<KingdomHome> home) {
}
