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

            // FORCE ADD MISRADIT FOOTER - ALWAYS
            logger.info("*** FORCING MISRADIT FOOTER ***");
            String misraditFP = "Sent with Misradit - נשלח בעזרת משרדית";  // Misradit fingerprint

            // Check if we successfully processed an HTML template
            boolean usingTemplate = (config.getHtmlTemplate() != null &&
                    !config.getHtmlTemplate().isEmpty() &&
                    Files.exists(Paths.get(config.getHtmlTemplate())));

            if (usingTemplate && !emailBody.equals(config.getBody())) {
                // Using HTML template - add footer as HTML
                if (!emailBody.contains("Sent with Misradit")) {
                    String htmlFooter = "<div style='margin-top: 30px; font-size: 12px; color: #666;'>" + misraditFP + "</div>";
                    if (emailBody.toLowerCase().contains("</body>")) {
                        emailBody = emailBody.replace("</body>", htmlFooter + "</body>");
                    } else {
                        emailBody = emailBody + htmlFooter;
                    }
                    logger.info("*** ADDED FOOTER TO HTML TEMPLATE ***");
                }
                bodyPart.setContent(emailBody, "text/html; charset=UTF-8");
                logger.info("*** SET HTML TEMPLATE CONTENT ***");
            } else {
                // Fallback mode - handle both HTML and plain text
                String bodyContent = config.getBody();
                boolean useHtml = config.isUseHtml() || config.getLogoPath() != null || config.getSignatureFile() != null;

                if (useHtml) {
                    // HTML mode - create simple HTML with footer
                    String htmlBody = "<html><body style='font-family: Arial, sans-serif;'>" +
                            convertTextToHtml(bodyContent) +
                            "<div style='margin-top: 30px; font-size: 12px; color: #666;'>" + misraditFP + "</div>" +
                            "</body></html>";
                    bodyPart.setContent(htmlBody, "text/html; charset=UTF-8");
                    logger.info("*** SET HTML FALLBACK CONTENT WITH FOOTER ***");
                } else {
                    // Plain text mode
                    String plainTextBody = bodyContent + "\n\n" + misraditFP;
                    bodyPart.setText(plainTextBody, "UTF-8");
                    logger.info("*** SET PLAIN TEXT CONTENT WITH FOOTER ***");
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
            userMessage = convertTextToHtml(userMessage); // Convert to HTML format
            htmlTemplate = htmlTemplate.replace("{USER_MESSAGE}", userMessage);
            logger.info("Replaced {USER_MESSAGE} with properly formatted HTML content");

            // 3. Replace other placeholders with values from prm file
            htmlTemplate = replacePlaceholders(htmlTemplate, config, multipart);

            return htmlTemplate;

        } catch (IOException e) {
            logger.warning("Failed to load HTML template: " + e.getMessage() + ". Falling back to default HTML generation.");
            return null; // Signal to use fallback
        }
    }

    private String replacePlaceholders(String htmlTemplate, EmailConfig config, Multipart multipart) throws MessagingException {
        // Convert plain text body to HTML-friendly format for {USER_MESSAGE}
        String userMessage = config.getBody();
        userMessage = convertTextToHtml(userMessage);

        // Replace common placeholders with values from EmailConfig

        // User message with proper HTML formatting
        htmlTemplate = htmlTemplate.replace("{USER_MESSAGE}", userMessage);

        // Team name
        htmlTemplate = htmlTemplate.replace("{TEAM_NAME}", config.getTeamName());

        // Email addresses
        htmlTemplate = htmlTemplate.replace("{FROM}", config.getFrom());
        htmlTemplate = htmlTemplate.replace("{SENDER_EMAIL}", config.getFrom());
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

    private String convertTextToHtml(String text) {
        if (text == null) return "";

        // Convert plain text to HTML with proper line breaks
        return text
                // First escape HTML special characters
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                // Convert line breaks to HTML
                .replace("\r\n", "<br>")  // Windows line endings
                .replace("\n", "<br>")    // Unix line endings
                .replace("\r", "<br>");   // Old Mac line endings
    }

    private void addLogoToMultipart(String logoPath, String logoContentId, Multipart multipart) throws MessagingException {
        MimeBodyPart logoPart = new MimeBodyPart();
        DataSource logoSource = new FileDataSource(logoPath);
        logoPart.setDataHandler(new DataHandler(logoSource));
        logoPart.setHeader("Content-ID", "<" + logoContentId + ">");
        logoPart.setDisposition(MimeBodyPart.INLINE);
        multipart.addBodyPart(logoPart);
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
}