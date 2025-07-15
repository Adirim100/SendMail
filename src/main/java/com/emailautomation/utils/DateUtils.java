package com.emailautomation.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

public class DateUtils {
    private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    public static boolean isDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                LocalDate.parse(value.trim(), formatter);
                return true;
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return false;
    }

    public static LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        throw new IllegalArgumentException("Unable to parse date: " + value);
    }
}