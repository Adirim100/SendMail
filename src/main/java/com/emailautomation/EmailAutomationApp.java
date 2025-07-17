package com.emailautomation;

import com.emailautomation.services.*;
import com.emailautomation.models.*;
import com.emailautomation.utils.*;
import java.util.logging.Logger;

/**
 * Simple email sending application
 */
public class EmailAutomationApp {
    private static final Logger logger = Logger.getLogger(EmailAutomationApp.class.getName());

    private final EmailService emailService;
    private final NotificationService notificationService;
    private final LogService logService;

    public EmailAutomationApp() {
        this.emailService = new EmailService();
        this.notificationService = new NotificationService();
        this.logService = new LogService();
    }

    public static void main(String[] args) {
        EmailAutomationApp app = new EmailAutomationApp();
        app.run(args);
    }

    private void run(String[] args) {
        if (args.length == 0) {
            showUsageError();
            System.exit(1);
        }

        try {
            String paramFile = args[0];

            logger.info("Loading email configuration from: " + paramFile);

            // Load email configuration
            EmailConfig config = EmailConfigLoader.loadFromFile(paramFile);

            // Validate configuration
            if (!config.isValid()) {
                String error = "Invalid email configuration: " + config.getValidationErrors();
                logService.logError("filesendlist.log", error, config);
                logService.logError("sentlast.log", error, config);
                notificationService.showError("Email configuration error!");
                System.exit(1);
            }

            logger.info("Sending email to: " + String.join(",", config.getTo()));
            if (config.isDebug()) {
                logger.info("Debug mode is ON - files will be preserved");
            }

            // Send email via SMTP
            emailService.sendViaSMTP(config);

            logService.logSuccess("filesendlist.log", "Email sent successfully", config);
            logService.logSuccess("sentlast.log", "Email sent successfully", config);
            notificationService.showSuccess("Email sent successfully!");

            // Wait a bit to ensure notification is displayed
            try {
                Thread.sleep(2000); // 2 seconds
            } catch (InterruptedException e) {
                // Ignore
            }

            // Delete parameter file only if debug=false
            if (!config.isDebug()) {
                FileUtils.deleteFile(paramFile);
                logger.info("Parameter file deleted: " + paramFile);

                // Also delete associated files
                String baseFileName = paramFile.replaceAll("\\.[^.]+$", "");
                deleteIfExists(baseFileName + ".txt");
                deleteIfExists(baseFileName + ".html");
                deleteIfExists(baseFileName + ".list");
            } else {
                logger.info("Debug mode: keeping all files");
            }

            // Clean up notification service to allow exit
            notificationService.cleanup();

            // Force exit
            System.exit(0);

        } catch (Exception e) {
            logger.severe("Error sending email: " + e.getMessage());
            e.printStackTrace(); // This will show the full error stack trace
            notificationService.showError("Email sending failed: " + e.getMessage());
            notificationService.cleanup();
            System.exit(1);
        }
    }

    private void showUsageError() {
        String usage = "Usage: java -jar email-automation.jar <param-file>\n" +
                "Example: java -jar email-automation.jar email-config.txt";
        notificationService.showError(usage, 5);
    }

    private void deleteIfExists(String filePath) {
        try {
            if (FileUtils.fileExists(filePath)) {
                FileUtils.deleteFile(filePath);
                logger.info("Deleted: " + filePath);
            }
        } catch (Exception e) {
            logger.warning("Could not delete " + filePath + ": " + e.getMessage());
        }
    }
}