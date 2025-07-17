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

            // Set Reply-To address if specified
            if (config.getReplyTo() != null && !config.getReplyTo().isEmpty()) {
                message.setReplyTo(new Address[] { new InternetAddress(config.getReplyTo()) });
                logger.info("Reply-To address set to: " + config.getReplyTo());
            }

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

            // Request read receipt if enabled
            if (config.isReadReceipt()) {
                String receiptTo = config.getReplyTo() != null && !config.getReplyTo().isEmpty()
                        ? config.getReplyTo() : config.getFrom();
                message.setHeader("Disposition-Notification-To", receiptTo);
                message.setHeader("Return-Receipt-To", receiptTo);
                logger.info("Read receipt requested, will be sent to: " + receiptTo);
            }

            // Create multipart message
            Multipart multipart = new MimeMultipart("related");

            // Create body part
            MimeBodyPart bodyPart = new MimeBodyPart();

            // Generate email body content
            String emailBody = generateEmailBody(config, multipart);

            // Check if we successfully processed an HTML template
            boolean usingTemplate = (config.getHtmlTemplate() != null &&
                    !config.getHtmlTemplate().isEmpty() &&
                    Files.exists(Paths.get(config.getHtmlTemplate())));

            // Set content type based on whether we're using HTML template or not
            if (usingTemplate && !emailBody.equals(config.getBody())) {
                bodyPart.setContent(emailBody, "text/html; charset=UTF-8");
                logger.info("Using HTML template for email body");
            } else {
                // Fallback to original logic for non-template emails
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
                    String htmlBody = generateDefaultHtml(config, bodyContent, logoContentId, signatureContent, multipart);
                    bodyPart.setContent(htmlBody, "text/html; charset=UTF-8");
                } else {
                    bodyPart.setText(bodyContent, "UTF-8");
                }
            }

            multipart.addBodyPart(bodyPart);

            // Add attachments
            addAttachments(config, multipart);

            message.setContent(multipart);

            // Send message
            Transport.send(message);
            logger.info("Email sent successfully via SMTP");

        } catch (MessagingException e) {
            logger.severe("Failed to send email: " + e.getMessage());
            throw e;
        }
    }

    private String generateEmailBody(EmailConfig config, Multipart multipart) throws MessagingException {
        // Check if HTML template is specified
        if (config.getHtmlTemplate() != null && !config.getHtmlTemplate().isEmpty()) {
            String templateResult = processHtmlTemplate(config, multipart);
            if (templateResult != null) {
                return templateResult; // Successfully processed template
            }
            // If template processing failed, fall through to default logic
            logger.info("Template processing failed, using default HTML generation");
        }

        // If no template or template failed, return the body as-is (will be handled by fallback logic)
        return config.getBody();
    }

    private String processHtmlTemplate(EmailConfig config, Multipart multipart) throws MessagingException {
        try {
            // 1. Load the HTML template from the path specified in prm file
            String templatePath = config.getHtmlTemplate();

            // Check if template file exists
            if (!Files.exists(Paths.get(templatePath))) {
                logger.warning("HTML template file does not exist: " + templatePath + ". Falling back to default HTML generation.");
                return null; // Signal to use fallback
            }

            String htmlTemplate = Files.readString(Paths.get(templatePath));
            logger.info("Loaded HTML template from: " + templatePath);

            // 2. Replace {USER_MESSAGE} with content from .txt file (body from config)
            String userMessage = config.getBody();
            htmlTemplate = htmlTemplate.replace("{USER_MESSAGE}", userMessage);
            logger.info("Replaced {USER_MESSAGE} with body content");

            // 3. Replace other placeholders with values from prm file
            htmlTemplate = replacePlaceholders(htmlTemplate, config, multipart);

            return htmlTemplate;

        } catch (IOException e) {
            logger.warning("Failed to load HTML template: " + e.getMessage() + ". Falling back to default HTML generation.");
            return null; // Signal to use fallback
        }
    }

    private String replacePlaceholders(String htmlTemplate, EmailConfig config, Multipart multipart) throws MessagingException {
        // Replace common placeholders with values from EmailConfig

        // Team name
        htmlTemplate = htmlTemplate.replace("{TEAM_NAME}", config.getTeamName());

        // Email addresses
        htmlTemplate = htmlTemplate.replace("{FROM}", config.getFrom());
        htmlTemplate = htmlTemplate.replace("{SENDER_EMAIL}", config.getFrom()); // Add this line
        htmlTemplate = htmlTemplate.replace("{TO}", String.join(", ", config.getTo()));
        if (config.getReplyTo() != null) {
            htmlTemplate = htmlTemplate.replace("{REPLY_TO}", config.getReplyTo());
        }

        // Subject
        htmlTemplate = htmlTemplate.replace("{SUBJECT}", config.getSubject());

        // Handle logo if specified
        if (config.getLogoPath() != null && !config.getLogoPath().isEmpty()) {
            String logoContentId = "logo_" + System.currentTimeMillis() + "@emailautomation";
            htmlTemplate = htmlTemplate.replace("{LOGO}", "cid:" + logoContentId);

            // Add logo as embedded image to multipart
            addLogoToMultipart(config.getLogoPath(), logoContentId, multipart);
            logger.info("Added logo to email: " + config.getLogoPath());
        } else {
            // Remove logo placeholder if no logo specified
            htmlTemplate = htmlTemplate.replace("{LOGO}", "");
        }

        // Handle signature
        if (config.getSignatureFile() != null && !config.getSignatureFile().isEmpty()) {
            try {
                String signatureContent = Files.readString(Paths.get(config.getSignatureFile()));
                htmlTemplate = htmlTemplate.replace("{SIGNATURE}", signatureContent);
                logger.info("Added signature from: " + config.getSignatureFile());
            } catch (IOException e) {
                logger.warning("Failed to load signature file: " + e.getMessage());
                htmlTemplate = htmlTemplate.replace("{SIGNATURE}", "");
            }
        } else {
            htmlTemplate = htmlTemplate.replace("{SIGNATURE}", "");
        }

        // Additional common placeholders
        htmlTemplate = htmlTemplate.replace("{USER_EMAIL}", config.getUser());
        htmlTemplate = htmlTemplate.replace("{SMTP_SERVER}", config.getSmtpServer());

        // Date placeholders
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        htmlTemplate = htmlTemplate.replace("{DATE}", now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        htmlTemplate = htmlTemplate.replace("{TIME}", now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        htmlTemplate = htmlTemplate.replace("{DATETIME}", now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        // Attachment info
        if (config.getAttachmentName() != null) {
            htmlTemplate = htmlTemplate.replace("{ATTACHMENT_NAME}", config.getAttachmentName());
        } else {
            htmlTemplate = htmlTemplate.replace("{ATTACHMENT_NAME}", "");
        }

        logger.info("Replaced all placeholders in HTML template");
        return htmlTemplate;
    }

    private void addLogoToMultipart(String logoPath, String logoContentId, Multipart multipart) throws MessagingException {
        MimeBodyPart logoPart = new MimeBodyPart();
        DataSource logoSource = new FileDataSource(logoPath);
        logoPart.setDataHandler(new DataHandler(logoSource));
        logoPart.setHeader("Content-ID", "<" + logoContentId + ">");
        logoPart.setDisposition(MimeBodyPart.INLINE);
        multipart.addBodyPart(logoPart);
    }

    private String generateDefaultHtml(EmailConfig config, String bodyContent, String logoContentId,
                                       String signatureContent, Multipart multipart) throws MessagingException {
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
                // Add Misradit footer before </body>
                htmlBody = insertSignatureIntoHtml(htmlBody, getMisraditFooter(config.getTeamName()));
            } else {
                // Wrap in HTML with logo and signature
                htmlBody = "<html><body style='font-family: Arial, sans-serif;'>" +
                        "<img src='cid:" + logoContentId + "' style='max-width: 200px; display: block; margin-bottom: 20px;'/>" +
                        bodyContent +
                        signatureContent +
                        getMisraditFooter(config.getTeamName()) +
                        "</body></html>";
            }

            // Add logo as embedded image
            addLogoToMultipart(config.getLogoPath(), logoContentId, multipart);
        } else {
            // HTML without logo - check if it's Markdown or HTML
            if (bodyContent.toLowerCase().contains("<html>") ||
                    bodyContent.toLowerCase().contains("<!doctype")) {
                // Already HTML - insert signature before </body>
                if (!signatureContent.isEmpty()) {
                    bodyContent = insertSignatureIntoHtml(bodyContent, signatureContent);
                }
                // Add Misradit footer
                bodyContent = insertSignatureIntoHtml(bodyContent, getMisraditFooter(config.getTeamName()));
                htmlBody = bodyContent;
            } else {
                // Convert Markdown-style text to HTML and add signature
                htmlBody = "<html><body style='font-family: Arial, sans-serif;'>" +
                        convertMarkdownToHtml(bodyContent) +
                        signatureContent +
                        getMisraditFooter(config.getTeamName()) +
                        "</body></html>";
            }
        }

        return htmlBody;
    }

    private void addAttachments(EmailConfig config, Multipart multipart) {
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

    private String getMisraditFooter(String teamName) {
        return "<div style='margin-top: 50px; text-align: right; font-size: 12px; color: #666; font-family: Arial, sans-serif;'>" +
                "Sent by " + teamName + " with Misradit • נשלח על ידי " + teamName + " בעזרת משרדית" +
                "</div>";
    }
}