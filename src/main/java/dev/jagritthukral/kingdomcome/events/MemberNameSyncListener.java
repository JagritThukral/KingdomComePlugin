package dev.jagritthukral.kingdomcome.events;

import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.MemberService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;
import java.util.UUID;

public class MemberNameSyncListener implements Listener {
    private final MemberService memberService;

    public MemberNameSyncListener(MemberService memberService) {
        this.memberService = memberService;
    }

    @EventHandler
    public void playerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String newName = player.getName();
        KingdomMember cachedMember = memberService.getMemberFromCache(playerUuid);
        // if player isn't in a kingdom (not in member cache) skip
        if (cachedMember == null) return;
        // if playerName is not same as cached lasKnownName, update it
        if (!Objects.equals(newName, cachedMember.lastKnownName())) {
            memberService.updateMemberName(playerUuid, newName);
        }
    }
}
