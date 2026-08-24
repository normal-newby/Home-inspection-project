package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.CompanyAsset;
import ca.inspection.home.inspection.repository.CompanyAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CompanyAssetServiceTest {

    @Mock
    private CompanyAssetRepository companyAssetRepository;

    @Mock
    private HelperFunctions helperFunctions;

    @InjectMocks
    private CompanyAssetService companyAssetService;

    @TempDir
    Path tempDir;

    private MultipartFile mockFileThatWrites(String originalFileName, byte[] content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(originalFileName);
        doAnswer(invocation -> {
            File dest = invocation.getArgument(0);
            Files.write(dest.toPath(), content);
            return null;
        }).when(file).transferTo(any(File.class));
        return file;
    }

    private Path createJpegFile(Path dir, String fileName) throws IOException {
        Files.createDirectories(dir);
        BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        Path path = dir.resolve(fileName);
        ImageIO.write(img, "jpg", path.toFile());
        return path;
    }

    // UPLOAD ASSET

    @Test
    void uploadAsset_noExistingAsset_createsAndSavesNewAsset() throws IOException {
        String key = "logo";
        MultipartFile file = mockFileThatWrites("logo.png", "content".getBytes());

        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);
        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.empty());
        when(companyAssetRepository.save(any(CompanyAsset.class))).thenAnswer(res -> res.getArgument(0));

        ResponseEntity<?> result = companyAssetService.uploadAsset(key, file);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("Saved", true, "Key", key));

        ArgumentCaptor<CompanyAsset> captor = ArgumentCaptor.forClass(CompanyAsset.class);
        verify(companyAssetRepository).save(captor.capture());
        CompanyAsset saved = captor.getValue();
        assertThat(saved.getKey()).isEqualTo(key);
        assertThat(saved.getPath()).startsWith("asset_" + key + "_").endsWith(".png");
        assertThat(tempDir.resolve(saved.getPath())).exists();
    }

    @Test
    void uploadAsset_existingAsset_deletesOldFileAndUpdatesRecord() throws IOException {
        String key = "logo";
        Path oldFile = createJpegFile(tempDir, "asset_logo_old.jpg");

        CompanyAsset existing = new CompanyAsset();
        existing.setKey(key);
        existing.setPath("asset_logo_old.jpg");

        MultipartFile file = mockFileThatWrites("new.png", "content".getBytes());

        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);
        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.of(existing));
        when(companyAssetRepository.save(any(CompanyAsset.class))).thenAnswer(res -> res.getArgument(0));

        ResponseEntity<?> result = companyAssetService.uploadAsset(key, file);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(oldFile).doesNotExist();
        assertThat(existing.getPath()).startsWith("asset_logo_").endsWith(".png");
        assertThat(tempDir.resolve(existing.getPath())).exists();
        verify(companyAssetRepository).save(existing);
    }

    @Test
    void uploadAsset_transferFails_returnsBadRequest() throws IOException {
        String key = "logo";
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("logo.png");
        doThrow(new IOException("disk full")).when(file).transferTo(any(File.class));

        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        ResponseEntity<?> result = companyAssetService.uploadAsset(key, file);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("Can't upload asset");
        verify(companyAssetRepository, never()).save(any());
    }

    // GET ALL ASSETS

    @Test
    void getAllAssets_returnsAllFromRepository() {
        CompanyAsset a = new CompanyAsset();
        CompanyAsset b = new CompanyAsset();

        when(companyAssetRepository.findAll()).thenReturn(List.of(a, b));

        List<CompanyAsset> result = companyAssetService.getAllAssets();

        assertThat(result).containsExactly(a, b);
    }

    // GET ASSET FILE

    @Test
    void getAssetFile_assetExists_returnsOkWithResource() throws IOException {
        String key = "logo";
        createJpegFile(tempDir, "photo.jpg");

        CompanyAsset asset = new CompanyAsset();
        asset.setKey(key);
        asset.setPath("photo.jpg");

        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.of(asset));
        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        ResponseEntity<?> result = companyAssetService.getAssetFile(key);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(Resource.class);
        assertThat(((Resource) result.getBody()).exists()).isTrue();
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("photo.jpg");
    }

    @Test
    void getAssetFile_assetNotFound_returnsNoContent() {
        String key = "missing";
        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.empty());

        ResponseEntity<?> result = companyAssetService.getAssetFile(key);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void getAssetFile_unknownContentType_defaultsToOctetStream() throws IOException {
        String key = "blob";
        Files.createDirectories(tempDir);
        Path file = tempDir.resolve("file.unknownext");
        Files.write(file, new byte[]{1, 2, 3});

        CompanyAsset asset = new CompanyAsset();
        asset.setKey(key);
        asset.setPath("file.unknownext");

        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.of(asset));
        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        ResponseEntity<?> result = companyAssetService.getAssetFile(key);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    // DELETE ASSET FILE

    @Test
    void deleteAssetFile_assetExists_deletesFileAndRecord() throws IOException {
        String key = "logo";
        Path file = createJpegFile(tempDir, "photo.jpg");

        CompanyAsset asset = new CompanyAsset();
        asset.setKey(key);
        asset.setPath("photo.jpg");

        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.of(asset));
        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        ResponseEntity<?> result = companyAssetService.deleteAssetFile(key);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("Deleted asset with key: " + key);
        assertThat(file).doesNotExist();
        verify(companyAssetRepository).delete(asset);
    }

    @Test
    void deleteAssetFile_assetNotFound_returnsNoContent() {
        String key = "missing";
        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.empty());

        ResponseEntity<?> result = companyAssetService.deleteAssetFile(key);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(companyAssetRepository, never()).delete(any());
    }

    @Test
    void deleteAssetFile_fileMissingOnDisk_stillDeletesRecord() {
        String key = "logo";
        CompanyAsset asset = new CompanyAsset();
        asset.setKey(key);
        asset.setPath("does-not-exist.jpg");

        when(companyAssetRepository.findByKey(key)).thenReturn(Optional.of(asset));
        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        ResponseEntity<?> result = companyAssetService.deleteAssetFile(key);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(companyAssetRepository).delete(asset);
    }

    // TO BASE 64

    @Test
    void toBase64_assetExists_returnsDataUrl() throws IOException {
        createJpegFile(tempDir, "photo.jpg");

        CompanyAsset asset = new CompanyAsset();
        asset.setPath("photo.jpg");

        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        String result = companyAssetService.toBase64(asset);

        assertThat(result).startsWith("data:");
        assertThat(result).contains(";base64,");
    }

    @Test
    void toBase64_nullAsset_returnsNull() {
        String result = companyAssetService.toBase64(null);

        assertThat(result).isNull();
        verifyNoInteractions(helperFunctions);
    }

    @Test
    void toBase64_assetWithNullPath_returnsNull() {
        CompanyAsset asset = new CompanyAsset();
        asset.setPath(null);

        String result = companyAssetService.toBase64(asset);

        assertThat(result).isNull();
        verifyNoInteractions(helperFunctions);
    }

    @Test
    void toBase64_fileMissingOnDisk_returnsNull() {
        CompanyAsset asset = new CompanyAsset();
        asset.setPath("missing.jpg");

        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        String result = companyAssetService.toBase64(asset);

        assertThat(result).isNull();
    }

    @Test
    void toBase64_unknownContentType_defaultsToImageJpg() throws IOException {
        Files.createDirectories(tempDir);
        Path file = tempDir.resolve("blob.unknownext");
        Files.write(file, new byte[]{1, 2, 3});

        CompanyAsset asset = new CompanyAsset();
        asset.setPath("blob.unknownext");

        when(helperFunctions.getCompanyAssetDirectory()).thenReturn(tempDir);

        String result = companyAssetService.toBase64(asset);

        assertThat(result).startsWith("data:image/jpg;base64,");
    }

}
