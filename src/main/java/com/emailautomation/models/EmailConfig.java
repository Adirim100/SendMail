package com.emailautomation.models;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Email configuration model with builder pattern and validation
 */
public class EmailConfig {
    private String smtpServer;
    private int port;
    private String user;
    private String password;
    private String from;
    private List<String> to;
    private List<String> bcc;
    private String attachmentPath;
    private String attachmentName;
    private List<String> attachmentPaths;  // New field for multiple attachments
    private String subject;
    private String body;
    private boolean useTLS;
    private String logoPath;  // New field for logo/image

    private EmailConfig() {
        this.to = new ArrayList<>();
        this.bcc = new ArrayList<>();
        this.attachmentPaths = new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isValid() {
        return smtpServer != null && !smtpServer.isEmpty() &&
                port > 0 &&
                user != null && !user.isEmpty() &&
                password != null && !password.isEmpty() &&
                to != null && !to.isEmpty();
    }

    public String getValidationErrors() {
        List<String> errors = new ArrayList<>();

        if (smtpServer == null || smtpServer.isEmpty()) {
            errors.add("SMTP server is missing");
        }
        if (port <= 0) {
            errors.add("Port is invalid");
        }
        if (user == null || user.isEmpty()) {
            errors.add("User is missing");
        }
        if (password == null || password.isEmpty()) {
            errors.add("Password is missing");
        }
        if (to == null || to.isEmpty()) {
            errors.add("Recipient email address is missing");
        }

        return String.join(", ", errors);
    }

    // Getters
    public String getSmtpServer() { return smtpServer; }
    public int getPort() { return port; }
    public String getUser() { return user; }
    public String getPassword() { return password; }
    public String getFrom() { return from != null ? from : user; }
    public List<String> getTo() { return new ArrayList<>(to); }
    public List<String> getBcc() { return new ArrayList<>(bcc); }
    public String getAttachmentPath() { return attachmentPath; }
    public String getAttachmentName() { return attachmentName; }
    public List<String> getAttachmentPaths() { return new ArrayList<>(attachmentPaths); }
    public String getSubject() { return subject != null ? subject : ""; }
    public String getBody() { return body != null ? body : ""; }
    public boolean isUseTLS() { return useTLS; }
    public String getLogoPath() { return logoPath; }  // New getter

    @Override
    public String toString() {
        return new StringJoiner(", ", EmailConfig.class.getSimpleName() + "[", "]")
                .add("to=" + String.join(",", to))
                .add("subject='" + subject + "'")
                .add("attachment='" + attachmentName + "'")
                .toString();
    }

    public static class Builder {
        private final EmailConfig config;

        private Builder() {
            this.config = new EmailConfig();
        }

        public Builder smtpServer(String smtpServer) {
            config.smtpServer = smtpServer;
            return this;
        }

        public Builder port(int port) {
            config.port = port;
            return this;
        }

        public Builder user(String user) {
            config.user = user;
            return this;
        }

        public Builder password(String password) {
            config.password = password;
            return this;
        }

        public Builder from(String from) {
            config.from = from;
            return this;
        }

        public Builder to(List<String> to) {
            config.to = new ArrayList<>(to);
            return this;
        }

        public Builder addTo(String email) {
            config.to.add(email);
            return this;
        }

        public Builder bcc(List<String> bcc) {
            config.bcc = new ArrayList<>(bcc);
            return this;
        }

        public Builder addBcc(String email) {
            config.bcc.add(email);
            return this;
        }

        public Builder attachmentPath(String path) {
            config.attachmentPath = path;
            return this;
        }

        public Builder attachmentName(String name) {
            config.attachmentName = name;
            return this;
        }

        public Builder attachmentPaths(List<String> paths) {
            config.attachmentPaths = new ArrayList<>(paths);
            return this;
        }

        public Builder addAttachmentPath(String path) {
            config.attachmentPaths.add(path);
            return this;
        }

        public Builder subject(String subject) {
            config.subject = subject;
            return this;
        }

        public Builder body(String body) {
            config.body = body;
            return this;
        }

        public Builder useTLS(boolean useTLS) {
            config.useTLS = useTLS;
            return this;
        }

        public Builder logoPath(String logoPath) {
            config.logoPath = logoPath;
            return this;
        }

        public EmailConfig build() {
            return config;
        }
    }
}