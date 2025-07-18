package com.emailautomation.services;

import com.emailautomation.models.EmailConfig;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class LogService {
    private static final Logger logger = Logger.getLogger(LogService.class.getName());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");

    public void logSuccess(String logFile, String message, EmailConfig config) {
        log(logFile, "SUCCESS", message, config);
    }

    public void logError(String logFile, String message, EmailConfig config) {
        log(logFile, "ERROR", message, config);
    }

    private void log(String logFile, String status, String message, EmailConfig config) {
        String logEntry = String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s%n",
                LocalDateTime.now().format(DATE_FORMAT),
                status,
                config.getAttachmentName() != null ? config.getAttachmentName() : "N/A",
                config.getAttachmentPath() != null ? config.getAttachmentPath() : "N/A",
                String.join(",", config.getTo()),
                String.join(",", config.getBcc()),
                message
        );

        // Determine log directory from attachment path or use current directory
        Path logPath;
        if (config.getAttachmentPath() != null && !config.getAttachmentPath().isEmpty()) {
            // Use the directory of the attachment file
            Path attachmentPath = Paths.get(config.getAttachmentPath());
            Path attachmentDir = attachmentPath.getParent();
            if (attachmentDir != null) {
                logPath = attachmentDir.resolve(logFile);
            } else {
                logPath = Paths.get(logFile);
            }
        } else if (!config.getAttachmentPaths().isEmpty()) {
            // If using multiple attachments, use the directory of the first one
            Path firstAttachmentPath = Paths.get(config.getAttachmentPaths().get(0));
            Path attachmentDir = firstAttachmentPath.getParent();
            if (attachmentDir != null) {
                logPath = attachmentDir.resolve(logFile);
            } else {
                logPath = Paths.get(logFile);
            }
        } else {
            // No attachment, use current directory
            logPath = Paths.get(logFile);
        }

        try {
            Files.write(logPath, logEntry.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.info("Log written to: " + logPath);
        } catch (IOException e) {
            logger.severe("Failed to write to log file: " + e.getMessage());
        }
    }
}