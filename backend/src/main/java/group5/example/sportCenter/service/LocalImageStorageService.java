package group5.example.sportCenter.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import group5.example.sportCenter.config.ImageStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalImageStorageService {
    private static final long MAX_PIXELS = 40_000_000L;
    private final ImageStorageProperties properties;
    private final Path storageRoot;
    private final String normalizedUrlPrefix;

    public LocalImageStorageService(ImageStorageProperties properties) {
        this.properties = properties;
        try {
            Path configured = Path.of(properties.getStorageDirectory());
            this.storageRoot = (configured.isAbsolute() ? configured : Path.of(System.getProperty("user.dir")).resolve(configured))
                    .toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid image storage directory configuration", exception);
        }
        String prefix = properties.getPublicUrlPrefix();
        if (prefix == null || !prefix.startsWith("/") || prefix.contains("..")) {
            throw new IllegalArgumentException("Image public URL prefix must be an absolute application path");
        }
        this.normalizedUrlPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    public StoredImage store(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw badRequest("An image file is required");
        }
        if (upload.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds the configured size limit");
        }
        final byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload could not be read");
        }
        if (bytes.length > properties.getMaxFileSize().toBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds the configured size limit");
        }

        ImageFormat format = detectAndValidate(bytes);
        String filename = UUID.randomUUID() + "." + format.extension;
        Path destination = resolveFilename(filename);
        try {
            Files.createDirectories(storageRoot);
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // No database reference was written; a leftover partial file is an orphan.
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image storage is unavailable");
        }
        return new StoredImage(filename, normalizedUrlPrefix + "/" + filename, format.contentType);
    }

    public Resource load(String filename) {
        Path path = resolveFilename(filename);
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return new FileSystemResource(path);
    }

    public String contentType(String filename) {
        resolveFilename(filename);
        return switch (extension(filename)) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw badRequest("Unsupported image filename");
        };
    }

    public void deleteStoredUrl(String url) {
        if (url == null || !url.startsWith(normalizedUrlPrefix + "/")) return;
        String filename = url.substring(normalizedUrlPrefix.length() + 1);
        try {
            Files.deleteIfExists(resolveFilename(filename));
        } catch (IOException | ResponseStatusException ignored) {
            // Cleanup is best-effort; the database reference has already committed.
        }
    }

    private Path resolveFilename(String filename) {
        if (filename == null || !filename.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)")) {
            throw badRequest("Invalid image path");
        }
        Path resolved = storageRoot.resolve(filename).normalize();
        if (!resolved.startsWith(storageRoot) || !resolved.getFileName().toString().equals(filename)) {
            throw badRequest("Invalid image path");
        }
        return resolved;
    }

    private ImageFormat detectAndValidate(byte[] bytes) {
        if (startsWith(bytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            validateRaster(bytes, "png");
            return new ImageFormat("png", "image/png");
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) {
            validateRaster(bytes, "jpeg");
            return new ImageFormat("jpg", "image/jpeg");
        }
        if (isWebp(bytes)) return new ImageFormat("webp", "image/webp");
        throw badRequest("Only valid JPEG, PNG, or WebP images are supported");
    }

    private void validateRaster(byte[] bytes, String expectedFormat) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw badRequest("Image content is invalid");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw badRequest("Image content is invalid");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!reader.getFormatName().toLowerCase(Locale.ROOT).contains(expectedFormat)) {
                    throw badRequest("Image content does not match its format");
                }
                checkDimensions(reader.getWidth(0), reader.getHeight(0));
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw badRequest("Image content is invalid");
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw badRequest("Image content is invalid");
        }
    }

    private boolean isWebp(byte[] bytes) {
        if (bytes.length < 20 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WEBP")) return false;
        long riffLength = uint32le(bytes, 4);
        if (riffLength + 8 != bytes.length) return false;
        int offset = 12;
        boolean imageChunk = false;
        boolean validDimensions = false;
        while (offset < bytes.length) {
            if (bytes.length - offset < 8) return false;
            String chunk = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long chunkLength = uint32le(bytes, offset + 4);
            long dataStart = offset + 8L;
            long dataEnd = dataStart + chunkLength;
            if (dataEnd > bytes.length) return false;
            int start = (int) dataStart;
            int length = (int) chunkLength;
            if (chunk.equals("VP8X") && length >= 10) {
                long width = 1 + uint24le(bytes, start + 4);
                long height = 1 + uint24le(bytes, start + 7);
                checkDimensions(width, height);
                validDimensions = true;
            } else if (chunk.equals("VP8L") && length >= 5 && bytes[start] == 0x2f) {
                int b1 = bytes[start + 1] & 0xff, b2 = bytes[start + 2] & 0xff;
                int b3 = bytes[start + 3] & 0xff, b4 = bytes[start + 4] & 0xff;
                long width = 1 + (((b2 & 0x3f) << 8) | b1);
                long height = 1 + (((b4 & 0x0f) << 10) | (b3 << 2) | ((b2 & 0xc0) >> 6));
                checkDimensions(width, height);
                imageChunk = true;
                validDimensions = true;
            } else if (chunk.equals("VP8 ") && length >= 10 && (bytes[start + 3] & 0xff) == 0x9d
                    && (bytes[start + 4] & 0xff) == 0x01 && (bytes[start + 5] & 0xff) == 0x2a) {
                long width = uint16le(bytes, start + 6) & 0x3fff;
                long height = uint16le(bytes, start + 8) & 0x3fff;
                checkDimensions(width, height);
                imageChunk = true;
                validDimensions = true;
            }
            long next = dataEnd + (chunkLength & 1L);
            if (next > bytes.length) return false;
            offset = (int) next;
        }
        return offset == bytes.length && imageChunk && validDimensions;
    }

    private void checkDimensions(long width, long height) {
        if (width < 1 || height < 1 || width > 20_000 || height > 20_000 || width * height > MAX_PIXELS) {
            throw badRequest("Image dimensions are invalid or too large");
        }
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }
    private static boolean ascii(byte[] value, int offset, String expected) {
        return offset + expected.length() <= value.length
                && expected.equals(new String(value, offset, expected.length(), java.nio.charset.StandardCharsets.US_ASCII));
    }
    private static long uint32le(byte[] value, int i) {
        return ByteBuffer.wrap(value, i, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL;
    }
    private static long uint24le(byte[] value, int i) {
        return (value[i] & 0xffL) | ((value[i + 1] & 0xffL) << 8) | ((value[i + 2] & 0xffL) << 16);
    }
    private static int uint16le(byte[] value, int i) {
        return (value[i] & 0xff) | ((value[i + 1] & 0xff) << 8);
    }
    private static String extension(String filename) { return filename.substring(filename.lastIndexOf('.') + 1); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record StoredImage(String filename, String imageUrl, String contentType) {}
    private record ImageFormat(String extension, String contentType) {}
}
