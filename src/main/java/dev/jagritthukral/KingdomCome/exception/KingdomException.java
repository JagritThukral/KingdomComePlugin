package dev.jagritthukral.KingdomCome.exception;

public abstract class KingdomException extends RuntimeException {
    protected KingdomException(String message) {
        super(message);
    }

    protected KingdomException(String message, Throwable cause) {
        super(message, cause);
    }
}
