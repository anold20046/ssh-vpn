package com.sshvpn;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SshVpnService {
    private static final int LOCAL_PORT = 9000;
    private static final String LOCAL_HOST = "127.0.0.1";

    private final Consumer<String> onStatusChange;
    private final Consumer<String> onError;

    private SshClient sshClient;
    private ClientSession session;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Thread acceptThread;
    private volatile boolean running = false;

    public SshVpnService(Consumer<String> onStatusChange, Consumer<String> onError) {
        this.onStatusChange = onStatusChange;
        this.onError = onError;
    }

    public void connect(String host, int port, String username, String password) throws IOException {
        sshClient = SshClient.setUpDefaultClient();
        sshClient.start();

        updateStatus("Connecting to " + host + ":" + port + "...");

        session = sshClient.connect(username, host, port)
                .verify()
                .getSession();

        session.addPasswordIdentity(password);
        session.auth().verify();

        updateStatus("Authenticated. Starting SOCKS proxy...");

        startSocksProxy();

        running = true;
        updateStatus("Connected");
    }

    private void startSocksProxy() throws IOException {
        executor = Executors.newCachedThreadPool();

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(LOCAL_HOST, LOCAL_PORT));

        acceptThread = new Thread(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handleSocksClient(socket));
                } catch (IOException e) {
                    if (running) {
                        onError.accept("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void handleSocksClient(Socket clientSocket) {
        try {
            Socks5Handler handler = new Socks5Handler(clientSocket, session);
            handler.handle();
        } catch (Exception e) {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void disconnect() {
        running = false;

        updateStatus("Disconnecting...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        if (executor != null) {
            executor.shutdownNow();
        }

        try {
            if (session != null) {
                session.close();
            }
            if (sshClient != null) {
                sshClient.stop();
            }
        } catch (IOException ignored) {
        }

        updateStatus("Disconnected");
    }

    public boolean isConnected() {
        return running && session != null && session.isOpen();
    }

    private void updateStatus(String status) {
        if (onStatusChange != null) {
            onStatusChange.accept(status);
        }
    }

    /**
     * Handles a single SOCKS5 client connection, tunneling traffic through SSH.
     */
    private static final class Socks5Handler {
        private final Socket clientSocket;
        private final ClientSession session;

        Socks5Handler(Socket clientSocket, ClientSession session) {
            this.clientSocket = clientSocket;
            this.session = session;
        }

        void handle() throws IOException {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            // ---- SOCKS5 greeting ----
            int version = in.read();
            if (version != 5) {
                clientSocket.close();
                return;
            }

            int numMethods = in.read();
            if (numMethods > 0) {
                in.readNBytes(numMethods); // consume methods
            }

            // Respond: no authentication required
            out.write(new byte[]{5, 0});
            out.flush();

            // ---- SOCKS5 request ----
            int reqVersion = in.read(); // always 5
            if (reqVersion != 5) {
                clientSocket.close();
                return;
            }

            int command = in.read();
            in.read(); // reserved byte
            int addressType = in.read();

            String targetHost;
            int targetPort;

            switch (addressType) {
                case 1: { // IPv4
                    byte[] ipv4 = in.readNBytes(4);
                    targetHost = String.format("%d.%d.%d.%d",
                            ipv4[0] & 0xFF, ipv4[1] & 0xFF,
                            ipv4[2] & 0xFF, ipv4[3] & 0xFF);
                    break;
                }
                case 3: { // Domain name
                    int domainLen = in.read();
                    targetHost = new String(in.readNBytes(domainLen));
                    break;
                }
                case 4: { // IPv6
                    byte[] ipv6 = in.readNBytes(16);
                    targetHost = InetAddress.getByAddress(ipv6).getHostAddress();
                    break;
                }
                default:
                    clientSocket.close();
                    return;
            }

            targetPort = (in.read() << 8) | in.read();

            // Only CONNECT command is supported
            if (command != 1) {
                out.write(new byte[]{5, 7, 0, 1, 0, 0, 0, 0, 0, 0});
                out.flush();
                clientSocket.close();
                return;
            }

            try {
                // Create direct-tcpip channel through SSH
                ChannelDirectTcpip channel = session.createDirectTcpipChannel(
                        SshdSocketAddress.LOCALHOST_ADDRESS,
                        new SshdSocketAddress(targetHost, targetPort));

                channel.open().verify();

                // Send SOCKS success response
                out.write(new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
                out.flush();

                InputStream remoteIn = channel.getInvertedOut();
                OutputStream remoteOut = channel.getInvertedIn();
                // Upload: client -> remote
                Thread uploadThread = new Thread(() -> {
                    try {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            remoteOut.write(buffer, 0, n);
                            remoteOut.flush();
                        }
                    } catch (IOException ignored) {
                    } finally {
                        try {
                            channel.close();
                        } catch (IOException ignored) {
                        }
                    }
                });
                uploadThread.setDaemon(true);
                uploadThread.start();

                // Download: remote -> client
                byte[] buffer = new byte[8192];
                int n;
                while ((n = remoteIn.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    out.flush();
                }

            } catch (IOException e) {
                // SOCKS5 general failure
                out.write(new byte[]{5, 5, 0, 1, 0, 0, 0, 0, 0, 0});
                out.flush();
            } finally {
                clientSocket.close();
            }
        }
    }
}