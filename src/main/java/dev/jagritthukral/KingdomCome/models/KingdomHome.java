package dev.jagritthukral.KingdomCome.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record KingdomHome(UUID world_uuid, double x, double y, double z) {
    // Secondary constructor to create a KingdomHome directly from a Bukkit Location
    public KingdomHome(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and its World cannot be null");
        }
        // calling canonical constructor
        this(
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public Location getBukkitLocation() {
        World world = Bukkit.getWorld(world_uuid);
        if (world == null) {
            throw new IllegalStateException("World: " + world_uuid + " not loaded");
        }
        return new Location(world, x, y, z);
    }
}
