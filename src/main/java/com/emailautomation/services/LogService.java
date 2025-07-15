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

        try {
            Files.write(Paths.get(logFile), logEntry.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.severe("Failed to write to log file: " + e.getMessage());
        }
    }
}