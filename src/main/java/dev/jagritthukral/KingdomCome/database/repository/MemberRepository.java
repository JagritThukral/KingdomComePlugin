package dev.jagritthukral.KingdomCome.database.repository;

import dev.jagritthukral.KingdomCome.database.DatabaseService;
import dev.jagritthukral.KingdomCome.exception.AlreadyInKingdomException;
import dev.jagritthukral.KingdomCome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.KingdomCome.models.KingdomMember;
import dev.jagritthukral.KingdomCome.models.KingdomPlayerRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class MemberRepository {
    private final DatabaseService db;
    private final ExecutorService asyncExecutor;

    public MemberRepository(DatabaseService db) {
        this.db = db;
        this.asyncExecutor = db.getAsyncExecutor();
    }

    public CompletableFuture<Optional<KingdomMember>> getKingdomMember(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String getKingdomMemberByUuidSql = "SELECT * FROM Players WHERE player_uuid = ?;";
            try (Connection connection = db.getConnection();
                 PreparedStatement getKingdomMemberByUuidStmt = connection.prepareStatement(getKingdomMemberByUuidSql)) {
                getKingdomMemberByUuidStmt.setString(1, playerUuid);
                try (ResultSet result = getKingdomMemberByUuidStmt.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new KingdomMember(result.getInt("kingdom_id"), result.getString("player_uuid"), KingdomPlayerRole.valueOf(result.getString("role")), result.getString("discord_id")));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> addKingdomMember(int kingdomId, String playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String addMemberByKingdomIdSql = "INSERT INTO Players(kingdom_id, player_uuid) VALUES (?,?) ;";
            try (Connection connection = db.getConnection();
                 PreparedStatement addMemberByKingdomIdStmt = connection.prepareStatement(addMemberByKingdomIdSql)) {
                addMemberByKingdomIdStmt.setInt(1, kingdomId);
                addMemberByKingdomIdStmt.setString(2, playerUuid);
                addMemberByKingdomIdStmt.executeUpdate();

            } catch (SQLException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("Players.player_uuid")) {
                    throw new AlreadyInKingdomException(e);
                }
                throw new RuntimeException("Failed to add player to kingdom", e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> kickKingdomMember(String playerUuid, int kingdomId) {
        return CompletableFuture.runAsync(() -> {
            String removeMemberSql = "DELETE FROM Players WHERE player_uuid = ? AND kingdom_id = ?;";
            try (Connection connection = db.getConnection();
                 PreparedStatement removeMemberStmt = connection.prepareStatement(removeMemberSql)) {
                removeMemberStmt.setString(1, playerUuid);
                removeMemberStmt.setInt(2, kingdomId);
                int rowsChanged = removeMemberStmt.executeUpdate();
                if (rowsChanged == 0)
                    throw new MissingKingdomMembershipException("That player is not a member of your kingdom");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to kick player from kingdom", e);
            }
        }, asyncExecutor);
    }
}
