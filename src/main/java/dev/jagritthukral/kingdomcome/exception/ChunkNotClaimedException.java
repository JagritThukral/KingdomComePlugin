package dev.jagritthukral.kingdomcome.exception;

public class ChunkNotClaimedException extends KingdomException {
    private static final String message = "This chunk has not been claimed yet.";

    public ChunkNotClaimedException(Throwable cause) {
        super(message, cause);
    }

    public ChunkNotClaimedException() {
        super(message);
    }
}
