package dev.jagritthukral.kingdomcome.database.repository;

import dev.jagritthukral.kingdomcome.database.DatabaseService;
import dev.jagritthukral.kingdomcome.exception.AlreadyInKingdomException;
import dev.jagritthukral.kingdomcome.exception.MissingKingdomMembershipException;
import dev.jagritthukral.kingdomcome.models.KingdomMember;
import dev.jagritthukral.kingdomcome.models.KingdomPlayerRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class MemberRepository {
    private final DatabaseService db;
    private final ExecutorService asyncExecutor;

    public MemberRepository(DatabaseService db) {
        this.db = db;
        this.asyncExecutor = db.getAsyncExecutor();
    }

    public CompletableFuture<Optional<KingdomMember>> getKingdomMember(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String getKingdomMemberByUuidSql = "SELECT * FROM Players WHERE player_uuid = ?;";
            try (Connection connection = db.getConnection(); PreparedStatement getKingdomMemberByUuidStmt = connection.prepareStatement(getKingdomMemberByUuidSql)) {
                getKingdomMemberByUuidStmt.setString(1, playerUuid.toString());
                try (ResultSet result = getKingdomMemberByUuidStmt.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new KingdomMember(result.getLong("kingdom_id"), UUID.fromString(result.getString("player_uuid")), result.getString("last_known_name"), KingdomPlayerRole.valueOf(result.getString("role")), result.getString("discord_id")));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<ArrayList<KingdomMember>> getKingdomMemberByName(String lastKnownName, long kingdomId) {
        return CompletableFuture.supplyAsync(() -> {
            String getKingdomMemberByNameSql = "SELECT * FROM Players WHERE last_known_name = ? COLLATE NOCASE AND kingdom_id = ?;";
            ArrayList<KingdomMember> membersList = new ArrayList<>();
            try (Connection connection = db.getConnection(); PreparedStatement getKingdomMemberByNameStmt = connection.prepareStatement(getKingdomMemberByNameSql)) {
                getKingdomMemberByNameStmt.setString(1, lastKnownName);
                getKingdomMemberByNameStmt.setLong(2, kingdomId);
                try (ResultSet memberResultSet = getKingdomMemberByNameStmt.executeQuery()) {
                    while (memberResultSet.next()) {
                        membersList.add(new KingdomMember(memberResultSet.getLong("kingdom_id"), UUID.fromString(memberResultSet.getString("player_uuid")), memberResultSet.getString("last_known_name"), KingdomPlayerRole.valueOf(memberResultSet.getString("role")), memberResultSet.getString("discord_id")));
                    }
                    return membersList;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<ArrayList<KingdomMember>> getAllKingdomMembers(long kingdomId) {
        return CompletableFuture.supplyAsync(() -> {
            ArrayList<KingdomMember> membersArrayList = new ArrayList<>();
            String getAllMembersSql = "SELECT * FROM Players WHERE kingdom_id = ?";
            try (Connection connection = db.getConnection(); PreparedStatement getAllMembersStmt = connection.prepareStatement(getAllMembersSql)) {
                getAllMembersStmt.setLong(1, kingdomId);
                try (ResultSet membersResultSet = getAllMembersStmt.executeQuery()) {
                    while (membersResultSet.next()) {
                        membersArrayList.add(new KingdomMember(membersResultSet.getLong("kingdom_id"), UUID.fromString(membersResultSet.getString("player_uuid")), membersResultSet.getString("last_known_name"), KingdomPlayerRole.valueOf(membersResultSet.getString("role")), membersResultSet.getString("discord_id")));
                    }
                    return membersArrayList;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<ArrayList<KingdomMember>> getAllMembers() {
        return CompletableFuture.supplyAsync(() -> {
            String getAllMembersSql = "SELECT * FROM Players";
            ArrayList<KingdomMember> membersList = new ArrayList<>();
            try (Connection connection = db.getConnection(); PreparedStatement getAllMembersStmt = connection.prepareStatement(getAllMembersSql)) {
                try (ResultSet allMembersResultSet = getAllMembersStmt.executeQuery()) {
                    while (allMembersResultSet.next()) {
                        membersList.add(new KingdomMember(allMembersResultSet.getLong("kingdom_id"), UUID.fromString(allMembersResultSet.getString("player_uuid")), allMembersResultSet.getString("last_known_name"), KingdomPlayerRole.valueOf(allMembersResultSet.getString("role")), allMembersResultSet.getString("discord_id")));
                    }
                    return membersList;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> addKingdomMember(long kingdomId, UUID playerUuid, String lastKnownName) {
        return CompletableFuture.runAsync(() -> {
            String addMemberByKingdomIdSql = "INSERT INTO Players(kingdom_id, player_uuid,last_known_name) VALUES (?,?,?) ;";
            try (Connection connection = db.getConnection(); PreparedStatement addMemberByKingdomIdStmt = connection.prepareStatement(addMemberByKingdomIdSql)) {
                addMemberByKingdomIdStmt.setLong(1, kingdomId);
                addMemberByKingdomIdStmt.setString(2, playerUuid.toString());
                addMemberByKingdomIdStmt.setString(3, lastKnownName);
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

    public CompletableFuture<Void> kickKingdomMember(UUID playerUuid, long kingdomId) {
        return CompletableFuture.runAsync(() -> {
            String removeMemberSql = "DELETE FROM Players WHERE player_uuid = ? AND kingdom_id = ? ;";
            try (Connection connection = db.getConnection(); PreparedStatement removeMemberStmt = connection.prepareStatement(removeMemberSql)) {
                removeMemberStmt.setString(1, playerUuid.toString());
                removeMemberStmt.setLong(2, kingdomId);
                int rowsChanged = removeMemberStmt.executeUpdate();
                if (rowsChanged == 0)
                    throw new MissingKingdomMembershipException("That player is not a member of your kingdom");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to kick player from kingdom", e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> updateMemberName(UUID playerUuid, String newName) {
        return CompletableFuture.runAsync(() -> {
            String updateMemberNameSql = "UPDATE Players SET last_known_name = ? WHERE player_uuid = ?";
            try (Connection connection = db.getConnection(); PreparedStatement updateMemberNameStmt = connection.prepareStatement(updateMemberNameSql)) {
                updateMemberNameStmt.setString(1, newName);
                updateMemberNameStmt.setString(2, playerUuid.toString());
                updateMemberNameStmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update member name", e);
            }
        }, asyncExecutor);
    }

}
