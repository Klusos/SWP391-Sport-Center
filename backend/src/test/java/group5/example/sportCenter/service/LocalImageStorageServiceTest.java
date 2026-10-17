package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import group5.example.sportCenter.config.ImageStorageProperties;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import jakarta.persistence.Column;
import org.hibernate.annotations.Nationalized;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class LocalImageStorageServiceTest {
    @TempDir Path temp;
    private LocalImageStorageService storage;

    @BeforeEach
    void setUp() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setStorageDirectory(temp.toString());
        storage = new LocalImageStorageService(properties);
    }

    @Test
    void storesValidatedPngWithUniqueUntrustedFilenameAndCanLoadIt() throws Exception {
        byte[] png = png();
        var first = storage.store(new MockMultipartFile("file", "../../unsafe.exe", "text/plain", png));
        var second = storage.store(new MockMultipartFile("file", "same.png", "image/png", png));

        assertNotEquals(first.filename(), second.filename());
        assertTrue(first.imageUrl().matches("/api/public/images/[0-9a-f-]{36}\\.png"));
        assertEquals("image/png", first.contentType());
        assertArrayEquals(png, storage.load(first.filename()).getContentAsByteArray());
    }

    @Test
    void acceptsJpegAndWebpSignaturesWithValidImageStructure() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);
        assertEquals("image/jpeg", storage.store(new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg.toByteArray())).contentType());

        byte[] webp = minimalLosslessWebp();
        assertEquals("image/webp", storage.store(new MockMultipartFile("file", "photo.webp", "image/webp", webp)).contentType());
    }

    @Test
    void rejectsUnsupportedAndMalformedImageContent() {
        byte[] gif = "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> storage.store(new MockMultipartFile("file", "x.gif", "image/gif", gif)))
                .getStatusCode().value());

        byte[] fakePng = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> storage.store(new MockMultipartFile("file", "x.png", "image/png", fakePng)))
                .getStatusCode().value());
    }

    @Test
    void rejectsOversizedFilesAndTraversalNames() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> storage.store(new MockMultipartFile("file", "large.png", "image/png", oversized)))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> storage.load("../../application.properties")).getStatusCode().value());
    }

    @Test
    void removesOnlyFilesAddressedByItsOwnPublicUrlPrefix() throws Exception {
        var stored = storage.store(new MockMultipartFile("file", "x.png", "image/png", png()));
        storage.deleteStoredUrl("/api/public/images/../../application.properties");
        assertTrue(Files.exists(temp.resolve(stored.filename())));
        storage.deleteStoredUrl(stored.imageUrl());
        assertFalse(Files.exists(temp.resolve(stored.filename())));
    }

    @Test
    void entityMappingsDeclareNullableNationalizedImageUrlUpToFiveHundredCharacters() throws Exception {
        for (Class<?> entity : new Class<?>[] {Sport.class, Coach.class, Facility.class}) {
            var field = entity.getDeclaredField("imageUrl");
            var column = field.getAnnotation(Column.class);
            assertTrue(field.isAnnotationPresent(Nationalized.class), entity.getSimpleName());
            assertEquals("image_url", column.name());
            assertEquals(500, column.length());
            assertTrue(column.nullable());
        }
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] minimalLosslessWebp() {
        return new byte[] {'R','I','F','F',18,0,0,0,'W','E','B','P',
                'V','P','8','L',5,0,0,0,0x2f,0,0,0,0,0};
    }
}
