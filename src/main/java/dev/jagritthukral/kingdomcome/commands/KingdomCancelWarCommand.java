package dev.jagritthukral.kingdomcome.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.kingdomcome.database.DatabaseService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;

public class KingdomCancelWarCommand {
    private final DatabaseService db;
    private final JavaPlugin plugin;

    public KingdomCancelWarCommand(DatabaseService db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("cancelwar")
                .executes(this::cancelWar);
    }

    private int cancelWar(CommandContext<CommandSourceStack> context) {
        // TODO: Add logic here
        return 0;
    }
}