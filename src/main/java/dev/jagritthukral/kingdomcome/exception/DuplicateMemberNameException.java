package dev.jagritthukral.kingdomcome.exception;

import dev.jagritthukral.kingdomcome.models.KingdomMember;

import java.util.List;

public class DuplicateMemberNameException extends KingdomException {
    private final List<KingdomMember> duplicateMembers;

    public DuplicateMemberNameException(List<KingdomMember> duplicateMembers) {
        super("Found multiple member with same name");
        this.duplicateMembers = duplicateMembers;
    }

    public List<KingdomMember> getDuplicateMembers() {
        return duplicateMembers;
    }
}
