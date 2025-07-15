package com.emailautomation.services;

import com.emailautomation.models.EmailConfig;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Service for sending emails via SMTP
 */
public class EmailService {
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

    /**
     * Send email via SMTP server
     */
    public void sendViaSMTP(EmailConfig config) throws MessagingException {
        // Set up mail server properties
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getSmtpServer());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        props.put("mail.smtp.auth", "true");

        // THIS IS WHERE CERT IS EXPLORED
        if (config.isUseTLS()) {  // <-- This checks the cert parameter
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", config.getSmtpServer());
            logger.info("Using STARTTLS encryption on port " + config.getPort());
        } else if (config.getPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            logger.info("Using SSL encryption on port 465");
        } else {
            logger.info("No encryption enabled on port " + config.getPort() + " - cert=false");
        }

        // Create session with authentication
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.getUser(), config.getPassword());
            }
        });

        try {
            // Create message
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.getFrom()));

            // Add recipients
            for (String recipient : config.getTo()) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient.trim()));
            }

            // Add BCC recipients
            for (String bcc : config.getBcc()) {
                if (!bcc.isEmpty()) {
                    message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc.trim()));
                }
            }

            // Set subject with UTF-8 encoding for Hebrew support
            message.setSubject(config.getSubject(), "UTF-8");

            // Create multipart message
            Multipart multipart = new MimeMultipart("related");

            // Create body part
            MimeBodyPart bodyPart = new MimeBodyPart();

            // Check if we have a logo to include or HTML is requested
            String bodyContent = config.getBody();
            String logoContentId = null;
            boolean useHtml = config.isUseHtml() || config.getLogoPath() != null || config.getSignatureFile() != null;

            // Load signature if specified
            String signatureContent = "";
            if (config.getSignatureFile() != null && !config.getSignatureFile().isEmpty()) {
                try {
                    signatureContent = Files.readString(Paths.get(config.getSignatureFile()));
                    logger.info("Loaded signature from: " + config.getSignatureFile());
                } catch (IOException e) {
                    logger.warning("Failed to load signature file: " + e.getMessage());
                }
            }

            if (useHtml) {
                String htmlBody;

                if (config.getLogoPath() != null && !config.getLogoPath().isEmpty()) {
                    // Generate unique content ID for the logo
                    logoContentId = "logo_" + System.currentTimeMillis() + "@emailautomation";

                    // Check if body already contains HTML tags
                    boolean isFullHtml = bodyContent.toLowerCase().contains("<html>") ||
                            bodyContent.toLowerCase().contains("<!doctype");

                    if (isFullHtml) {
                        // Insert logo after <body> tag
                        String logoImg = "<img src='cid:" + logoContentId + "' style='max-width: 200px; display: block; margin-bottom: 20px;'/>";
                        htmlBody = bodyContent.replaceFirst("(?i)<body[^>]*>", "$0" + logoImg);
                        // Insert signature before </body>
                        if (!signatureContent.isEmpty()) {
                            htmlBody = insertSignatureIntoHtml(htmlBody, signatureContent);
                        }
                    } else {
                        // Wrap in HTML with logo and signature
                        htmlBody = "<html><body style='font-family: Arial, sans-serif;'>" +
                                "<img src='cid:" + logoContentId + "' style='max-width: 200px; display: block; margin-bottom: 20px;'/>" +
                                bodyContent +
                                signatureContent +
                                "</body></html>";
                    }

                    // Add logo as embedded image
                    MimeBodyPart logoPart = new MimeBodyPart();
                    DataSource logoSource = new FileDataSource(config.getLogoPath());
                    logoPart.setDataHandler(new DataHandler(logoSource));
                    logoPart.setHeader("Content-ID", "<" + logoContentId + ">");
                    logoPart.setDisposition(MimeBodyPart.INLINE);
                    multipart.addBodyPart(logoPart);
                } else {
                    // HTML without logo - check if it's Markdown or HTML
                    if (bodyContent.toLowerCase().contains("<html>") ||
                            bodyContent.toLowerCase().contains("<!doctype")) {
                        // Already HTML - insert signature before </body>
                        if (!signatureContent.isEmpty()) {
                            bodyContent = insertSignatureIntoHtml(bodyContent, signatureContent);
                        }
                        htmlBody = bodyContent;
                    } else {
                        // Convert Markdown-style text to HTML and add signature
                        htmlBody = "<html><body style='font-family: Arial, sans-serif;'>" +
                                convertMarkdownToHtml(bodyContent) +
                                signatureContent +
                                "</body></html>";
                    }
                }

                bodyPart.setContent(htmlBody, "text/html; charset=UTF-8");
            } else {
                // Plain text
                bodyPart.setText(bodyContent, "UTF-8");
            }

            multipart.addBodyPart(bodyPart);

            // Add attachments
            List<String> allAttachments = new ArrayList<>();

            // If we have multiple attachments from .list file, use those
            if (!config.getAttachmentPaths().isEmpty()) {
                allAttachments.addAll(config.getAttachmentPaths());
            }
            // Otherwise, use single attachment from config if present
            else if (config.getAttachmentPath() != null && !config.getAttachmentPath().isEmpty()) {
                allAttachments.add(config.getAttachmentPath());
            }

            // Add all attachments to the email
            for (String attachmentPath : allAttachments) {
                try {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(attachmentPath);
                    attachmentPart.setDataHandler(new DataHandler(source));

                    // Extract filename from path
                    String filename = new File(attachmentPath).getName();
                    attachmentPart.setFileName(filename);

                    multipart.addBodyPart(attachmentPart);
                    logger.info("Added attachment: " + filename);
                } catch (Exception e) {
                    logger.warning("Failed to attach file: " + attachmentPath + " - " + e.getMessage());
                }
            }

            message.setContent(multipart);

            // Send message
            Transport.send(message);
            logger.info("Email sent successfully via SMTP");

        } catch (MessagingException e) {
            logger.severe("Failed to send email: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Send email via Outlook COM interface (Windows only)
     * This method requires JACOB library and only works on Windows
     */
    public void sendViaOutlook(EmailConfig config) throws Exception {
        // Check if JACOB is available
        try {
            Class.forName("com.jacob.com.ComThread");
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                    "Outlook integration is not available. JACOB library is required for Windows COM integration.");
        }

        throw new UnsupportedOperationException(
                "Outlook integration is currently disabled. To enable it, ensure JACOB library and native DLLs are properly configured.");

        /* Original Outlook implementation - uncomment if JACOB is properly set up
        ActiveXComponent outlook = null;
        Dispatch mailItem = null;

        try {
            // Initialize COM
            ComThread.InitSTA();

            // Create Outlook application instance
            outlook = new ActiveXComponent("Outlook.Application");

            // Create mail item
            mailItem = Dispatch.call(outlook, "CreateItem", 0).toDispatch();

            // Set email properties
            Dispatch.put(mailItem, "Subject", config.getSubject());
            Dispatch.put(mailItem, "Body", config.getBody());

            // Set HTML body if needed
            String htmlBody = String.format("<h2>%s</h2>", config.getSubject());
            Dispatch.put(mailItem, "HTMLBody", htmlBody);

            // Add recipients
            Dispatch.put(mailItem, "To", String.join(";", config.getTo()));

            if (!config.getBcc().isEmpty()) {
                Dispatch.put(mailItem, "BCC", String.join(";", config.getBcc()));
            }

            // Add attachment
            if (config.getAttachmentPath() != null && !config.getAttachmentPath().isEmpty()) {
                Dispatch attachments = Dispatch.get(mailItem, "Attachments").toDispatch();
                Dispatch.call(attachments, "Add", config.getAttachmentPath());
            }

            // Send email
            Dispatch.call(mailItem, "Send");
            logger.info("Email sent successfully via Outlook");

        } catch (Exception e) {
            logger.severe("Failed to send email via Outlook: " + e.getMessage());
            throw e;
        } finally {
            // Clean up COM resources
            if (mailItem != null) {
                mailItem.safeRelease();
            }
            if (outlook != null) {
                outlook.safeRelease();
            }
            ComThread.Release();
        }
        */
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private String formatHtmlBody(String text) {
        // Convert plain text to HTML with proper formatting
        return text
                // First escape HTML
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                // Convert line breaks to <br>
                .replace("\n", "<br>")
                // Convert tabs to spaces
                .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
                // Basic text formatting patterns
                .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")  // **bold**
                .replaceAll("\\*(.+?)\\*", "<em>$1</em>")  // *italic*
                .replaceAll("__(.+?)__", "<u>$1</u>");  // __underline__
    }

    private String convertMarkdownToHtml(String markdown) {
        String html = markdown
                // Escape HTML entities
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

                // Headers
                .replaceAll("^### (.+)$", "<h3>$1</h3>")
                .replaceAll("^## (.+)$", "<h2>$1</h2>")
                .replaceAll("^# (.+)$", "<h1>$1</h1>")

                // Lists (simple support)
                .replaceAll("^\\* (.+)$", "<li>$1</li>")
                .replaceAll("^- (.+)$", "<li>$1</li>")
                .replaceAll("(<li>.*</li>)\n(?!<li>)", "<ul>$1</ul>")

                // Text formatting
                .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")  // **bold**
                .replaceAll("\\*(.+?)\\*", "<em>$1</em>")  // *italic*
                .replaceAll("__(.+?)__", "<u>$1</u>")  // __underline__

                // Line breaks - convert double newline to paragraph
                .replaceAll("\n\n", "</p><p>")
                .replaceAll("\n", "<br>");

        return "<p>" + html + "</p>";
    }

    private String insertSignatureIntoHtml(String html, String signature) {
        // Insert signature before </body> tag
        int bodyCloseIndex = html.toLowerCase().lastIndexOf("</body>");
        if (bodyCloseIndex > 0) {
            return html.substring(0, bodyCloseIndex) + signature + html.substring(bodyCloseIndex);
        } else {
            // No </body> tag found, append at end
            return html + signature;
        }
    }
}