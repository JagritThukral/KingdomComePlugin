package dev.jagritthukral.kingdomcome;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.jagritthukral.kingdomcome.commands.*;
import dev.jagritthukral.kingdomcome.database.DatabaseService;
import dev.jagritthukral.kingdomcome.database.repository.ClaimRepository;
import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.service.ClaimService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        List<UUID> worldUuidList = Bukkit.getWorlds()
                .stream()
                .map(WorldInfo::getUID)
                .toList();
        ArrayList<Claim> claimsList = claimRepository.getAllClaims().join();
        ClaimService claimService = new ClaimService(worldUuidList, claimsList, memberRepository, claimRepository);
        // register events
        this.getServer().getPluginManager().registerEvents(this, this);

        // register commands
        LiteralArgumentBuilder<CommandSourceStack> rootCommand = Commands.literal("k");
        rootCommand.then(new KingdomCreateCommand(this, kingdomRepository).createCommand());
        rootCommand.then(new KingdomDisbandCommand(this, kingdomRepository).createCommand());
        rootCommand.then(new KingdomClaimCommand(this, claimService).createCommand());
        rootCommand.then(new KingdomUnclaimCommand(this, claimService).createCommand());
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
            try {
                db.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
