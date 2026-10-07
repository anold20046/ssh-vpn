package com.sshvpn;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.ssh-vpn";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.properties";

    private Properties properties;

    public ConfigManager() {
        properties = new Properties();
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                properties.load(input);
            } catch (IOException e) {
                System.err.println("Error loading config: " + e.getMessage());
            }
        }
    }

    public void saveConfig() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
                properties.store(output, "SSH VPN Configuration");
            }
        } catch (IOException e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    public String getIp() {
        return properties.getProperty("ip", "");
    }

    public void setIp(String ip) {
        properties.setProperty("ip", ip);
    }

    public int getPort() {
        String port = properties.getProperty("port", "22");
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return 22;
        }
    }

    public void setPort(int port) {
        properties.setProperty("port", String.valueOf(port));
    }

    public String getUsername() {
        return properties.getProperty("username", "");
    }

    public void setUsername(String username) {
        properties.setProperty("username", username);
    }

    public String getPassword() {
        return properties.getProperty("password", "");
    }

    public void setPassword(String password) {
        properties.setProperty("password", password);
    }

    public boolean isConfigured() {
        return !getIp().isEmpty() && !getUsername().isEmpty() && !getPassword().isEmpty();
    }
}