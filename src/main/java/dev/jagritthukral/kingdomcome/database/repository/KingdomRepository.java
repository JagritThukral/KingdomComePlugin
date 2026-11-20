package dev.jagritthukral.kingdomcome.database.repository;

import dev.jagritthukral.kingdomcome.database.DatabaseService;
import dev.jagritthukral.kingdomcome.exception.AlreadyInKingdomException;
import dev.jagritthukral.kingdomcome.exception.DuplicateKingdomException;
import dev.jagritthukral.kingdomcome.exception.KingdomHomeNotSetException;
import dev.jagritthukral.kingdomcome.exception.KingdomNotFoundException;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.models.KingdomHome;
import dev.jagritthukral.kingdomcome.models.KingdomPlayerRole;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class KingdomRepository {
    private final DatabaseService db;
    private final ExecutorService asyncExecutor;

    public KingdomRepository(DatabaseService db) {
        this.db = db;
        this.asyncExecutor = db.getAsyncExecutor();
    }

    public CompletableFuture<Optional<Kingdom>> findKingdomByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String findKingdomByNameSql = "SELECT * from Kingdoms WHERE name = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement findKingdomByNameStmt = connection.prepareStatement(findKingdomByNameSql)) {
                findKingdomByNameStmt.setString(1, name);
                try (ResultSet kingdomResultSet = findKingdomByNameStmt.executeQuery()) {
                    if (kingdomResultSet.next()) {
                        String worldUuidString = kingdomResultSet.getString("home_world_uuid");
                        boolean worldNull = worldUuidString == null || worldUuidString.isEmpty();
                        UUID worldUuid = worldNull ? null : UUID.fromString(worldUuidString);
                        double x = kingdomResultSet.getDouble("home_x");
                        boolean xNull = kingdomResultSet.wasNull();
                        double y = kingdomResultSet.getDouble("home_y");
                        boolean yNull = kingdomResultSet.wasNull();
                        double z = kingdomResultSet.getDouble("home_z");
                        boolean zNull = kingdomResultSet.wasNull();
                        Optional<KingdomHome> home = (worldNull || xNull || yNull || zNull)
                                ? Optional.empty()
                                : Optional.of(new KingdomHome(worldUuid, x, y, z));

                        return Optional.of(new Kingdom(
                                kingdomResultSet.getInt("kingdom_id"),
                                kingdomResultSet.getString("leader_uuid"),
                                name,
                                kingdomResultSet.getLong("created_at"),
                                home
                        ));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Kingdom> createKingdomWithLeader(String name, String leaderUuid) {
        return CompletableFuture.supplyAsync(() -> {

            String insertKingdomSql = "INSERT INTO Kingdoms (name, leader_uuid,created_at) VALUES (?, ?, ?);";
            String insertPlayerSql = "INSERT INTO Players (kingdom_id, player_uuid, role) VALUES (?, ?, ?);";
            long createdAt = Instant.now().getEpochSecond();
            try (Connection connection = db.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement kingdomStmt = connection.prepareStatement(insertKingdomSql, Statement.RETURN_GENERATED_KEYS); PreparedStatement playerStmt = connection.prepareStatement(insertPlayerSql)) {

                    kingdomStmt.setString(1, name);
                    kingdomStmt.setString(2, leaderUuid);
                    kingdomStmt.setLong(3, createdAt);
                    kingdomStmt.executeUpdate();

                    int kingdomId;
                    try (ResultSet rs = kingdomStmt.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("No generated kingdom_id returned");
                        }
                        kingdomId = rs.getInt(1); // get the AUTO_INCREMENTED value
                    }

                    playerStmt.setInt(1, kingdomId);
                    playerStmt.setString(2, leaderUuid);
                    playerStmt.setString(3, KingdomPlayerRole.LEADER.name());
                    playerStmt.executeUpdate();

                    connection.commit();
                    return new Kingdom(kingdomId, leaderUuid, name, createdAt, Optional.empty());
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("Kingdoms.name")) {
                    throw new DuplicateKingdomException("A kingdom named '" + name + "' already exists.", e);
                }
                if (msg.contains("Players.player_uuid")) {
                    throw new AlreadyInKingdomException(e);
                }
                throw new RuntimeException("Failed to create kingdom", e);
            }
        }, asyncExecutor);

    }

    public CompletableFuture<Void> disbandKingdom(String name) {
        return CompletableFuture.runAsync(() -> {
            String deleteKingdomSql = "DELETE FROM Kingdoms WHERE name = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement deleteKingdomStmt = connection.prepareStatement(deleteKingdomSql)) {
                deleteKingdomStmt.setString(1, name);
                int result = deleteKingdomStmt.executeUpdate();
                if (result == 0) {
                    throw new KingdomNotFoundException("A kingdom named '" + name + "' does not exist.");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to disband kingdom", e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<KingdomHome> getKingdomHome(int kingdomId) {
        return CompletableFuture.supplyAsync(() -> {
            String getHomeSql = "SELECT home_world_uuid, home_x, home_y, home_z FROM Kingdoms WHERE kingdom_id = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement getHomeStmt = connection.prepareStatement(getHomeSql)) {
                getHomeStmt.setInt(1, kingdomId);
                try (ResultSet homeResult = getHomeStmt.executeQuery()) {
                    if (!homeResult.next()) {
                        throw new KingdomNotFoundException();
                    }
                    String worldUuidString = homeResult.getString("home_world_uuid");
                    boolean worldNull = worldUuidString == null || worldUuidString.isEmpty();
                    UUID worldUuid = worldNull ? null : UUID.fromString(worldUuidString);
                    double x = homeResult.getDouble("home_x");
                    boolean xNull = homeResult.wasNull();
                    double y = homeResult.getDouble("home_y");
                    boolean yNull = homeResult.wasNull();
                    double z = homeResult.getDouble("home_z");
                    boolean zNull = homeResult.wasNull();

                    if (worldNull || xNull || yNull || zNull) {
                        throw new KingdomHomeNotSetException();
                    }

                    return new KingdomHome(worldUuid, x, y, z);
                }


            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> setKingdomHome(int kingdomId, KingdomHome home) {
        return CompletableFuture.runAsync(() -> {
            String addHomeSql = "UPDATE Kingdoms SET home_world_uuid = ?,home_x = ?,home_y = ?,home_z = ? WHERE kingdom_id = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement addHomeStmt = connection.prepareStatement(addHomeSql)) {
                addHomeStmt.setString(1, home.world_uuid().toString());
                addHomeStmt.setDouble(2, home.x());
                addHomeStmt.setDouble(3, home.y());
                addHomeStmt.setDouble(4, home.z());
                addHomeStmt.setInt(5, kingdomId);
                int rowsChanged = addHomeStmt.executeUpdate();
                if (rowsChanged == 0) throw new RuntimeException("failed to set kingdom home");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
}
