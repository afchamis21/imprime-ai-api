package org.imprime.ai.api.config;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class OciConfig {
    @Bean
    public AuthenticationDetailsProvider ociAuthenticationProvider(
            OciConfigProperties properties) throws IOException {

        return new ConfigFileAuthenticationDetailsProvider(
                properties.getObjectStorage().getConfigFile(),
                properties.getObjectStorage().getProfile()
        );
    }

    @Bean
    public ObjectStorage objectStorage(
            AuthenticationDetailsProvider authenticationDetailsProvider,
            OciConfigProperties properties) {
        return ObjectStorageClient.builder()
                .region(Region.fromRegionId(properties.getObjectStorage().getRegionId()))
                .build(authenticationDetailsProvider);
    }
}
