package me.hungaz;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.Scanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class PlayerIPWhitelist extends JavaPlugin implements Listener {
    private String mysqlHostname;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String kickMessage;

    @Override
    public void onEnable() {
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
        mysqlHostname = getConfig().getString("mysql.hostname");
        mysqlPort = getConfig().getInt("mysql.port");
        mysqlDatabase = getConfig().getString("mysql.database");
        mysqlUsername = getConfig().getString("mysql.username");
        mysqlPassword = getConfig().getString("mysql.password");
        kickMessage = getConfig().getString("kick-message", "Mày là thằng nào?!");
    }

    private boolean testMySQLConnection() {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + mysqlHostname + ":" + mysqlPort + "/" + mysqlDatabase,
                mysqlUsername, mysqlPassword)) {
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createTableIfNotExists() {
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
        String publicIPAddress = getPublicIPAddress();
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

    private String getPublicIPAddress() {
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
