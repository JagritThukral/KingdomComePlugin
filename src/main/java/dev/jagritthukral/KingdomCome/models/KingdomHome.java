package dev.jagritthukral.KingdomCome.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record KingdomHome(String world_name, double x, double y, double z) {
    // Secondary constructor to create a KingdomHome directly from a Bukkit Location
    public KingdomHome(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and its World cannot be null");
        }
        // calling canonical constructor
        this(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public Location getBukkitLocation() {
        World world = Bukkit.getWorld(world_name);
        if (world == null) {
            throw new IllegalStateException("World: " + world_name + " not loaded");
        }
        return new Location(world, x, y, z);
    }
}
