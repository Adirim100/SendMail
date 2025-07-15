package com.emailautomation.utils;

import com.emailautomation.models.EmailConfig;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class EmailConfigLoader {
    private static final Logger logger = Logger.getLogger(EmailConfigLoader.class.getName());

    public static EmailConfig loadFromFile(String filePath) throws IOException {
        EmailConfig.Builder builder = EmailConfig.builder();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key) {
                    case "smtp_server":
                        builder.smtpServer(value);
                        break;
                    case "port":
                        builder.port(Integer.parseInt(value));
                        break;
                    case "user":
                        builder.user(value);
                        break;
                    case "password":
                        builder.password(value);
                        break;
                    case "from_":
                        builder.from(value);
                        break;
                    case "to":
                        builder.to(Arrays.asList(value.split(",")));
                        break;
                    case "bcc":
                        builder.bcc(Arrays.asList(value.split(",")));
                        break;
                    case "fileandpath":
                        builder.attachmentPath(value);
                        break;
                    case "filename":
                        builder.attachmentName(value);
                        break;
                    case "subject":
                        builder.subject(value);
                        break;
                    case "body":
                        builder.body(value);
                        break;
                    case "cert":
                        builder.useTLS(Boolean.parseBoolean(value) || "True".equalsIgnoreCase(value));
                        break;
                    case "logo":
                    case "logo_path":
                        builder.logoPath(value);
                        break;
                    case "sendamail":
                    case "sendemail":
                        // Ignored - deprecated parameter
                        logger.info("Ignoring deprecated parameter: " + key);
                        break;
                }
            }
        }

        // Load body from separate .txt file if it exists
        String txtFilePath = filePath.replaceAll("\\.[^.]+$", ".txt");
        if (Files.exists(Paths.get(txtFilePath))) {
            logger.info("Loading email body from: " + txtFilePath);
            // Try to detect encoding, default to UTF-8
            String bodyFromFile = readFileWithEncoding(txtFilePath);
            builder.body(bodyFromFile);
        }

        // Load attachments from separate .list file if it exists
        String listFilePath = filePath.replaceAll("\\.[^.]+$", ".list");
        if (Files.exists(Paths.get(listFilePath))) {
            logger.info("Loading attachments from: " + listFilePath);
            List<String> attachmentLines = Files.readAllLines(Paths.get(listFilePath));
            List<String> attachmentPaths = new ArrayList<>();

            for (String line : attachmentLines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) { // Skip empty lines and comments
                    attachmentPaths.add(line);
                }
            }

            if (!attachmentPaths.isEmpty()) {
                builder.attachmentPaths(attachmentPaths);
            }
        }

        return builder.build();
    }

    private static String readFileWithEncoding(String filePath) throws IOException {
        // First try UTF-8
        try {
            return Files.readString(Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            // If UTF-8 fails, try IBM-862 (Hebrew DOS)
            logger.info("UTF-8 decoding failed, trying IBM-862 encoding");
            try {
                return Files.readString(Paths.get(filePath), java.nio.charset.Charset.forName("IBM862"));
            } catch (Exception ex) {
                // If IBM-862 fails, try Windows-1255 (Hebrew Windows)
                logger.info("IBM-862 decoding failed, trying Windows-1255 encoding");
                return Files.readString(Paths.get(filePath), java.nio.charset.Charset.forName("Windows-1255"));
            }
        }
    }
}