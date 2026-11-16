package dev.jagritthukral.KingdomCome.exception;

public class MissingKingdomMembershipException extends KingdomException {
    private static String message = "kingdom membership needed to execute this action.";

    public MissingKingdomMembershipException() {
        super(message);
    }

    public MissingKingdomMembershipException(Throwable cause) {
        super(message, cause);
    }
    public MissingKingdomMembershipException(String message){
        super(message);
    }
    public MissingKingdomMembershipException(String message, Throwable cause){
        super(message, cause);
    }
}
