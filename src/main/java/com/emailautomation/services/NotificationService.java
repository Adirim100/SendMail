package com.emailautomation.services;

import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.logging.Logger;
import java.net.URL;

public class NotificationService {
    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());
    private SystemTray tray;
    private TrayIcon trayIcon;
    private boolean systemTrayAvailable = false;

    public NotificationService() {
        initializeSystemTray();
    }

    private void initializeSystemTray() {
        // Check if system tray is supported
        if (!SystemTray.isSupported()) {
            logger.warning("System tray is not supported on this platform");
            return;
        }

        try {
            tray = SystemTray.getSystemTray();

            // Try to load icon from resources first, then file system
            Image image = loadIcon();
            if (image == null) {
                logger.warning("Could not load tray icon, using default image");
                // Create a simple default icon
                image = createDefaultIcon();
            }

            trayIcon = new TrayIcon(image, "Email Automation");
            trayIcon.setImageAutoSize(true);

            // Add the tray icon
            assert tray != null;
            tray.add(trayIcon);
            systemTrayAvailable = true;
            logger.info("System tray initialized successfully");

        } catch (AWTException e) {
            logger.severe("Failed to add tray icon: " + e.getMessage());
            trayIcon = null;
            systemTrayAvailable = false;
        } catch (Exception e) {
            logger.severe("Unexpected error initializing system tray: " + e.getMessage());
            trayIcon = null;
            systemTrayAvailable = false;
        }
    }

    private Image loadIcon() {
        // Try loading from resources
        URL iconURL = getClass().getResource("/icon.png");
        if (iconURL != null) {
            return Toolkit.getDefaultToolkit().getImage(iconURL);
        }

        // Try loading from file system
        try {
            return Toolkit.getDefaultToolkit().getImage("icon.png");
        } catch (Exception e) {
            return null;
        }
    }

    private Image createDefaultIcon() {
        // Create a simple 16x16 default icon
        Image image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillOval(2, 2, 12, 12);
        g2d.dispose();
        return image;
    }

    public void showSuccess(String message) {
        showNotification("Success", message, MessageType.INFO);
    }

    public void showError(String message) {
        showNotification("Error", message, MessageType.ERROR);
    }

    public void showError(String message, int durationSeconds) {
        showNotification("Error", message, MessageType.ERROR);
    }

    private void showNotification(String title, String message, MessageType type) {
        logger.info(title + ": " + message);

        // Always show console output
        if (type == MessageType.ERROR) {
            System.err.println("\n[ERROR] " + message);
        } else {
            System.out.println("\n[" + title.toUpperCase() + "] " + message);
        }

        // Check if running in headless mode
        if (GraphicsEnvironment.isHeadless()) {
            logger.info("Running in headless mode - GUI notifications disabled");
            return;
        }

        // Try system tray notification first
        if (systemTrayAvailable && trayIcon != null) {
            try {
                trayIcon.displayMessage(title, message, type);
                logger.info("System tray notification displayed");
            } catch (Exception e) {
                logger.warning("Failed to display tray notification: " + e.getMessage());
                showDialogNotification(title, message, type);
            }
        } else {
            // Fall back to dialog
            showDialogNotification(title, message, type);
        }
    }

    private void showDialogNotification(String title, String message, MessageType type) {
        // Use SwingUtilities to ensure thread safety
        SwingUtilities.invokeLater(() -> {
            try {
                int optionType = type == MessageType.ERROR ?
                        JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
                JOptionPane.showMessageDialog(null, message, title, optionType);
                logger.info("Dialog notification displayed");
            } catch (Exception e) {
                logger.severe("Failed to show dialog: " + e.getMessage());
            }
        });
    }

    public void cleanup() {
        if (tray != null && trayIcon != null) {
            try {
                tray.remove(trayIcon);
                logger.info("System tray cleaned up");
            } catch (Exception e) {
                logger.warning("Error during cleanup: " + e.getMessage());
            }
        }
    }
}