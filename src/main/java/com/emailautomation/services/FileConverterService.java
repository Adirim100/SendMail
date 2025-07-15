package com.emailautomation.services;

import com.emailautomation.models.FileConversionConfig;
import com.emailautomation.utils.DateUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service for converting files between different formats
 */
public class FileConverterService {
    private static final Logger logger = Logger.getLogger(FileConverterService.class.getName());

    public void convert(FileConversionConfig config) throws IOException {
        switch (config.getInputType().toLowerCase()) {
            case "excel":
            case "xlsx":
            case "xls":
                convertFromExcel(config);
                break;
            case "csv":
                convertFromCSV(config);
                break;
            case "tsv":
                convertFromTSV(config);
                break;
            default:
                throw new IllegalArgumentException("Unsupported input type: " + config.getInputType());
        }
    }

    private void convertFromExcel(FileConversionConfig config) throws IOException {
        List<List<String>> data = readExcelFile(config.getInputFile());
        writeToOutput(data, config);
    }

    private void convertFromCSV(FileConversionConfig config) throws IOException {
        List<List<String>> data = readDelimitedFile(config.getInputFile(), ",");
        writeToOutput(data, config);
    }

    private void convertFromTSV(FileConversionConfig config) throws IOException {
        List<List<String>> data = readDelimitedFile(config.getInputFile(), "\t");
        writeToOutput(data, config);
    }

    private List<List<String>> readExcelFile(String filePath) throws IOException {
        List<List<String>> data = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(filePath)) {
            Workbook workbook;

            if (filePath.toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream);
            } else {
                workbook = new HSSFWorkbook(inputStream);
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                List<String> rowData = new ArrayList<>();
                for (Cell cell : row) {
                    rowData.add(formatter.formatCellValue(cell));
                }
                data.add(rowData);
            }

            workbook.close();
        }

        return data;
    }

    private List<List<String>> readDelimitedFile(String filePath, String delimiter) throws IOException {
        List<List<String>> data = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(delimiter);
                List<String> rowData = new ArrayList<>();
                for (String value : values) {
                    rowData.add(value.trim());
                }
                data.add(rowData);
            }
        }

        return data;
    }

    private void writeToOutput(List<List<String>> data, FileConversionConfig config) throws IOException {
        String delimiter = getDelimiter(config.getOutputType());
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(config.getDateFormat());

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(config.getOutputFile()), StandardCharsets.UTF_8))) {

            for (List<String> row : data) {
                boolean hasDate = false;
                List<String> processedRow = new ArrayList<>();

                for (int i = 0; i < row.size(); i++) {
                    String value = row.get(i);

                    // Process date in specified column
                    if (i == config.getColumnIndex() && DateUtils.isDate(value)) {
                        LocalDate date = DateUtils.parseDate(value);
                        value = date.format(dateFormatter);
                        hasDate = true;
                    }

                    processedRow.add(value);
                }

                // Write row if it meets the criteria
                if (!config.isMustHaveDate() || hasDate) {
                    writer.println(String.join(delimiter, processedRow));
                }
            }
        }

        logger.info("File converted successfully: " + config.getOutputFile());
    }

    private String getDelimiter(String outputType) {
        switch (outputType.toLowerCase()) {
            case "csv":
                return ",";
            case "tsv":
            case "txt":
                return "\t";
            case "pipe":
                return "|";
            default:
                return "\t";
        }
    }
}