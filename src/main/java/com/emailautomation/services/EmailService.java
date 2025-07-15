package com.emailautomation.services;

import com.emailautomation.models.EmailConfig;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;
import java.io.File;
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

            // Check if we have a logo to include
            String bodyContent = config.getBody();
            String logoContentId = null;

            if (config.getLogoPath() != null && !config.getLogoPath().isEmpty()) {
                // Generate unique content ID for the logo
                logoContentId = "logo_" + System.currentTimeMillis() + "@emailautomation";

                // Create HTML body with embedded logo
                String htmlBody = "<html><body>" +
                        "<img src='cid:" + logoContentId + "' style='max-width: 200px; display: block; margin-bottom: 20px;'/>" +
                        "<pre style='font-family: Arial, sans-serif; white-space: pre-wrap;'>" +
                        escapeHtml(bodyContent) +
                        "</pre></body></html>";

                bodyPart.setContent(htmlBody, "text/html; charset=UTF-8");

                // Add logo as embedded image
                MimeBodyPart logoPart = new MimeBodyPart();
                DataSource logoSource = new FileDataSource(config.getLogoPath());
                logoPart.setDataHandler(new DataHandler(logoSource));
                logoPart.setHeader("Content-ID", "<" + logoContentId + ">");
                logoPart.setDisposition(MimeBodyPart.INLINE);

                multipart.addBodyPart(bodyPart);
                multipart.addBodyPart(logoPart);
            } else {
                // No logo, just plain text
                bodyPart.setText(bodyContent, "UTF-8");
                multipart.addBodyPart(bodyPart);
            }

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
}