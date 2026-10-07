package com.sshvpn;

import java.io.IOException;

public class WindowsProxyManager implements ProxyManager {

    private static final String REG_PATH =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";

    @Override
    public void enableProxy(String host, int port) {
        try {
            // Set ProxyEnable to 1
            ProcessBuilder pb1 = new ProcessBuilder(
                    "reg", "add", REG_PATH, "/v", "ProxyEnable",
                    "/t", "REG_DWORD", "/d", "1", "/f"
            );
            pb1.redirectErrorStream(true);
            Process p1 = pb1.start();
            p1.waitFor();

            // Set ProxyServer
            String proxyServer = "socks5://" + host + ":" + port;
            ProcessBuilder pb2 = new ProcessBuilder(
                    "reg", "add", REG_PATH, "/v", "ProxyServer",
                    "/t", "REG_SZ", "/d", proxyServer, "/f"
            );
            pb2.redirectErrorStream(true);
            Process p2 = pb2.start();
            p2.waitFor();

            // Notify Windows of proxy change using rundll32
            ProcessBuilder pb3 = new ProcessBuilder(
                    "rundll32.exe", "wininet.dll,InternetSetOption", "39", "0"
            );
            pb3.redirectErrorStream(true);
            Process p3 = pb3.start();
            p3.waitFor();

            // Refresh
            ProcessBuilder pb4 = new ProcessBuilder(
                    "rundll32.exe", "wininet.dll,InternetSetOption", "37", "0"
            );
            pb4.redirectErrorStream(true);
            Process p4 = pb4.start();
            p4.waitFor();

        } catch (IOException | InterruptedException e) {
            System.err.println("Error setting Windows proxy: " + e.getMessage());
        }
    }

    @Override
    public void disableProxy() {
        try {
            ProcessBuilder pb1 = new ProcessBuilder(
                    "reg", "add", REG_PATH, "/v", "ProxyEnable",
                    "/t", "REG_DWORD", "/d", "0", "/f"
            );
            pb1.redirectErrorStream(true);
            Process p1 = pb1.start();
            p1.waitFor();

            ProcessBuilder pb2 = new ProcessBuilder(
                    "reg", "add", REG_PATH, "/v", "ProxyServer",
                    "/t", "REG_SZ", "/d", "", "/f"
            );
            pb2.redirectErrorStream(true);
            Process p2 = pb2.start();
            p2.waitFor();

        } catch (IOException | InterruptedException e) {
            System.err.println("Error disabling Windows proxy: " + e.getMessage());
        }
    }
}