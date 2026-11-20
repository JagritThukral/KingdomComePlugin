package dev.jagritthukral.kingdomcome.exception;

public class AlreadyInKingdomException extends KingdomException {
    public static final String message = "Player is already in another kingdom.";

    public AlreadyInKingdomException(Throwable cause) {
        super(message, cause);
    }

    public AlreadyInKingdomException() {
        super(message);
    }


}