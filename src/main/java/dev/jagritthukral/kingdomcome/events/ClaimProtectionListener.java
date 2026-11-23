package dev.jagritthukral.kingdomcome.events;

import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.ClaimService;
import dev.jagritthukral.kingdomcome.service.KingdomService;
import dev.jagritthukral.kingdomcome.service.MemberService;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.UUID;

public class ClaimProtectionListener implements Listener {
    private final ClaimService claimService;
    private final MemberService memberService;
    private final KingdomService kingdomService;

    public ClaimProtectionListener(ClaimService claimService, MemberService memberService, KingdomService kingdomService) {
        this.claimService = claimService;
        this.memberService = memberService;
        this.kingdomService = kingdomService;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // get chunk and worldUuid where the block was broken
        Chunk chunk = block.getChunk();
        UUID worldUuid = block.getWorld()
                .getUID();

        Player player = event.getPlayer();
        KingdomMember cachedMember = memberService.getMemberFromCache(player.getUniqueId());
        // if player is not cache they are probably (hopefully) not part of any kingdom, and it isn't a cache miss

        Claim cachedChunkClaim = claimService.getClaimFromCache(worldUuid, chunk.getChunkKey());
        // if chunk doesn't exist in cache it probably (hopefully) wilderness. so don't stop the action
        if (cachedChunkClaim == null) return;

        // a player who isn't part of any kingdom, can't break block blocks of this kingdom
        if (cachedMember == null) {
            player.sendMessage("This chunk belongs to");
        }


    }
}
