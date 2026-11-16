package dev.jagritthukral.KingdomCome.models;


public enum KingdomPlayerRole {
    LEADER,
    DUKE,
    MEMBER;

    public boolean canClaimOrUnclaimChunks() {
        return this == LEADER || this == DUKE;
    }

    public boolean canAddOrKickMembers() {
        return this == LEADER || this == DUKE;
    }

    public boolean canSetHome() {
        return this == LEADER || this == DUKE;
    }
}
