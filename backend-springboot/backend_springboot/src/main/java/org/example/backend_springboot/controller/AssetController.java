package org.example.backend_springboot.controller;

import org.example.backend_springboot.service.ImageAssetService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final ImageAssetService imageAssetService;

    public AssetController(ImageAssetService imageAssetService) {
        this.imageAssetService = imageAssetService;
    }

    @GetMapping("/batches/{batchUuid}/images/{scope}/{index}")
    public ResponseEntity<Resource> batchImage(@PathVariable String batchUuid,
                                               @PathVariable String scope,
                                               @PathVariable int index) throws IOException {
        Resource resource = imageAssetService.loadBatchImage(batchUuid, scope, index);
        return imageResponse(resource);
    }

    @GetMapping("/questions/{questionId}/images/{scope}/{index}")
    public ResponseEntity<Resource> questionImage(@PathVariable Long questionId,
                                                  @PathVariable String scope,
                                                  @PathVariable int index) throws IOException {
        Resource resource = imageAssetService.loadQuestionImage(questionId, scope, index);
        return imageResponse(resource);
    }

    private ResponseEntity<Resource> imageResponse(Resource resource) throws IOException {
        MediaType mediaType = MediaType.IMAGE_PNG;
        if (resource instanceof FileSystemResource fileResource) {
            String filename = fileResource.getFilename() != null
                    ? fileResource.getFilename().toLowerCase() : "";
            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (filename.endsWith(".webp")) {
                mediaType = MediaType.parseMediaType("image/webp");
            } else if (filename.endsWith(".gif")) {
                mediaType = MediaType.IMAGE_GIF;
            }
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(resource.contentLength())
                .body(resource);
    }
}
