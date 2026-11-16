package dev.jagritthukral.KingdomCome.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.jagritthukral.KingdomCome.database.DatabaseService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;

public class KingdomDeclareWarCommand {
    private final DatabaseService db;
    private final JavaPlugin plugin;

    public KingdomDeclareWarCommand(DatabaseService db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("declarewar")
                .then(Commands.argument("KingdomName", StringArgumentType.string()).executes(this::declareWar)
                );
    }

    private int declareWar(CommandContext<CommandSourceStack> context) {
        // TODO: Add logic here
        return 0;
    }
}