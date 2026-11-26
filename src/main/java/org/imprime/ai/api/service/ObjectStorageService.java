package org.imprime.ai.api.service;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.Bucket;
import com.oracle.bmc.objectstorage.requests.GetBucketRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.transfer.UploadConfiguration;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AppConfig;
import org.imprime.ai.api.config.OciConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.oci.ObjectStorageKey;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorageService {
    private final Executor executor = Executors.newSingleThreadExecutor();

    private final AppConfig appConfig;
    private final ObjectStorage objectStorage;
    private final OciConfigProperties properties;
    private final UploadConfiguration uploadConfiguration = UploadConfiguration.builder()
            .allowMultipartUploads(true)
            .build();

    public void upload(
            ObjectStorageKey objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType) {

        String path = objectKey.getKey();
        if (path == null || path.isEmpty()) {
            log.error("Path is null or empty. Not uploading.");
            return;
        }

        final String fullPath = buildFullObjectName(path);

        UploadManager uploadManager = new UploadManager(
                objectStorage,
                uploadConfiguration
        );

        PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(properties.getObjectStorage().getNamespace())
                .bucketName(objectKey.getBucket())
                .objectName(fullPath)
                .contentLength(contentLength)
                .contentType(Objects.requireNonNullElse(contentType, MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .putObjectBody(inputStream)
                .build();

        UploadManager.UploadRequest uploadRequest = UploadManager.UploadRequest.builder(
                        inputStream,
                        contentLength
                )
                .build(request);

        uploadManager.upload(uploadRequest);
    }

    public void deleteAsync(ObjectStorageKey key) {
        ServiceContext ctx = ServiceContext.getContext();
        executor.execute(() -> {
            try {
                ServiceContext.copy(ctx);
                delete(key);
            } catch (Exception e) {
                log.error("Failed to asynchronously delete Object Storage object [{}]", key.getKey(), e);
            } finally {
                ServiceContext.clear();
            }
        });
    }

    public void delete(ObjectStorageKey key) {
        final String fullPath = buildFullObjectName(key.getKey());

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .namespaceName(properties.getObjectStorage().getNamespace())
                .bucketName(key.getBucket())
                .objectName(fullPath).build();

        objectStorage.deleteObject(request);
    }

    public String buildPublicObjectUrl(FileAsset asset) {
        if (asset == null || asset.getAssetUrl() == null || asset.getAssetUrl().isBlank()) {
            return null;
        }
        String assetUrl = asset.getAssetUrl().trim();
        if (isHttpUrl(assetUrl)) {
            return assetUrl;
        }
        OciConfigProperties.ObjectStorageConfig storageConfig = properties.getObjectStorage();
        if (storageConfig == null
                || isBlank(storageConfig.getRegionId())
                || isBlank(storageConfig.getNamespace())) {
            return assetUrl;
        }
        ObjectStorageKey storageKey = ObjectStorageKey.of(asset);
        String fullObjectName = buildFullObjectName(storageKey.getKey());
        return String.format(
                "https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s",
                storageConfig.getRegionId(),
                storageConfig.getNamespace(),
                storageKey.getBucket(),
                fullObjectName
        );
    }

    public PresignedDownloadUrl createPresignedDownloadUrl(FileAsset asset, Duration ttl) {
        if (asset == null || isBlank(asset.getAssetUrl())) {
            throw new IllegalArgumentException("FileAsset must have a valid assetUrl");
        }
        Duration effectiveTtl = ttl != null && !ttl.isNegative() && !ttl.isZero()
                ? ttl
                : Duration.ofMinutes(15);
        Instant expiresAt = Instant.now().plus(effectiveTtl);

        String rawUrl = asset.getAssetUrl().trim();
        OciConfigProperties.ObjectStorageConfig storageConfig = properties.getObjectStorage();
        if (isHttpUrl(rawUrl)
                || objectStorage == null
                || storageConfig == null
                || isBlank(storageConfig.getRegionId())
                || isBlank(storageConfig.getNamespace())) {
            return new PresignedDownloadUrl(buildPublicObjectUrl(asset), expiresAt);
        }

        ObjectStorageKey storageKey = ObjectStorageKey.of(asset);
        String fullObjectName = buildFullObjectName(storageKey.getKey());

        try {
            CreatePreauthenticatedRequestDetails details = CreatePreauthenticatedRequestDetails.builder()
                    .name("dl-" + UUID.randomUUID())
                    .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
                    .objectName(fullObjectName)
                    .timeExpires(Date.from(expiresAt))
                    .build();

            CreatePreauthenticatedRequestRequest request = CreatePreauthenticatedRequestRequest.builder()
                    .namespaceName(storageConfig.getNamespace())
                    .bucketName(storageKey.getBucket())
                    .createPreauthenticatedRequestDetails(details)
                    .build();

            CreatePreauthenticatedRequestResponse response = objectStorage.createPreauthenticatedRequest(request);
            String accessUri = response.getPreauthenticatedRequest().getAccessUri();
            String normalizedUri = accessUri != null && accessUri.startsWith("/") ? accessUri : "/" + Objects.toString(accessUri, "");
            String url = "https://objectstorage." + storageConfig.getRegionId() + ".oraclecloud.com" + normalizedUri;
            Instant parExpiresAt = response.getPreauthenticatedRequest().getTimeExpires() != null
                    ? response.getPreauthenticatedRequest().getTimeExpires().toInstant()
                    : expiresAt;
            return new PresignedDownloadUrl(url, parExpiresAt);
        } catch (Exception e) {
            log.error("Failed to create OCI preauthenticated download URL for asset [{}]", asset.getGuid(), e);
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
    }

    public String requirePrivateOrderBucket() {
        OciConfigProperties.ObjectStorageConfig config = properties.getObjectStorage();
        if (objectStorage == null || config == null || isBlank(config.getNamespace())
                || isBlank(config.getOrderModelsBucket())) throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        String bucketName = config.getOrderModelsBucket();
        try {
            Bucket bucket = objectStorage.getBucket(GetBucketRequest.builder()
                    .namespaceName(config.getNamespace()).bucketName(bucketName).build()).getBucket();
            if (bucket.getPublicAccessType() == Bucket.PublicAccessType.NoPublicAccess) return bucketName;
            log.error("Order model storage bucket allows public access");
        } catch (RuntimeException ex) {
            log.error("Failed to verify private order model storage", ex);
        }
        throw new BadGatewayException(MessageCd.BAD_GATEWAY);
    }

    public byte[] downloadPrivateOrderFile(FileAsset asset) {
        if (asset == null || isBlank(asset.getAssetUrl())) throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        String path = asset.getAssetUrl().trim();
        if (!isHttpUrl(path)) return downloadBytes(asset);

        OciConfigProperties.ObjectStorageConfig config = properties.getObjectStorage();
        if (config == null || isBlank(config.getNamespace()) || isBlank(config.getRegionId())) {
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
        try {
            URI uri = URI.create(path);
            String expectedHost = "objectstorage." + config.getRegionId() + ".oraclecloud.com";
            String prefix = "/n/" + config.getNamespace() + "/b/";
            if ("https".equalsIgnoreCase(uri.getScheme()) && expectedHost.equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getPath().startsWith(prefix)) {
                String[] location = uri.getPath().substring(prefix.length()).split("/o/", 2);
                if (location.length == 2 && !location[1].isBlank()
                        && ("models".equals(location[0]) || location[0].equals(config.getOrderModelsBucket()))) {
                    return downloadObject(asset, config, location[0], location[1]);
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
        throw new BadGatewayException(MessageCd.BAD_GATEWAY);
    }

    public byte[] downloadBytes(FileAsset asset) {
        if (asset == null || isBlank(asset.getAssetUrl())) {
            throw new IllegalArgumentException("FileAsset must have a valid assetUrl");
        }

        String rawUrl = asset.getAssetUrl().trim();
        if (isHttpUrl(rawUrl)) {
            try (InputStream stream = URI.create(rawUrl).toURL().openStream()) {
                return stream.readAllBytes();
            } catch (Exception e) {
                log.error("Failed to download external HTTP asset [{}]", asset.getGuid(), e);
                throw new BadGatewayException(MessageCd.BAD_GATEWAY);
            }
        }

        OciConfigProperties.ObjectStorageConfig storageConfig = properties.getObjectStorage();
        if (objectStorage == null || storageConfig == null || isBlank(storageConfig.getNamespace())) {
            log.error("OCI ObjectStorage is not configured to download asset [{}]", asset.getGuid());
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }

        ObjectStorageKey storageKey = ObjectStorageKey.of(asset);
        return downloadObject(asset, storageConfig, storageKey.getBucket(), buildFullObjectName(storageKey.getKey()));
    }

    private byte[] downloadObject(FileAsset asset, OciConfigProperties.ObjectStorageConfig storageConfig,
                                  String bucket, String objectName) {
        if (objectStorage == null || storageConfig == null) throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(storageConfig.getNamespace())
                .bucketName(bucket)
                .objectName(objectName)
                .build();

        try {
            GetObjectResponse response = objectStorage.getObject(request);
            try (InputStream stream = response.getInputStream()) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            log.error("Failed to download OCI Object Storage asset [{}]", asset.getGuid(), e);
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
    }

    private String buildFullObjectName(String key) {
        String envPrefix = appConfig != null && appConfig.getEnvironment() != null
                ? appConfig.getEnvironment().name().toLowerCase(Locale.ROOT)
                : "dev";
        return Path.of(envPrefix, key).toString().replace('\\', '/');
    }

    private boolean isHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PresignedDownloadUrl(String url, Instant expiresAt) {
    }
}
