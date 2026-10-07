package com.sshvpn;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MainWindow extends JFrame {
    private JButton toggleButton;
    private JButton settingsButton;
    private JLabel statusLabel;
    private Timer statusTimer;
    private int seconds = 0;
    private boolean wasConnected = false;

    private ConfigManager configManager;
    private SshVpnService sshVpnService;
    private ProxyManager proxyManager;

    public MainWindow() {
        super("SSH VPN");
        configManager = new ConfigManager();
        sshVpnService = new SshVpnService(
                this::updateStatus,
                this::showError
        );

        // Select proxy manager based on OS
        proxyManager = createProxyManager();

        initComponents();
        setupWindowListener();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(320, 280);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private ProxyManager createProxyManager() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new WindowsProxyManager();
        } else {
            return new LinuxProxyManager();
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Toggle button
        toggleButton = new JButton("Connect");
        toggleButton.setFont(new Font("SansSerif", Font.PLAIN, 18));
        toggleButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        toggleButton.setMaximumSize(new Dimension(280, 70));
        toggleButton.setPreferredSize(new Dimension(280, 70));
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.addActionListener(e -> toggleConnection());

        // Status label
        statusLabel = new JLabel("Not Connected", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(220, 53, 69));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setMaximumSize(new Dimension(280, 60));
        statusLabel.setPreferredSize(new Dimension(280, 60));

        // Settings button
        settingsButton = new JButton("Open Settings");
        settingsButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        settingsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        settingsButton.setMaximumSize(new Dimension(200, 45));
        settingsButton.setPreferredSize(new Dimension(200, 45));
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsButton.addActionListener(e -> openSettings());

        // GitHub button
        JButton githubButton = new JButton("GitHub");
        githubButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        githubButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        githubButton.setMaximumSize(new Dimension(200, 35));
        githubButton.setPreferredSize(new Dimension(200, 35));
        githubButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        githubButton.addActionListener(e -> openGitHub());

        mainPanel.add(toggleButton);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(settingsButton);
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(githubButton);

        add(mainPanel, BorderLayout.CENTER);

        // Status check timer
        statusTimer = new Timer(1000, e -> checkStatus());
    }

    private void setupWindowListener() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (sshVpnService.isConnected()) {
                    int result = JOptionPane.showConfirmDialog(
                            MainWindow.this,
                            "Do you really wish to exit?\nThe connection will be stopped.",
                            "Exit Program?",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                    );
                    if (result == JOptionPane.YES_OPTION) {
                        disconnect();
                        dispose();
                        System.exit(0);
                    }
                } else {
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void toggleConnection() {
        toggleButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        if (sshVpnService.isConnected()) {
            disconnect();
        } else {
            connect();
        }

        setCursor(Cursor.getDefaultCursor());
        toggleButton.setEnabled(true);
    }

    private void connect() {
        if (!configManager.isConfigured()) {
            JOptionPane.showMessageDialog(this,
                    "Error: You should set SSH server settings first.",
                    "Error!",
                    JOptionPane.ERROR_MESSAGE);
            openSettings();
            return;
        }

        toggleButton.setText("Connecting...");

        new Thread(() -> {
            try {
                sshVpnService.connect(
                        configManager.getIp(),
                        configManager.getPort(),
                        configManager.getUsername(),
                        configManager.getPassword()
                );

                // Enable system proxy
                proxyManager.enableProxy("127.0.0.1", 9000);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setBackground(new Color(40, 167, 69));
                    statusLabel.setText("Connected      00:00:00");
                    toggleButton.setText("Disconnect");
                    seconds = 0;
                    statusTimer.start();
                });

            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Connection Error: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    toggleButton.setText("Connect");
                });
            }
        }).start();
    }

    private void disconnect() {
        toggleButton.setText("Disconnecting...");
        statusTimer.stop();

        new Thread(() -> {
            sshVpnService.disconnect();
            proxyManager.disableProxy();

            SwingUtilities.invokeLater(() -> {
                toggleButton.setText("Connect");
                statusLabel.setBackground(new Color(220, 53, 69));
                statusLabel.setText("Not Connected");
                seconds = 0;
            });
        }).start();
    }

    private void checkStatus() {
        boolean isConnected = sshVpnService.isConnected();

        if (!isConnected && wasConnected) {
            // Connection lost
            disconnect();
            showError("SSH VPN Disconnected unexpectedly!");
        } else if (isConnected) {
            seconds++;
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            int secs = seconds % 60;
            statusLabel.setText(String.format("Connected      %02d:%02d:%02d",
                    hours, minutes, secs));
        }

        wasConnected = isConnected;
    }

    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            if (status.equals("Connected")) {
                statusLabel.setBackground(new Color(40, 167, 69));
                statusLabel.setText("Connected      00:00:00");
            }
        });
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    message,
                    "Error",
                    JOptionPane.WARNING_MESSAGE);
        });
    }

    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(this, configManager);
        dialog.setVisible(true);
    }

    private void openGitHub() {
        try {
            Desktop.getDesktop().browse(
                    new URI("https://github.com/omidmousavi/csharp-ssh-vpn"));
        } catch (IOException | URISyntaxException e) {
            showError("Could not open browser: " + e.getMessage());
        }
    }
}