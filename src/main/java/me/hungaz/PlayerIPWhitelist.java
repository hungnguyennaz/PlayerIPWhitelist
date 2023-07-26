package me.hungaz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class PlayerIPWhitelist extends JavaPlugin implements Listener {
    private String mysqlHostname;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String kickMessage; // Kick message
    private int cacheDuration; // Cache duration (in seconds)

    // Separate caches to store IP addresses and their timestamps (in milliseconds)
    private Map<String, String> ipCache = new HashMap<>();
    private Map<String, Long> timestampCache = new HashMap<>();

    @Override
    public void onEnable() {
        // Check if a table is exists or not, if not, call createTableIfNotExists()
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
        reloadConfig();

        mysqlHostname = getConfig().getString("mysql.hostname");
        mysqlPort = getConfig().getInt("mysql.port");
        mysqlDatabase = getConfig().getString("mysql.database");
        mysqlUsername = getConfig().getString("mysql.username");
        mysqlPassword = getConfig().getString("mysql.password");
        kickMessage = getConfig().getString("kick-message", "Mày là thằng nào?!");
        cacheDuration = getConfig().getInt("cache-duration", 300);
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
        // Use ipify API to get the public IP address from cache or the API
        String publicIPAddress = getPublicIPAddressFromCacheOrAPI();
        if (publicIPAddress == null) {
            getLogger().warning("Failed to retrieve public IP from ipify API.");
            return;
        }

        boolean isWhitelisted = checkWhitelist(publicIPAddress);
        if (!isWhitelisted) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            getLogger().warning("IP " + publicIPAddress + " tried to join, but not whitelisted.");
        }
    }

    private String getPublicIPAddressFromCacheOrAPI() {
        // Get public IP address and cache
        String publicIPAddress = ipCache.get("ip_address");
        long currentTime = System.currentTimeMillis();

        if (publicIPAddress != null) {
            long timestamp = timestampCache.getOrDefault("timestamp", 0L);
            if (currentTime - timestamp <= cacheDuration * 1000L) {
                return publicIPAddress;
            } else {
                ipCache.remove("ip_address");
                timestampCache.remove("timestamp");
            }
        }

        publicIPAddress = getPublicIPAddressFromAPI();
        if (publicIPAddress != null) {
            ipCache.put("ip_address", publicIPAddress);
            timestampCache.put("timestamp", currentTime);
        }

        return publicIPAddress;
    }

    private String getPublicIPAddressFromAPI() {
        // Get public IP of the player
        try {
            URL url = new URL("https://api.ipify.org?format=json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (Scanner scanner = new Scanner(conn.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                    return jsonObject.get("ip").getAsString();
                }
            } else {
                getLogger().warning("Failed to retrieve public IP. HTTP response code: " + conn.getResponseCode());
            }
        } catch (IOException e) {
            getLogger().warning("Failed to retrieve public IP. Error: " + e.getMessage());
        }
        return null;
    }

    private boolean checkWhitelist(String ipAddress) {
        // Find IP address in database
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + mysqlHostname + ":" + mysqlPort + "/" + mysqlDatabase,
                mysqlUsername, mysqlPassword)) {
            String query = "SELECT COUNT(*) FROM playerip_whitelisted WHERE ip_address = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, ipAddress);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

}
