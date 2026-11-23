package dev.jagritthukral.kingdomcome.models;

import java.util.UUID;

public record Claim(long kingdomId, int chunkX, int chunkZ, UUID worldUuid) {
}
