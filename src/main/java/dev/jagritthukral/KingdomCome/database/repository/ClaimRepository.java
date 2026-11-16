package dev.jagritthukral.KingdomCome.database.repository;

import dev.jagritthukral.KingdomCome.database.DatabaseService;
import dev.jagritthukral.KingdomCome.exception.ChunkNotClaimedException;
import dev.jagritthukral.KingdomCome.models.Claim;
import dev.jagritthukral.KingdomCome.models.ClaimStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ClaimRepository {
    private final DatabaseService db;
    private final Executor asyncExecutor;

    public ClaimRepository(DatabaseService db) {
        this.db = db;
        this.asyncExecutor = db.getAsyncExecutor();
    }

    public CompletableFuture<Optional<Claim>> getClaim(int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT kingdom_id FROM Claims WHERE chunk_x = ? AND chunk_z = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, chunkX);
                stmt.setInt(2, chunkZ);
                try (ResultSet result = stmt.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new Claim(result.getInt("kingdom_id"), chunkX, chunkZ));
                    }
                    return Optional.empty();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<ClaimStatus> claimChunk(int claimKingdomId, int chunkX, int chunkZ, String worldUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String getKingdomIdOfClaimSql = "SELECT kingdom_id FROM Claims WHERE chunk_x = ? AND chunk_z = ?;";
            String insertClaimSql = "INSERT INTO Claims (kingdom_id, chunk_x, chunk_z,world_uuid) VALUES (?, ?, ?, ?);";

            try (Connection connection = db.getConnection()) {
                // Check who currently owns the chunk
                try (PreparedStatement getKingdomIdOfClaimStmt = connection.prepareStatement(getKingdomIdOfClaimSql)) {
                    getKingdomIdOfClaimStmt.setInt(1, claimKingdomId);
                    getKingdomIdOfClaimStmt.setInt(2, chunkX);
                    getKingdomIdOfClaimStmt.setInt(3, chunkZ);
                    getKingdomIdOfClaimStmt.setString(4, worldUuid);
                    try (ResultSet resultSet = getKingdomIdOfClaimStmt.executeQuery()) {
                        if (resultSet.next()) {
                            int existingClaimKingdomId = resultSet.getInt("kingdom_id");
                            // chunk already claimed by the user's kingdom
                            if (existingClaimKingdomId == claimKingdomId) {
                                return ClaimStatus.ALREADY_OWNED_BY_YOU;

                            }
                            // claimed by other kingdom
                            return ClaimStatus.CLAIMED_BY_OTHER;

                        }
                    }
                }

                // If unowned, perform the insert
                try (PreparedStatement insertClaimStmt = connection.prepareStatement(insertClaimSql)) {
                    insertClaimStmt.setInt(1, claimKingdomId);
                    insertClaimStmt.setInt(2, chunkX);
                    insertClaimStmt.setInt(3, chunkZ);
                    insertClaimStmt.executeUpdate();

                    return ClaimStatus.CLAIMED;
                }

            } catch (SQLException e) {
                throw new RuntimeException("Failed to process chunk claim", e);
            }
        }, asyncExecutor);

    }

    public CompletableFuture<ClaimStatus> unclaimChunk(int unclaimKingdomId, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            String getKingdomIdOfClaimSql = "SELECT kingdom_id FROM Claims WHERE chunk_x = ? AND chunk_z = ?;";
            String deleteClaimSql = "DELETE FROM Claims WHERE chunk_x = ? AND chunk_z = ?;";
            try (Connection connection = db.getConnection()) {
                // check who owns the chunk
                try (PreparedStatement getKingdomIdOfClaimStmt = connection.prepareStatement(getKingdomIdOfClaimSql)) {
                    getKingdomIdOfClaimStmt.setInt(1, chunkX);
                    getKingdomIdOfClaimStmt.setInt(2, chunkZ);
                    try (ResultSet kindomIdResultSet = getKingdomIdOfClaimStmt.executeQuery()) {
                        if (kindomIdResultSet.next()) {
                            int existingClaimKingdomId = kindomIdResultSet.getInt("kingdom_id");
                            // if another kingdom owns this chunk
                            if (existingClaimKingdomId != unclaimKingdomId) {
                                return ClaimStatus.CLAIMED_BY_OTHER;
                            }
                        } else {
                            // can't unclaim a chunk that hasn't been claimed
                            throw new ChunkNotClaimedException();
                        }
                    }
                }
                // continue deletion if it's claim of their kingdom and isn't unclaimed
                try (PreparedStatement deleteClaimStmt = connection.prepareStatement(deleteClaimSql)) {
                    deleteClaimStmt.setInt(1, chunkX);
                    deleteClaimStmt.setInt(2, chunkZ);
                    int rowsChanged = deleteClaimStmt.executeUpdate();
                    if (rowsChanged == 0) {
                        throw new ChunkNotClaimedException();
                    }
                    return ClaimStatus.UNCLAIMED;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
}
