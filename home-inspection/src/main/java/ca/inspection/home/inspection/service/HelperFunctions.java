package ca.inspection.home.inspection.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class HelperFunctions {

    @Value("${app.upload-dir}")
    private String uploadDir;

    public Path getDirectory(){
        return Paths.get(uploadDir);
    }
    public Path getCompanyAssetDirectory() {
        return getDirectory().resolve("company");
    }

    public static String getFileExtension(String originalFileName){
        if (originalFileName == null || !originalFileName.contains(".")) return "";
        int dotIndex = originalFileName.lastIndexOf(".");
        if (dotIndex == originalFileName.length() - 1) return "";
        return originalFileName.substring(dotIndex);

    }

    public static String capitalizeFirstLetter(String s){
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
