package org.imprime.ai.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oci")
public class OciConfigProperties {
    private ObjectStorageConfig objectStorage;

    @Data
    public static class ObjectStorageConfig {
        private String namespace;
        private String bucketName;
        private String configFile;
        private String regionId;
        private String profile;
    }
}
