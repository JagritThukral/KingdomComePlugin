package dev.jagritthukral.kingdomcome.exception;

public class MissingAddMemberPermissionException extends KingdomException {
    private final static String message = "You need to be be a leader or duke to add or kick players from your kingdom";

    public MissingAddMemberPermissionException() {
        super(message);
    }

    public MissingAddMemberPermissionException(Throwable cause) {
        super(message, cause);
    }
}
