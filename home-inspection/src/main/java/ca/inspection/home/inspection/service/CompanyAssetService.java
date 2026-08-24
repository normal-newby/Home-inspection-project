package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.CompanyAsset;
import ca.inspection.home.inspection.repository.CompanyAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class CompanyAssetService {
    @Autowired
    private CompanyAssetRepository companyAssetRepository;

    @Autowired
    private HelperFunctions helperFunctions;

    public ResponseEntity<?> uploadAsset(String key, MultipartFile file){
        try {
            Path directory = helperFunctions.getCompanyAssetDirectory();
            Files.createDirectories(directory);

            String extension = HelperFunctions.getFileExtension(file.getOriginalFilename());
            String fileName = "asset_" + key + "_" + UUID.randomUUID() + extension;
            Path path = directory.resolve(fileName);
            file.transferTo(path.toFile());

            CompanyAsset asset = companyAssetRepository.findByKey(key)
                    .orElseGet(CompanyAsset::new);

            if (asset.getPath() != null){
                Files.deleteIfExists(directory.resolve(asset.getPath()));
            }

            asset.setKey(key);
            asset.setPath(fileName);
            companyAssetRepository.save(asset);

            log.info("Uploaded company asset key={}", key);
            return ResponseEntity.ok(Map.of("Saved", true, "Key", key));

        } catch (Exception e){
            log.error("Failed to upload company asset key={}", key, e);
            return ResponseEntity.badRequest().body("Can't upload asset");
        }
    }

    public List<CompanyAsset> getAllAssets(){
        return companyAssetRepository.findAll();
    }

    public ResponseEntity<?> getAssetFile(String key){
        try {
            CompanyAsset asset = companyAssetRepository.findByKey(key)
                    .orElse(null);
            if (asset == null) return ResponseEntity.noContent().build();

            Path path = helperFunctions.getCompanyAssetDirectory().resolve(asset.getPath());
            Resource resource = new FileSystemResource(path.toFile());
            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" +
                            path.getFileName() + "\"")
                    .body(resource);

        } catch (Exception e){
            log.error("Failed to read company asset key={}", key, e);
            return ResponseEntity.badRequest().body("Cannot get asset file");
        }
    }

    public ResponseEntity<?> deleteAssetFile(String key){
        try {
            CompanyAsset asset = companyAssetRepository.findByKey(key)
                    .orElse(null);
            if (asset == null) return ResponseEntity.noContent().build();
            Files.deleteIfExists(helperFunctions.getCompanyAssetDirectory().resolve(asset.getPath()));
            companyAssetRepository.delete(asset);
            log.info("Deleted company asset key={}", key);
            return ResponseEntity.ok("Deleted asset with key: " + key);
        } catch (Exception e){
            log.error("Failed to delete company asset key={}", key, e);
            return ResponseEntity.badRequest().body("Could not delete asset with key: " + key);
        }
    }

    public String toBase64(CompanyAsset asset){
        try {
            if (asset == null || asset.getPath() == null) return null;

            Path path = helperFunctions.getCompanyAssetDirectory().resolve(asset.getPath());
            if (!Files.exists(path)) return null;

            byte[] bytes = Files.readAllBytes(path);
            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "image/jpg";

            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e){
            log.warn("Failed to encode company asset path={}", asset.getPath(), e);
            return null;
        }
    }
}
