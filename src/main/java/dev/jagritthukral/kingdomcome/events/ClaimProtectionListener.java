package dev.jagritthukral.kingdomcome.events;

import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.ClaimService;
import dev.jagritthukral.kingdomcome.service.KingdomService;
import dev.jagritthukral.kingdomcome.service.MemberService;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class ClaimProtectionListener implements Listener {
    private final ClaimService claimService;
    private final MemberService memberService;
    private final KingdomService kingdomService;
    private final JavaPlugin plugin;

    public ClaimProtectionListener(JavaPlugin plugin, ClaimService claimService, MemberService memberService, KingdomService kingdomService) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.memberService = memberService;
        this.kingdomService = kingdomService;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        handleBlockModification(event, event.getPlayer(), event.getBlock(), "break");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        handleBlockModification(event, event.getPlayer(), event.getBlock(), "place");
    }

    private void handleBlockModification(Cancellable event, Player player, Block block, String action) {
        // If player is a server operator, let them modify blocks
        if (player.isOp()) return;

        Chunk chunk = block.getChunk();
        UUID worldUuid = block.getWorld()
                .getUID();

        Claim cachedChunkClaim = claimService.getClaimFromCache(worldUuid, chunk.getX(), chunk.getZ());

        // If chunk doesn't exist in cache, it's wilderness; allow the action
        if (cachedChunkClaim == null) return;

        KingdomMember cachedMember = memberService.getMemberFromCache(player.getUniqueId());

        // Check if the player belongs to the kingdom that owns the claim
        if (cachedMember != null && cachedMember.kingdomId() == cachedChunkClaim.kingdomId()) {
            return; // Allowed
        }

        // Otherwise, deny the action and notify the player
        Kingdom claimKingdom = kingdomService.getKingdomFromCache(cachedChunkClaim.kingdomId());
        String claimKingdomName = claimKingdom != null ? claimKingdom.name() : "a kingdom";

        if (claimKingdom == null) {
            plugin.getSLF4JLogger()
                    .warn(
                            "Kingdom {} was not found in cache, either this chunk shouldn't be in the cache or there's an issue with kingdom cache",
                            cachedChunkClaim.kingdomId()
                    );
        }

        player.sendMessage("This chunk is claimed by " + claimKingdomName + ". You cannot " + action + " blocks in this claim if you are not part of this kingdom.");
        event.setCancelled(true);
    }
}
