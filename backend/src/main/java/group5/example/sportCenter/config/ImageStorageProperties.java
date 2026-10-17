package group5.example.sportCenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.images")
public class ImageStorageProperties {
    private String storageDirectory = "../uploads";
    private DataSize maxFileSize = DataSize.ofMegabytes(5);
    private String publicUrlPrefix = "/api/public/images";

    public String getStorageDirectory() { return storageDirectory; }
    public void setStorageDirectory(String storageDirectory) { this.storageDirectory = storageDirectory; }
    public DataSize getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }
    public String getPublicUrlPrefix() { return publicUrlPrefix; }
    public void setPublicUrlPrefix(String publicUrlPrefix) { this.publicUrlPrefix = publicUrlPrefix; }
}
