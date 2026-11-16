package dev.jagritthukral.KingdomCome.exception;

public class DuplicateKingdomException extends KingdomException {
    private final String name;

    private static String buildMessage(String name) {
        return "A kingdom named " + name + " already exists";
    }

    public DuplicateKingdomException(String name, Throwable cause) {
        super(buildMessage(name), cause);
        this.name = name;
    }

    public DuplicateKingdomException(String name) {
        super(buildMessage(name));
        this.name = name;
    }

    public String getName() {
        return name;
    }
}