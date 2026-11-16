package dev.jagritthukral.KingdomCome.exception;

public class KingdomHomeNotSetException extends RuntimeException {
    private static final String message = "A home location has not been set for this kingdom";
    public KingdomHomeNotSetException() {
        super(message);
    }
    public KingdomHomeNotSetException(Throwable cause){super(message,cause);}
}
