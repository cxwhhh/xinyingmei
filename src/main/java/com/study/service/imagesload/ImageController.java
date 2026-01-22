package com.study.service.imagesload;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/images")
@Slf4j
public class ImageController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Autowired
    private ImageService imageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) {
        try {
            String filePath = imageService.storeImage(file, userId);
            Map<String, String> response = new HashMap<>();
            response.put("filePath", filePath);
            response.put("message", "上传成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("")
    public ResponseEntity<List<ImageDTO>> getUserImages() {
        try {
            Long userId = getCurrentUserId();
            List<ImageDTO> images = imageService.getImagesByUserId(userId);
            return ResponseEntity.ok(images);
        } catch (Exception e) {
            log.error("获取用户图片列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{userId}/{filename:.+}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable Long userId,
            @PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir, "images", userId.toString(), filename);
            Resource resource = new UrlResource(Objects.requireNonNull(filePath.toUri()));

            if (resource.exists() && resource.isReadable()) {
                String contentType = determineContentType(filename);
                MediaType mediaType = MediaType.parseMediaType(Objects.requireNonNull(contentType));
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .body(resource);
            } else {
                log.error("文件不存在或不可读: {}", filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("文件访问失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String determineContentType(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            return "image/jpeg";
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "svg":
                return "image/svg+xml";
            case "webp":
                return "image/webp";
            default:
                return "image/jpeg";
        }
    }

    private Long getCurrentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null || attrs.getRequest() == null) {
            throw new RuntimeException("无法获取请求上下文");
        }
        HttpSession session = attrs.getRequest().getSession();
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return userId;
    }
}
