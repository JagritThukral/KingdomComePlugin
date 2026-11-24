package dev.jagritthukral.kingdomcome.events;

import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.ClaimService;
import dev.jagritthukral.kingdomcome.service.MemberService;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.potion.PotionEffectType;

public class PvPListener implements Listener {
    private final MemberService memberService;
    private final ClaimService claimService;

    public PvPListener(MemberService memberService, ClaimService claimService) {
        this.memberService = memberService;
        this.claimService = claimService;
    }

    /**
     * Central rule check: returns true if attacker is allowed to harm victim.
     */
    private boolean canPvP(Player attacker, Player victim) {
        if (attacker.equals(victim)) {
            return true; // Allow self-damage (pearl/bow boosting)
        }

        KingdomMember attackerMember = memberService.getMemberFromCache(attacker.getUniqueId());
        KingdomMember victimMember = memberService.getMemberFromCache(victim.getUniqueId());
        // block friendly fire between kingdom members everywhere
        if (attackerMember != null && victimMember != null && victimMember.kingdomId() == attackerMember.kingdomId()) {
            return false;
        }
        Location victimLoc = victim.getLocation();
        Claim victimClaim = claimService.getClaimFromCache(
                victimLoc.getWorld()
                        .getUID(),
                victimLoc.getBlockX() >> 4,
                victimLoc.getBlockZ() >> 4
        );

        Location attackerLoc = attacker.getLocation();
        Claim attackerClaim = claimService.getClaimFromCache(
                attackerLoc.getWorld()
                        .getUID(),
                attackerLoc.getBlockX() >> 4,
                attackerLoc.getBlockZ() >> 4
        );
        // Allow PvP only if both players are in the wilderness
        if (victimClaim == null && attackerClaim == null) {
            return true;
        }

        // TODO: When wars are implemented, allow PvP if attacker and victim kingdoms are at war

        return false;
    }

    private void handle(Cancellable event, Player attacker, Player victim) {
        if (!canPvP(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    // 1. Direct melee swing (fires BEFORE damage/sounds/knockback)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrePlayerAttack(PrePlayerAttackEntityEvent event) {
        if (event.getAttacked() instanceof Player victim) {
            handle(event, event.getPlayer(), victim);
        }
    }

    // 2. Direct & indirect entity damage (Projectiles, Tridents, Crystals, TNT)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim
                && event.getDamageSource()
                .getCausingEntity() instanceof Player attacker) {
            handle(event, attacker, victim);
        }
    }

    // 3. Environmental / indirect PvP damage (Fire, Poison, Wither caused by another player)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return; // Already handled above
        }

        if (event.getEntity() instanceof Player victim
                && event.getDamageSource()
                .getCausingEntity() instanceof Player attacker) {
            handle(event, attacker, victim);
        }
    }

    // 4. Prevent Flame bows / Fire Aspect from igniting players in claims
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = switch (event.getCombuster()) {
            case Player p -> p;
            case org.bukkit.entity.Projectile proj when proj.getShooter() instanceof Player p -> p;
            default -> null;
        };

        if (attacker != null) {
            handle(event, attacker, victim);
        }
    }


    // 5. Prevent hooking/pulling players out of claims with fishing rods
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY
                && event.getCaught() instanceof Player victim) {
            handle(event, event.getPlayer(), victim);
        }
    }

    // 6. Harmful splash potions (ignores healing/buff potions and protects per-player)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion()
                .getShooter() instanceof Player attacker)) {
            return;
        }

        boolean hasHarmfulEffect = event.getPotion()
                .getEffects()
                .stream()
                .anyMatch(effect -> effect.getType()
                        .getEffectCategory() == PotionEffectType.Category.HARMFUL);
        if (!hasHarmfulEffect) {
            return;
        }

        for (var entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim && !canPvP(attacker, victim)) {
                event.setIntensity(victim, 0.0);
            }
        }
    }

    // 7. Harmful lingering potion clouds
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        if (!(event.getEntity()
                .getSource() instanceof Player attacker)) {
            return;
        }

        boolean hasHarmfulEffect = event.getEntity()
                .getCustomEffects()
                .stream()
                .anyMatch(effect -> effect.getType()
                        .getEffectCategory() == PotionEffectType.Category.HARMFUL)
                || (event.getEntity()
                .getBasePotionType() != null
                && event.getEntity()
                .getBasePotionType()
                .getPotionEffects()
                .stream()
                .anyMatch(effect -> effect.getType()
                        .getEffectCategory() == PotionEffectType.Category.HARMFUL));

        if (!hasHarmfulEffect) {
            return;
        }

        event.getAffectedEntities()
                .removeIf(
                        entity -> entity instanceof Player victim && !canPvP(attacker, victim)
                );
    }
}
