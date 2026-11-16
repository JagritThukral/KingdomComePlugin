package dev.jagritthukral.KingdomCome.exception;

public class MissingKingdomOwnershipException extends KingdomException {
    public MissingKingdomOwnershipException() {
        super(buildMessage());
    }

    public MissingKingdomOwnershipException(Throwable cause) {
        super(buildMessage(), cause);
    }

    private static String buildMessage() {
        return "You are not the leader of this Kingdom.\nOnly a leader can execute this action";
    }
}
