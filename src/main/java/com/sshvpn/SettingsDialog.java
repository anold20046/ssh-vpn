package com.sshvpn;

import javax.swing.*;
import java.awt.*;

public class SettingsDialog extends JDialog {
    private JTextField ipField;
    private JSpinner portSpinner;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private ConfigManager configManager;
    private boolean saved = false;

    public SettingsDialog(JFrame parent, ConfigManager configManager) {
        super(parent, "Settings", true);
        this.configManager = configManager;

        initComponents();
        loadSettings();

        setSize(350, 250);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initComponents() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // IP Address
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("IP Address:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        ipField = new JTextField(20);
        panel.add(ipField, gbc);

        // Port
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Server Port:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        portSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
        panel.add(portSpinner, gbc);

        // Username
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        usernameField = new JTextField(20);
        panel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        passwordField = new JPasswordField(20);
        panel.add(passwordField, gbc);

        // Save button
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveSettings());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(saveButton);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadSettings() {
        ipField.setText(configManager.getIp());
        portSpinner.setValue(configManager.getPort());
        usernameField.setText(configManager.getUsername());
        passwordField.setText(configManager.getPassword());
    }

    private void saveSettings() {
        configManager.setIp(ipField.getText().trim());
        configManager.setPort((Integer) portSpinner.getValue());
        configManager.setUsername(usernameField.getText().trim());
        configManager.setPassword(new String(passwordField.getPassword()));
        configManager.saveConfig();

        saved = true;
        JOptionPane.showMessageDialog(this, "Successfully saved.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    public boolean isSaved() {
        return saved;
    }
}