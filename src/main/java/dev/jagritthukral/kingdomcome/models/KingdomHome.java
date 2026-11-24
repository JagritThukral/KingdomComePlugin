package dev.jagritthukral.kingdomcome.models;

import dev.jagritthukral.kingdomcome.exception.WorldNotLoadedException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record KingdomHome(UUID worldUuid, double x, double y, double z) {
    // Secondary constructor to create a KingdomHome directly from a Bukkit Location
    public KingdomHome(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and its World cannot be null");
        }
        // calling canonical constructor
        this(
                location.getWorld()
                        .getUID(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public Location getBukkitLocation() {
        World world = Bukkit.getWorld(worldUuid);
        if (world == null) {
            throw new WorldNotLoadedException("World: " + worldUuid + " not loaded");
        }
        return new Location(world, x, y, z);
    }
}
