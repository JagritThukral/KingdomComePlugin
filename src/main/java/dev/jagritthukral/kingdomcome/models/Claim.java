package dev.jagritthukral.kingdomcome.models;

import java.util.UUID;

public record Claim(Integer kingdomId, Integer chunkX, Integer chunkZ, UUID worldUuid) {
}
