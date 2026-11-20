package dev.jagritthukral.kingdomcome.exception;

public class KingdomNotFoundException extends KingdomException {
    private static String buildMessage(String name) {
        return "A Kingdom named " + name + " doesn't exist";
    }

    public KingdomNotFoundException() {
        super("Couldn't find that kingdom");
    }

    public KingdomNotFoundException(String name, Throwable cause) {
        super(buildMessage(name), cause);
    }

    public KingdomNotFoundException(String name) {
        super(buildMessage(name));
    }
}