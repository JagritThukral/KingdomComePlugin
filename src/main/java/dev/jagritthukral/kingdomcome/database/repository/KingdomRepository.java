package dev.jagritthukral.kingdomcome.database.repository;

import dev.jagritthukral.kingdomcome.database.DatabaseService;
import dev.jagritthukral.kingdomcome.exception.AlreadyInKingdomException;
import dev.jagritthukral.kingdomcome.exception.DuplicateKingdomException;
import dev.jagritthukral.kingdomcome.exception.KingdomNotFoundException;
import dev.jagritthukral.kingdomcome.models.Kingdom;
import dev.jagritthukral.kingdomcome.models.KingdomHome;
import dev.jagritthukral.kingdomcome.models.KingdomPlayerRole;
import org.jspecify.annotations.Nullable;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
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

    private KingdomHome getKingdomHomeObjectFromResultSet(ResultSet resultSet) throws SQLException {
        String worldUuidString = resultSet.getString("home_world_uuid");
        boolean worldNull = worldUuidString == null || worldUuidString.isEmpty();
        UUID worldUuid = worldNull ? null : UUID.fromString(worldUuidString);
        double x = resultSet.getDouble("home_x");
        boolean xNull = resultSet.wasNull();
        double y = resultSet.getDouble("home_y");
        boolean yNull = resultSet.wasNull();
        double z = resultSet.getDouble("home_z");
        boolean zNull = resultSet.wasNull();
        return (worldNull || xNull || yNull || zNull)
                ? null
                : new KingdomHome(worldUuid, x, y, z);
    }

    public CompletableFuture<Optional<Kingdom>> findKingdomByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String findKingdomByNameSql = "SELECT * from Kingdoms WHERE name = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement findKingdomByNameStmt = connection.prepareStatement(findKingdomByNameSql)) {
                findKingdomByNameStmt.setString(1, name);
                try (ResultSet kingdomResultSet = findKingdomByNameStmt.executeQuery()) {
                    if (kingdomResultSet.next()) {


                        return Optional.of(new Kingdom(
                                kingdomResultSet.getLong("kingdom_id"),
                                UUID.fromString(kingdomResultSet.getString("leader_uuid")),
                                name,
                                kingdomResultSet.getLong("created_at"),
                                getKingdomHomeObjectFromResultSet(kingdomResultSet)
                        ));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<ArrayList<Kingdom>> getAllKingdoms() {
        return CompletableFuture.supplyAsync(() -> {
            ArrayList<Kingdom> kingdomsList = new ArrayList<>();
            String getAllKingdomsSql = "SELECT * FROM Kingdoms";
            try (Connection connection = db.getConnection(); PreparedStatement getAllKingdomsStmt = connection.prepareStatement(getAllKingdomsSql)) {
                try (ResultSet kingdomsResultSet = getAllKingdomsStmt.executeQuery()) {
                    while (kingdomsResultSet.next()) {
                        kingdomsList.add(new Kingdom(kingdomsResultSet.getLong("kingdom_id"),
                                UUID.fromString(kingdomsResultSet.getString("leader_uuid")),
                                kingdomsResultSet.getString("name"),
                                kingdomsResultSet.getLong("created_at"),
                                getKingdomHomeObjectFromResultSet(kingdomsResultSet)
                        ));
                    }
                    return kingdomsList;
                }
            } catch (SQLException e) {
                throw new RuntimeException("failed to get all kingdoms", e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Kingdom> createKingdomWithLeader(String name, UUID leaderUuid, String lastKnownName) {
        return CompletableFuture.supplyAsync(() -> {

            String insertKingdomSql = "INSERT INTO Kingdoms (name, leader_uuid,created_at) VALUES (?, ?, ?);";
            String insertPlayerSql = "INSERT INTO Players (kingdom_id, player_uuid, role,last_known_name) VALUES (?, ?, ?, ?);";
            long createdAt = Instant.now()
                    .getEpochSecond();
            try (Connection connection = db.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement kingdomStmt = connection.prepareStatement(insertKingdomSql, Statement.RETURN_GENERATED_KEYS); PreparedStatement playerStmt = connection.prepareStatement(insertPlayerSql)) {

                    kingdomStmt.setString(1, name);
                    kingdomStmt.setString(2, leaderUuid.toString());
                    kingdomStmt.setLong(3, createdAt);
                    kingdomStmt.executeUpdate();

                    long kingdomId;
                    try (ResultSet rs = kingdomStmt.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("No generated kingdom_id returned");
                        }
                        kingdomId = rs.getLong(1); // get the AUTO_INCREMENTED value
                    }

                    playerStmt.setLong(1, kingdomId);
                    playerStmt.setString(2, leaderUuid.toString());
                    playerStmt.setString(3, KingdomPlayerRole.LEADER.name());
                    playerStmt.setString(4, lastKnownName);
                    playerStmt.executeUpdate();

                    connection.commit();
                    return new Kingdom(kingdomId, leaderUuid, name, createdAt, null);
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

    public CompletableFuture<Long> disbandKingdom(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String deleteKingdomSql = "DELETE FROM Kingdoms WHERE name = ? RETURNING kingdom_id;";
            try (Connection connection = db.getConnection(); PreparedStatement deleteKingdomStmt = connection.prepareStatement(deleteKingdomSql)) {
                deleteKingdomStmt.setString(1, name);
                try (ResultSet kingdomIdResultSet = deleteKingdomStmt.executeQuery()) {
                    if (kingdomIdResultSet.next()) {
                        return kingdomIdResultSet.getLong("kingdom_id");
                    }
                    throw new KingdomNotFoundException("A kingdom named '" + name + "' does not exist.");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to disband kingdom", e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<@Nullable KingdomHome> getKingdomHome(long kingdomId) {
        return CompletableFuture.supplyAsync(() -> {
            String getHomeSql = "SELECT home_world_uuid, home_x, home_y, home_z FROM Kingdoms WHERE kingdom_id = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement getHomeStmt = connection.prepareStatement(getHomeSql)) {
                getHomeStmt.setLong(1, kingdomId);
                try (ResultSet homeResult = getHomeStmt.executeQuery()) {
                    if (!homeResult.next()) {
                        throw new KingdomNotFoundException();
                    }
                    return getKingdomHomeObjectFromResultSet(homeResult);
                }


            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> setKingdomHome(long kingdomId, KingdomHome home) {
        return CompletableFuture.runAsync(() -> {
            String addHomeSql = "UPDATE Kingdoms SET home_world_uuid = ?,home_x = ?,home_y = ?,home_z = ? WHERE kingdom_id = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement addHomeStmt = connection.prepareStatement(addHomeSql)) {
                addHomeStmt.setString(1, home.world_uuid()
                        .toString());
                addHomeStmt.setDouble(2, home.x());
                addHomeStmt.setDouble(3, home.y());
                addHomeStmt.setDouble(4, home.z());
                addHomeStmt.setLong(5, kingdomId);
                int rowsChanged = addHomeStmt.executeUpdate();
                if (rowsChanged == 0) throw new RuntimeException("failed to set kingdom home");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
}
