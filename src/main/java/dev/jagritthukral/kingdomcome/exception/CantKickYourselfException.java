package dev.jagritthukral.kingdomcome.exception;

public class CantKickYourselfException extends KingdomException {
    public CantKickYourselfException() {
        super("You can't kick yourself");
    }
}
