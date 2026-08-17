package com.eventrush.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaAssetService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path uploadDirectory;

    public MediaAssetService(@Value("${eventrush.media.upload-dir:./data/uploads}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public UploadedMedia saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("IMAGE_REQUIRED", HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST,
                    "图片不能超过 5 MB");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException("IMAGE_TYPE_NOT_SUPPORTED", HttpStatus.BAD_REQUEST,
                    "只支持 JPG、PNG 和 WebP 图片");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = uploadDirectory.resolve(fileName).normalize();
        if (!target.startsWith(uploadDirectory)) {
            throw new BusinessException("INVALID_IMAGE_PATH", HttpStatus.BAD_REQUEST, "图片路径无效");
        }
        try {
            Files.createDirectories(uploadDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException("IMAGE_UPLOAD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "图片保存失败，请稍后重试");
        }
        return new UploadedMedia("/media/" + fileName, file.getOriginalFilename(),
                file.getSize(), contentType);
    }

    public record UploadedMedia(String url, String originalName, long size, String contentType) {
    }
}
