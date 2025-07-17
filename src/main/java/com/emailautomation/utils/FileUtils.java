package com.emailautomation.utils;

import java.io.IOException;
import java.nio.file.*;

public class FileUtils {
    public static void deleteFile(String filePath) throws IOException {
        Files.deleteIfExists(Paths.get(filePath));
    }

    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

}