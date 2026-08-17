package com.eventrush.api;

import com.eventrush.service.MediaAssetService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/organizer/media")
class OrganizerMediaController {

    private final MediaAssetService mediaAssetService;

    OrganizerMediaController(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MediaAssetService.UploadedMedia uploadImage(@RequestParam("file") MultipartFile file) {
        return mediaAssetService.saveImage(file);
    }
}
