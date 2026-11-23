package dev.jagritthukral.kingdomcome.models;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record Kingdom(long kingdomId, UUID leaderUuid, String name, long createdAt, @Nullable KingdomHome home) {
    public Kingdom withHome(@Nullable KingdomHome newHome) {
        return new Kingdom(this.kingdomId, this.leaderUuid, this.name, this.createdAt, newHome);
    }
}
