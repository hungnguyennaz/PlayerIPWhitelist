package me.hungaz;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

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
        String ipAddress = event.getAddress().getHostAddress();
        if (isLocalIP(ipAddress)) {
            return;
        }

        boolean isWhitelisted = checkWhitelist(ipAddress);
        if (!isWhitelisted) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            getLogger().warning("IP " + ipAddress + " tried to join, but not whitelisted.");
        }
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

    private boolean isLocalIP(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
