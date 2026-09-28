package org.imprime.ai.api.service;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.transfer.UploadConfiguration;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AppConfig;
import org.imprime.ai.api.config.OciConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.oci.ObjectStorageKey;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
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

        final String fullPath = Path.of(appConfig.getEnvironment().name().toLowerCase(Locale.ROOT), path).toString();

        UploadManager uploadManager = new UploadManager(
                objectStorage,
                uploadConfiguration
        );

        PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(properties.getObjectStorage().getNamespace())
                .bucketName(properties.getObjectStorage().getBucketName())
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
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .namespaceName(properties.getObjectStorage().getNamespace())
                .bucketName(properties.getObjectStorage().getBucketName())
                .objectName(key.getKey()).build();

        objectStorage.deleteObject(request);
    }
}