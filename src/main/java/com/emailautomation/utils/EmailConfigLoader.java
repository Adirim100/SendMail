package com.emailautomation.utils;

import com.emailautomation.models.EmailConfig;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class EmailConfigLoader
{
    private static final Logger logger = Logger.getLogger(EmailConfigLoader.class.getName());

    public static EmailConfig loadFromFile(String filePath) throws IOException
    {
        EmailConfig.Builder builder = EmailConfig.builder();

        // Force DOS encoding (IBM-862) for the config file
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), java.nio.charset.Charset.forName("IBM862")))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();

                // For fields that can be Hebrew, fix the direction
                switch (key)
                {
                    case "smtp_server":
                        builder.smtpServer(value); // English/host - no fix
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
                        builder.from(fixHebrewDirection(value));
                        break;
                    case "to":
                        String[] toAddresses = value.split(",");
                        List<String> toList = new ArrayList<>();
                        for (String email : toAddresses)
                        {
                            String trimmed = fixHebrewDirection(email.trim());
                            if (!trimmed.isEmpty())
                            {
                                toList.add(trimmed);
                            }
                        }
                        builder.to(toList);
                        break;
                    case "bcc":
                        String[] bccAddresses = value.split(",");
                        List<String> bccList = new ArrayList<>();
                        for (String email : bccAddresses)
                        {
                            String trimmed = fixHebrewDirection(email.trim());
                            if (!trimmed.isEmpty())
                            {
                                bccList.add(trimmed);
                            }
                        }
                        builder.bcc(bccList);
                        break;
                    case "fileandpath":
                        builder.attachmentPath(value);
                        break;
                    case "filename":
                        builder.attachmentName(fixHebrewDirection(value));
                        break;
                    case "subject":
                        builder.subject(fixHebrewDirection(value));
                        break;
                    case "body":
                        builder.body(fixHebrewDirection(value));
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
                        logger.info("Ignoring deprecated parameter: " + key);
                        break;
                    case "signaturefile":
                    case "signature_file":
                    case "signature":
                        builder.signatureFile(fixHebrewDirection(value));
                        break;
                    case "debug":
                        builder.debug(Boolean.parseBoolean(value) || "True".equalsIgnoreCase(value));
                        break;
                    case "reply_to":
                    case "replyto":
                        builder.replyTo(fixHebrewDirection(value));
                        break;
                    case "read_receipt":
                    case "readreceipt":
                        builder.readReceipt(Boolean.parseBoolean(value) || "True".equalsIgnoreCase(value));
                        break;
                    case "teamname":
                    case "team_name":
                        builder.teamName(fixHebrewDirection(value));
                        break;
                    case "htmltemplate":
                    case "html_template":
                        builder.htmlTemplate(fixHebrewDirection(value));
                        break;
                }
            }
        }

        // Load body from separate files - priority: .md > .txt > config
        String mdFilePath = filePath.replaceAll("\\.[^.]+$", ".md");
        String txtFilePath = filePath.replaceAll("\\.[^.]+$", ".txt");

        if (Files.exists(Paths.get(mdFilePath)))
        {
            logger.info("Loading Markdown email body from: " + mdFilePath);
            String mdBody = readFileWithEncoding(mdFilePath);
            builder.body(mdBody);
            builder.useHtml(true);  // Will convert to HTML
        }
        else if (Files.exists(Paths.get(txtFilePath)))
        {
            logger.info("Loading email body from: " + txtFilePath);
            String bodyFromFile = readFileWithEncoding(txtFilePath);
            builder.body(bodyFromFile);
        }

        // Load attachments from separate .list file if it exists
        String listFilePath = filePath.replaceAll("\\.[^.]+$", ".list");
        if (Files.exists(Paths.get(listFilePath)))
        {
            logger.info("Loading attachments from: " + listFilePath);
            List<String> attachmentLines = Files.readAllLines(Paths.get(listFilePath));
            List<String> attachmentPaths = new ArrayList<>();

            for (String line : attachmentLines)
            {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#"))
                {
                    if (line.contains("="))
                    {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2)
                        {
                            attachmentPaths.add(parts[1].trim());
                        }
                    }
                    else
                    {
                        attachmentPaths.add(line);
                    }
                }
            }

            if (!attachmentPaths.isEmpty())
            {
                builder.attachmentPaths(attachmentPaths);
            }
        }

        return builder.build();
    }

    private static String readFileWithEncoding(String filePath) throws IOException
    {
        // Try UTF-8, fallback to IBM-862, then Windows-1255 for body/attachments
        try
        {
            return Files.readString(Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (java.nio.charset.MalformedInputException e)
        {
            logger.info("UTF-8 decoding failed, trying IBM-862 encoding");
            try
            {
                String raw = Files.readString(Paths.get(filePath), java.nio.charset.Charset.forName("IBM862"));
                return fixHebrewDirection(raw);
            }
            catch (Exception ex)
            {
                logger.info("IBM-862 decoding failed, trying Windows-1255 encoding");
                String raw = Files.readString(Paths.get(filePath), java.nio.charset.Charset.forName("Windows-1255"));
                return fixHebrewDirection(raw);
            }
        }
    }

    // Hebrew direction fixer: reverses any line with Hebrew letters
    private static String fixHebrewDirection(String text)
    {
        StringBuilder fixed = new StringBuilder();
        for (String line : text.split("\\r?\\n"))
        {
            if (line.matches(".*[\\u0590-\\u05FF]+.*")) // Hebrew Unicode range
            {
                fixed.append(new StringBuilder(line).reverse());
            }
            else
            {
                fixed.append(line);
            }
            fixed.append(System.lineSeparator());
        }
        // Remove last line separator for clean output
        if (fixed.length() >= System.lineSeparator().length())
        {
            fixed.setLength(fixed.length() - System.lineSeparator().length());
        }
        return fixed.toString();
    }
}
