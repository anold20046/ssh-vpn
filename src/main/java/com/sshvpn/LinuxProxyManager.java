package com.sshvpn;

import java.io.IOException;

public class LinuxProxyManager implements ProxyManager {

    // GNOME proxy settings via gsettings
    private static final String GNOME_PROXY_MODE = "org.gnome.system.proxy mode";
    private static final String GNOME_PROXY_SOCKS_HOST = "org.gnome.system.proxy.socks host";
    private static final String GNOME_PROXY_SOCKS_PORT = "org.gnome.system.proxy.socks port";

    @Override
    public void enableProxy(String host, int port) {
        try {
            // Try GNOME settings first
            if (isGnomeAvailable()) {
                setGnomeProxy(host, port);
            }

            // Also set environment variables for current process
            System.setProperty("socksProxyHost", host);
            System.setProperty("socksProxyPort", String.valueOf(port));

            // Export for shell
            exportEnvVars(host, port);

        } catch (Exception e) {
            System.err.println("Error setting Linux proxy: " + e.getMessage());
        }
    }

    @Override
    public void disableProxy() {
        try {
            if (isGnomeAvailable()) {
                unsetGnomeProxy();
            }

            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");

        } catch (Exception e) {
            System.err.println("Error disabling Linux proxy: " + e.getMessage());
        }
    }

    private boolean isGnomeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "gsettings");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void setGnomeProxy(String host, int port) throws IOException, InterruptedException {
        runCommand("gsettings", "set", GNOME_PROXY_SOCKS_HOST, host);
        runCommand("gsettings", "set", GNOME_PROXY_SOCKS_PORT, String.valueOf(port));
        runCommand("gsettings", "set", GNOME_PROXY_MODE, "manual");
    }

    private void unsetGnomeProxy() throws IOException, InterruptedException {
        runCommand("gsettings", "set", GNOME_PROXY_MODE, "none");
    }

    private void runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
    }

    private void exportEnvVars(String host, int port) {
        // Write to a file that can be sourced
        String homeDir = System.getProperty("user.home");
        String envFile = homeDir + "/.ssh-vpn/env.sh";

        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(homeDir + "/.ssh-vpn"));
            String content = String.format(
                    "export http_proxy=\"socks5://%s:%d\"\n" +
                            "export https_proxy=\"socks5://%s:%d\"\n" +
                            "export ALL_PROXY=\"socks5://%s:%d\"\n",
                    host, port, host, port, host, port
            );
            java.nio.file.Files.writeString(java.nio.file.Paths.get(envFile), content);
        } catch (IOException e) {
            System.err.println("Error writing env file: " + e.getMessage());
        }
    }
}