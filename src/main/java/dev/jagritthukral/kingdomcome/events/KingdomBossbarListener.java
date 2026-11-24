package dev.jagritthukral.kingdomcome.events;

import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.Kingdom; // Adjust import to match your model
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.ClaimService;
import dev.jagritthukral.kingdomcome.service.KingdomService;
import dev.jagritthukral.kingdomcome.service.MemberService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KingdomBossbarListener implements Listener, MembershipChangeListener {
    private final ClaimService claimService;
    private final KingdomService kingdomService;
    private final MemberService memberService;
    // Store each player's personal BossBar and their last known kingdom ID to avoid redundant updates
    private final ConcurrentHashMap<UUID, BossBar> playerBars = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastKingdom = new ConcurrentHashMap<>();

    public KingdomBossbarListener(ClaimService claimService, KingdomService kingdomService, MemberService memberService) {
        this.claimService = claimService;
        this.kingdomService = kingdomService;
        this.memberService = memberService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4) &&
                (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }

        updatePlayerLocation(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        updatePlayerLocation(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayerLocation(event.getPlayer(), event.getPlayer()
                .getLocation());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        updatePlayerLocation(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeBar(event.getPlayer());
        lastKingdom.remove(event.getPlayer()
                .getUniqueId());
    }

    private void updatePlayerLocation(Player player, Location to) {
        UUID worldUuid = to.getWorld()
                .getUID();
        long chunkKey = Chunk.getChunkKey(to.getBlockX() >> 4, to.getBlockZ() >> 4);
        Claim claim = claimService.getClaimFromCache(worldUuid, chunkKey);

        // Unclaimed wilderness -> remove bar if present
        if (claim == null) {
            if (lastKingdom.remove(player.getUniqueId()) != null) {
                removeBar(player);
            }
            return;
        }

        // Skip UI update if player moved into another chunk belonging to the SAME kingdom
        Long kingdomId = claim.kingdomId();
        if (kingdomId.equals(lastKingdom.get(player.getUniqueId()))) {
            return;
        }
        lastKingdom.put(player.getUniqueId(), kingdomId);

        // Fetch kingdom details
        Kingdom kingdom = kingdomService.getKingdomFromCache(kingdomId);
        if (kingdom == null) {
            lastKingdom.remove(player.getUniqueId());
            removeBar(player);
            return;
        }
        // determine diplomacy color
        BossBar.Color barColor = resolveBarColor(player, claim);
        updateKingdomBar(player, kingdom.name(), barColor);
    }

    private BossBar.Color resolveBarColor(Player player, Claim claim) {
        KingdomMember cachedMember = memberService.getMemberFromCache(player.getUniqueId());
        if (cachedMember == null || claim == null) return BossBar.Color.YELLOW; // fallback color
        // if player is in their kingdom 's claim
        if (cachedMember.kingdomId() == claim.kingdomId()) {
            return BossBar.Color.GREEN;
        }
        // if player is in another kingdom's claim
        // todo: red when kingdoms are in war
        return BossBar.Color.BLUE;
    }

    private void updateKingdomBar(Player player, String kingdomName, BossBar.Color color) {
        Component title = Component.text(kingdomName, NamedTextColor.GOLD);

        BossBar bar = playerBars.computeIfAbsent(player.getUniqueId(), _ -> {
            BossBar newBar = BossBar.bossBar(title, 1.0f, color, BossBar.Overlay.PROGRESS);
            player.showBossBar(newBar);
            return newBar;
        });

        bar.name(title);
        bar.color(color);
    }

    private void removeBar(Player player) {
        BossBar bar = playerBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    @Override
    public void onMembershipChanged(UUID playerUuid) {
        lastKingdom.remove(playerUuid); // Force a fresh color evaluation
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            playerBars.remove(playerUuid);
            return;
        }
        updatePlayerLocation(player, player.getLocation());

    }
}
