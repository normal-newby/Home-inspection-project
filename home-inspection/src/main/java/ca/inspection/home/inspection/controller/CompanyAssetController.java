package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.CompanyAsset;
import ca.inspection.home.inspection.service.CompanyAssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/company-assets")
@CrossOrigin(origins = "*")
public class CompanyAssetController {
    @Autowired
    private CompanyAssetService companyAssetService;

    @PostMapping("/{key}")
    public ResponseEntity<?> uploadAsset(@PathVariable String key, @RequestBody MultipartFile file){
        return companyAssetService.uploadAsset(key, file);
    }

    @GetMapping
    public List<CompanyAsset> getAllAssets(){
        return companyAssetService.getAllAssets();
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> getAssetFile(@PathVariable String key){
        return companyAssetService.getAssetFile(key);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> deleteAsset(@PathVariable String key){
        return companyAssetService.deleteAssetFile(key);
    }
}
