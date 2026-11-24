package dev.jagritthukral.kingdomcome;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.jagritthukral.kingdomcome.commands.*;
import dev.jagritthukral.kingdomcome.database.DatabaseService;
import dev.jagritthukral.kingdomcome.database.repository.ClaimRepository;
import dev.jagritthukral.kingdomcome.database.repository.KingdomRepository;
import dev.jagritthukral.kingdomcome.database.repository.MemberRepository;
import dev.jagritthukral.kingdomcome.events.ClaimProtectionListener;
import dev.jagritthukral.kingdomcome.events.KingdomBossbarListener;
import dev.jagritthukral.kingdomcome.events.MemberNameSyncListener;
import dev.jagritthukral.kingdomcome.events.PvPListener;
import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.service.ClaimService;
import dev.jagritthukral.kingdomcome.service.KingdomService;
import dev.jagritthukral.kingdomcome.service.MemberService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KingdomCome extends JavaPlugin {

    private DatabaseService db;
    private KingdomBossbarListener bossBarListener;

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
                .map(World::getUID)
                .toList();

        // get data from db repository
        ArrayList<Claim> claimsList = claimRepository.getAllClaims()
                .join();
        ArrayList<KingdomMember> membersList = memberRepository.getAllMembers()
                .join();
        ArrayList<Kingdom> kingdomsList = kingdomRepository.getAllKingdoms()
                .join();

        // initialize services
        ClaimService claimService = new ClaimService(this, worldUuidList, claimsList, memberRepository, claimRepository);
        MemberService memberService = new MemberService(this, membersList, memberRepository);
        KingdomService kingdomService = new KingdomService(kingdomsList, kingdomRepository, memberService, claimService);

        // initialize listeners
        bossBarListener = new KingdomBossbarListener(this, claimService, kingdomService, memberService);
        memberService.addChangeListener(bossBarListener);

        List<Listener> listeners = List.of(
                new MemberNameSyncListener(this, memberService),
                new ClaimProtectionListener(this, claimService, memberService, kingdomService),
                new PvPListener(memberService, claimService),
                bossBarListener
        );

        // register events
        for (Listener listener : listeners) {
            this.getServer()
                    .getPluginManager()
                    .registerEvents(listener, this);
        }

        // register commands
        LiteralArgumentBuilder<CommandSourceStack> rootCommand = Commands.literal("k");
        rootCommand.then(new KingdomCreateCommand(this, kingdomService).createCommand());
        rootCommand.then(new KingdomDisbandCommand(this, kingdomService).createCommand());
        rootCommand.then(new KingdomClaimCommand(this, claimService).createCommand());
        rootCommand.then(new KingdomUnclaimCommand(this, claimService).createCommand());
        rootCommand.then(new KingdomAddMemberCommand(this, memberService).createCommand());
        rootCommand.then(new KingdomKickMemberCommand(this, memberService).createCommand());
        rootCommand.then(new KingdomSetHomeCommand(this, kingdomService, memberService).createCommand());
        rootCommand.then(new KingdomTpHomeCommand(this, kingdomService, memberService).createCommand());
        rootCommand.then(new KingdomDeclareWarCommand(db, this).createCommand());
        rootCommand.then(new KingdomCancelWarCommand(db, this).createCommand());
        LiteralArgumentBuilder<CommandSourceStack> rootChatCommand = Commands.literal("kc");
        this.getLifecycleManager()
                .registerEventHandler(LifecycleEvents.COMMANDS, event -> {
                    event.registrar()
                            .register(rootCommand.build());
                    event.registrar()
                            .register(rootChatCommand.build());
                });
    }

    @Override
    public void onDisable() {
        if (bossBarListener != null) {
            bossBarListener.removeAllBars();
        }
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
