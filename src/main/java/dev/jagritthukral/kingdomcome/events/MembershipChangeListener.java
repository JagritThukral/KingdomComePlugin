package dev.jagritthukral.kingdomcome.events;

import java.util.UUID;

public interface MembershipChangeListener {
    void onMembershipChanged(UUID playerUuid);
}
