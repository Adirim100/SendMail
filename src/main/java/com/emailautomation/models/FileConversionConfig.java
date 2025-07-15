package com.emailautomation.models;

public class FileConversionConfig {
    private final String inputFile;
    private final String outputFile;
    private final String inputType;
    private final String outputType;
    private final boolean mustHaveDate;
    private final String dateFormat;
    private final int columnIndex;

    private FileConversionConfig(Builder builder) {
        this.inputFile = builder.inputFile;
        this.outputFile = builder.outputFile != null ? builder.outputFile : builder.inputFile + "." + builder.outputType;
        this.inputType = builder.inputType;
        this.outputType = builder.outputType;
        this.mustHaveDate = builder.mustHaveDate;
        this.dateFormat = builder.dateFormat;
        this.columnIndex = builder.columnIndex;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getInputFile() { return inputFile; }
    public String getOutputFile() { return outputFile; }
    public String getInputType() { return inputType; }
    public String getOutputType() { return outputType; }
    public boolean isMustHaveDate() { return mustHaveDate; }
    public String getDateFormat() { return dateFormat; }
    public int getColumnIndex() { return columnIndex; }

    public static class Builder {
        private String inputFile;
        private String outputFile;
        private String inputType = "excel";
        private String outputType = "tsv";
        private boolean mustHaveDate = false;
        private String dateFormat = "dd/MM/yyyy";
        private int columnIndex = 0;

        public Builder inputFile(String inputFile) {
            this.inputFile = inputFile;
            return this;
        }

        public Builder outputFile(String outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        public Builder outputType(String outputType) {
            this.outputType = outputType;
            return this;
        }

        public Builder mustHaveDate(boolean mustHaveDate) {
            this.mustHaveDate = mustHaveDate;
            return this;
        }

        public Builder dateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        public Builder columnIndex(int columnIndex) {
            this.columnIndex = columnIndex;
            return this;
        }

        public FileConversionConfig build() {
            return new FileConversionConfig(this);
        }
    }
}