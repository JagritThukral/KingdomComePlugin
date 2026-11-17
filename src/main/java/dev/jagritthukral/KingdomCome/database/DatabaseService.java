package dev.jagritthukral.KingdomCome.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.sql.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseService {
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private final ExecutorService asyncExecutor;

    public DatabaseService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = Executors.newSingleThreadExecutor();
        initDatabase();
    }

    private void initDatabase() {
        plugin.getSLF4JLogger().debug("Initializing Database");
        HikariConfig config = createConfig();
        this.dataSource = new HikariDataSource(config);

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Kingdoms(
                        kingdom_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        leader_uuid TEXT NOT NULL,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        home_world_uuid TEXT,
                        home_x REAL,
                        home_y REAL,
                        home_z REAL,
                        CHECK (
                            (home_world_uuid IS NULL AND home_x IS NULL AND home_y IS NULL AND home_z IS NULL) OR
                            (home_world_uuid IS NOT NULL AND home_x IS NOT NULL AND home_y IS NOT NULL AND home_z IS NOT NULL)
                        )
                    );
                    CREATE TABLE IF NOT EXISTS Players(
                        kingdom_id INTEGER NOT NULL,
                        player_uuid TEXT PRIMARY KEY,
                        role TEXT CHECK ( role in ('LEADER','DUKE','MEMBER')) default 'MEMBER',
                        discord_id TEXT,
                        FOREIGN KEY (kingdom_id)
                        REFERENCES Kingdoms(kingdom_id)
                        ON DELETE CASCADE
                    );
                    CREATE TABLE IF NOT EXISTS Claims(
                        kingdom_id INTEGER NOT NULL,
                        chunk_x INTEGER NOT NULL,
                        chunk_z INTEGER NOT NULL,
                        world_uuid TEXT NOT NULL,
                        PRIMARY KEY (chunk_x, chunk_z),
                        FOREIGN KEY (kingdom_id)
                        REFERENCES Kingdoms(kingdom_id)
                        ON DELETE CASCADE
                      );
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute DDL database initialization", e);
        }
    }


    private @NonNull HikariConfig createConfig() {
        plugin.getSLF4JLogger().debug("Configuring HikariCP for SQLite");
        HikariConfig config = new HikariConfig();

        // Ensure database directory exists
        File dbFile = new File(plugin.getDataFolder(), "KingdomCome.db");
        if (!plugin.getDataFolder().exists()) {
            boolean dirsCreated = plugin.getDataFolder().mkdirs();
            if (dirsCreated) plugin.getSLF4JLogger().debug("created plugin dir");
        }

        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

        // SQLite Optimizations & WAL Protocol
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(1);
        config.setMaxLifetime(60000);
        config.setIdleTimeout(45000);
        config.setConnectionTimeout(10000);
        config.setPoolName("SQLiteHikariPool");
        config.addDataSourceProperty("foreign_keys", "true");
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;");
        config.addDataSourceProperty("busy_timeout", "5000");

        return config;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariDataSource is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    public ExecutorService getAsyncExecutor() {
        return this.asyncExecutor;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}