package dev.jagritthukral.KingdomCome;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.jagritthukral.KingdomCome.commands.*;
import dev.jagritthukral.KingdomCome.database.DatabaseService;
import dev.jagritthukral.KingdomCome.database.repository.ClaimRepository;
import dev.jagritthukral.KingdomCome.database.repository.KingdomRepository;
import dev.jagritthukral.KingdomCome.database.repository.MemberRepository;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class KingdomCome extends JavaPlugin implements Listener {

    private DatabaseService db;

    @Override
    public void onEnable() {
        // Initialize database
        this.db = new DatabaseService(this);

        // Initialize Database Repositories
        KingdomRepository kingdomRepository = new KingdomRepository(db);
        MemberRepository memberRepository = new MemberRepository(db);
        ClaimRepository claimRepository = new ClaimRepository(db);

        // register events
        this.getServer().getPluginManager().registerEvents(this, this);

        // register commands
        LiteralArgumentBuilder<CommandSourceStack> rootCommand = Commands.literal("k");
        rootCommand.then(new KingdomCreateCommand(this, kingdomRepository).createCommand());
        rootCommand.then(new KingdomDisbandCommand(this, kingdomRepository).createCommand());
        rootCommand.then(new KingdomClaimCommand(this, memberRepository, claimRepository).createCommand());
        rootCommand.then(new KingdomUnclaimCommand(this, memberRepository, claimRepository).createCommand());
        rootCommand.then(new KingdomAddMemberCommand(this, memberRepository).createCommand());
        rootCommand.then(new KingdomKickMemberCommand(this, memberRepository).createCommand());
        rootCommand.then(new KingdomSetHomeCommand(this, memberRepository, kingdomRepository).createCommand());
        rootCommand.then(new KingdomTpHomeCommand(this, memberRepository, kingdomRepository).createCommand());
        rootCommand.then(new KingdomDeclareWarCommand(db, this).createCommand());
        rootCommand.then(new KingdomCancelWarCommand(db, this).createCommand());
        LiteralArgumentBuilder<CommandSourceStack> rootChatCommand = Commands.literal("kc");
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(rootCommand.build());
            event.registrar().register(rootChatCommand.build());
        });
    }

    @Override
    public void onDisable() {
        // Safely close the database connection pool on plugin disable/reload
        if (db != null) {
            db.close();
        }
    }

    public DatabaseService getDb() {
        return db;
    }
}
