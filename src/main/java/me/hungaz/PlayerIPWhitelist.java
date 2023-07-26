package me.hungaz;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;

public class PlayerIPWhitelist extends JavaPlugin implements Listener {
    private String mysqlHostname;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String kickMessage; // Kick message

    // HashMap to store cached results of whitelisted IP addresses
    private HashMap<String, Long> whitelistCache = new HashMap<>();
    private int cacheExpiration; // Cache expiration time in seconds

    @Override
    public void onEnable() {
        // Check if a table exists or not, if not, call createTableIfNotExists()
        loadConfig();
        if (testMySQLConnection()) {
            getLogger().info("MySQL connected successfully.");
        } else {
            getLogger().warning("MySQL connection failed!");
        }
        createTableIfNotExists();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadConfig() {
        // Load the configuration (in config.yml)
        saveDefaultConfig();
        reloadConfig();

        mysqlHostname = getConfig().getString("mysql.hostname");
        mysqlPort = getConfig().getInt("mysql.port");
        mysqlDatabase = getConfig().getString("mysql.database");
        mysqlUsername = getConfig().getString("mysql.username");
        mysqlPassword = getConfig().getString("mysql.password");
        kickMessage = getConfig().getString("kick-message", "Mày là thằng nào?!"); // Kick message
        cacheExpiration = getConfig().getInt("cache-expiration", 300); // Cache expiration
    }

    private boolean testMySQLConnection() {
        try (Connection ignored = DriverManager.getConnection(
                "jdbc:mysql://" + mysqlHostname + ":" + mysqlPort + "/" + mysqlDatabase,
                mysqlUsername, mysqlPassword)) {
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createTableIfNotExists() {
        // Create a table
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + mysqlHostname + ":" + mysqlPort + "/" + mysqlDatabase,
                mysqlUsername, mysqlPassword)) {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS playerip_whitelisted (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "ip_address VARCHAR(45) NOT NULL" +
                    ")";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableQuery);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        // Get the player's IP address
        String ipAddress = event.getAddress().getHostAddress();
        boolean isWhitelisted = checkWhitelist(ipAddress);
        if (!isWhitelisted) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            getLogger().warning("IP " + ipAddress + " tried to join, but not whitelisted.");
        }
    }

    private boolean checkWhitelist(String ipAddress) {
        // Check if the result is already cached and not expired
        if (whitelistCache.containsKey(ipAddress)) {
            long currentTime = System.currentTimeMillis();
            long cacheTime = whitelistCache.get(ipAddress);
            if (cacheTime + (cacheExpiration * 1000) > currentTime) {
                return true;
            }
        }

        // Find IP address in database
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + mysqlHostname + ":" + mysqlPort + "/" + mysqlDatabase,
                mysqlUsername, mysqlPassword)) {
            String query = "SELECT COUNT(*) FROM playerip_whitelisted WHERE ip_address = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, ipAddress);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        boolean isWhitelisted = resultSet.getInt(1) > 0;
                        // Cache the result with the current timestamp
                        whitelistCache.put(ipAddress, System.currentTimeMillis());
                        return isWhitelisted;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
