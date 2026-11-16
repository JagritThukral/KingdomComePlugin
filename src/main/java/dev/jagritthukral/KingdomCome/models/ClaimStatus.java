package dev.jagritthukral.KingdomCome.models;

public enum ClaimStatus {
    UNCLAIMED("This chunk has not been claimed yet."),
    CLAIMED("Your kingdom has successfully claimed this chunk!"),
    ALREADY_OWNED_BY_YOU("Your kingdom already owns this chunk."),
    CLAIMED_BY_OTHER("This chunk is claimed by another kingdom!");

    private final String message;

    ClaimStatus(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}