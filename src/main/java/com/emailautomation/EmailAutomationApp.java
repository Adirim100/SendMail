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
            boolean deleteParamFile = args.length >= 2 && "true".equalsIgnoreCase(args[1]);

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

            // Send email via SMTP
            emailService.sendViaSMTP(config);

            logService.logSuccess("filesendlist.log", "Email sent successfully", config);
            logService.logSuccess("sentlast.log", "Email sent successfully", config);
            notificationService.showSuccess("Email sent successfully!");

            // Delete parameter file if requested
            if (deleteParamFile) {
                FileUtils.deleteFile(paramFile);
            }

        } catch (Exception e) {
            logger.severe("Error sending email: " + e.getMessage());
            notificationService.showError("Email sending failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private void showUsageError() {
        String usage = "Usage: java -jar email-automation.jar <param-file> [delete-param-file]\n" +
                "Example: java -jar email-automation.jar email-config.txt true";
        notificationService.showError(usage, 5);
    }
}