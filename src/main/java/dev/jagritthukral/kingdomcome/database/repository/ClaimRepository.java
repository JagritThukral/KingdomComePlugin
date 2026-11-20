package dev.jagritthukral.kingdomcome.database.repository;

import dev.jagritthukral.kingdomcome.database.DatabaseService;
import dev.jagritthukral.kingdomcome.exception.ChunkNotClaimedException;
import dev.jagritthukral.kingdomcome.models.Claim;
import dev.jagritthukral.kingdomcome.models.ClaimStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ClaimRepository {
    private final DatabaseService db;
    private final ExecutorService asyncExecutor;

    public ClaimRepository(DatabaseService db) {
        this.db = db;
        this.asyncExecutor = db.getAsyncExecutor();
    }

    public CompletableFuture<Optional<Claim>> getClaim(int chunkX, int chunkZ, UUID worldUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String getClaimByChunkCordsSql = "SELECT kingdom_id FROM Claims WHERE chunk_x = ? AND chunk_z = ? AND world_uuid = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement getClaimStmt = connection.prepareStatement(getClaimByChunkCordsSql)) {
                getClaimStmt.setInt(1, chunkX);
                getClaimStmt.setInt(2, chunkZ);
                getClaimStmt.setString(3, worldUuid.toString());
                try (ResultSet result = getClaimStmt.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new Claim(result.getInt("kingdom_id"), chunkX, chunkZ, worldUuid));
                    }
                    return Optional.empty();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<ArrayList<Claim>> getAllClaims() {
        return CompletableFuture.supplyAsync(() -> {
            String getAllClaimsSql = "SELECT * FROM Claims;";
            ArrayList<Claim> claimsList = new ArrayList<>();
            try (Connection connection = db.getConnection(); PreparedStatement getAllClaimsStmt = connection.prepareStatement(getAllClaimsSql)) {
                try (ResultSet claimsSet = getAllClaimsStmt.executeQuery()) {
                    while (claimsSet.next()) {
                        claimsList.add(new Claim(claimsSet.getInt("kingdom_id"), claimsSet.getInt("chunk_x"), claimsSet.getInt("chunk_z"), UUID.fromString(claimsSet.getString("world_uuid"))));
                    }

                    return claimsList;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<ClaimStatus> claimChunk(int claimKingdomId, int chunkX, int chunkZ, String worldUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String getKingdomIdOfClaimSql = "SELECT kingdom_id FROM Claims WHERE chunk_x = ? AND chunk_z = ? AND world_uuid = ?;";
            String insertClaimSql = "INSERT INTO Claims (kingdom_id, chunk_x, chunk_z,world_uuid) VALUES (?, ?, ?, ?);";

            try (Connection connection = db.getConnection()) {
                // Check who currently owns the chunk
                try (PreparedStatement getKingdomIdOfClaimStmt = connection.prepareStatement(getKingdomIdOfClaimSql)) {
                    getKingdomIdOfClaimStmt.setInt(1, chunkX);
                    getKingdomIdOfClaimStmt.setInt(2, chunkZ);
                    getKingdomIdOfClaimStmt.setString(3, worldUuid);
                    try (ResultSet claimSet = getKingdomIdOfClaimStmt.executeQuery()) {
                        if (claimSet.next()) {
                            int existingClaimKingdomId = claimSet.getInt("kingdom_id");
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
                    insertClaimStmt.setString(4, worldUuid);
                    insertClaimStmt.executeUpdate();

                    return ClaimStatus.CLAIMED;
                }

            } catch (SQLException e) {
                throw new RuntimeException("Failed to process chunk claim", e);
            }
        }, asyncExecutor);

    }

    public CompletableFuture<ClaimStatus> unclaimChunk(int unclaimKingdomId, int chunkX, int chunkZ, String worlduuid) {
        return CompletableFuture.supplyAsync(() -> {
            String getKingdomIdOfClaimSql = "SELECT kingdom_id FROM Claims WHERE chunk_x = ? AND chunk_z = ? AND world_uuid = ?;";
            String deleteClaimSql = "DELETE FROM Claims WHERE chunk_x = ? AND chunk_z = ? AND world_uuid = ?;";
            try (Connection connection = db.getConnection()) {
                // check who owns the chunk
                try (PreparedStatement getKingdomIdOfClaimStmt = connection.prepareStatement(getKingdomIdOfClaimSql)) {
                    getKingdomIdOfClaimStmt.setInt(1, chunkX);
                    getKingdomIdOfClaimStmt.setInt(2, chunkZ);
                    getKingdomIdOfClaimStmt.setString(3, worlduuid);
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
                    deleteClaimStmt.setString(3, worlduuid);
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
