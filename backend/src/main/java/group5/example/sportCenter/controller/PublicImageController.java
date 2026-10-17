package group5.example.sportCenter.controller;

import group5.example.sportCenter.service.LocalImageStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/images")
public class PublicImageController {
    private final LocalImageStorageService storage;

    public PublicImageController(LocalImageStorageService storage) { this.storage = storage; }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        Resource image = storage.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storage.contentType(filename)))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(image);
    }
}
