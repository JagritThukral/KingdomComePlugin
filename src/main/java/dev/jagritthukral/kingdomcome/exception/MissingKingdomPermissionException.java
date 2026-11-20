package dev.jagritthukral.kingdomcome.exception;

public class MissingKingdomPermissionException extends KingdomException {
    private final MissingPermission missingPermission;

    public MissingKingdomPermissionException(MissingPermission permission) {
        super(permission.getMessage());
        this.missingPermission = permission;
    }

    public MissingPermission getMissingPermission() {
        return missingPermission;
    }

    public enum MissingPermission {
        ADD_MEMBER("You need to be a leader or duke of this kingdom to add members"),
        KICK_MEMBER("You need to be a leader or duke of this kingdom to kick members"),
        CLAIM_CHUNK("You need to be a leader or duke of this kingdom to claim chunks"),
        UNCLAIM_CHUNK("You need to be a leader or duke of this kingdom to unclaim chunks"),
        SET_HOME("You need to be a leader or duke of this kingdom to set home for the kingdom");
        private final String message;

        MissingPermission(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
