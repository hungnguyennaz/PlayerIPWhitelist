package me.hungaz;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
    private final HashMap<String, CacheEntry> whitelistCache = new HashMap<>();
    private int cacheExpiration; // Cache expiration time in seconds
    private boolean doNotignoreLocalIPEnabled; // New variable to store the value of do-not-ignore-local-ip option
    private boolean logIgnoredLocalIP; // New variable to store the value of log-ignored-local-ip option

    @Override
    public void onEnable() {
        // Load the configuration (in config.yml)
        saveDefaultConfig();
        loadConfigSettings();

        if (testMySQLConnection()) {
            getLogger().info("MySQL connected successfully.");
        } else {
            getLogger().warning("MySQL connection failed!");
        }

        createTableIfNotExists();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadConfigSettings() {
        // Configuration variables
        mysqlHostname = getConfig().getString("mysql.hostname");
        mysqlPort = getConfig().getInt("mysql.port");
        mysqlDatabase = getConfig().getString("mysql.database");
        mysqlUsername = getConfig().getString("mysql.username");
        mysqlPassword = getConfig().getString("mysql.password");
        kickMessage = getConfig().getString("kick-message", "Your IP isn't whitelisted!!!"); // Kick message
        cacheExpiration = getConfig().getInt("cache-expiration", 300); // Cache expiration
        doNotignoreLocalIPEnabled = getConfig().getBoolean("do-not-ignore-local-ip.enabled", true);
        logIgnoredLocalIP = getConfig().getBoolean("do-not-ignore-local-ip.log-ignored-local-ip", true);
    }

    private static class CacheEntry {
        private final boolean isWhitelisted;
        private final long timestamp;

        public CacheEntry(boolean isWhitelisted, long timestamp) {
            this.isWhitelisted = isWhitelisted;
            this.timestamp = timestamp;
        }

        public boolean isWhitelisted() {
            return isWhitelisted;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public boolean testMySQLConnection() {
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
        // Get the player's IP address and name
        String ipAddress = event.getAddress().getHostAddress();
        String playerName = event.getName();

        if (doNotignoreLocalIPEnabled && isLocalIpAddress(ipAddress)) {
            if (logIgnoredLocalIP) {
                getLogger().info("Player " + playerName + " with IP " + ipAddress + " connected through a local IP, ignoring.");
            }
            // Prevent whitelist check for local IP addresses
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            return;
        }

        boolean isWhitelisted = checkWhitelist(ipAddress);
        if (!isWhitelisted) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            getLogger().warning("Player " + playerName + " with IP " + ipAddress + " tried to join, but not whitelisted.");
        }
    }

    private boolean checkWhitelist(String ipAddress) {
        // Check if the result is already cached and not expired
        if (whitelistCache.containsKey(ipAddress)) {
            CacheEntry entry = whitelistCache.get(ipAddress);
            long currentTime = System.currentTimeMillis();
            long cacheTime = entry.getTimestamp();
            if (cacheTime + (cacheExpiration * 1000L) > currentTime) {
                return entry.isWhitelisted();
            }
        }

        // Skip database query for local IP addresses
        if (isLocalIpAddress(ipAddress)) {
            if (logIgnoredLocalIP) {
                getLogger().info("Player with IP " + ipAddress + " connected through a local IP, ignoring.");
            }
            // Cache the result for local IP address as whitelisted, with the current timestamp
            whitelistCache.put(ipAddress, new CacheEntry(true, System.currentTimeMillis()));
            return true;
        }

        // Find IP address in the database
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
                        whitelistCache.put(ipAddress, new CacheEntry(isWhitelisted, System.currentTimeMillis()));
                        return isWhitelisted;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isLocalIpAddress(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        }
    }
}
