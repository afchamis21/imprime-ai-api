package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.SlicingValidationProperties;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.oci.ObjectStorageKey;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.service.ObjectStorageService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlicedArtifactStorage {

    private final SlicingValidationProperties properties;
    private final ObjectStorageService objectStorageService;
    private final FileAssetRepository fileAssetRepository;

    public @Nullable Long storeValidated3mf(
            String orderGuid,
            int plateNumber,
            Long ownerUserId,
            @Nullable Path exported3mfPath
    ) {
        if (!properties.isStoreValidatedArtifacts()
                || exported3mfPath == null
                || !Files.isRegularFile(exported3mfPath)) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(exported3mfPath);
            if (bytes.length == 0) {
                return null;
            }
            String privateBucket = objectStorageService.requirePrivateOrderBucket();
            ObjectStorageKey key = ObjectStorageKey.validated3mf(
                    privateBucket,
                    orderGuid,
                    "plate-" + plateNumber + "-" + System.currentTimeMillis()
            );
            objectStorageService.upload(
                    key,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    "model/3mf"
            );

            FileAsset asset = new FileAsset();
            asset.setAssetType(FileAsset.AssetType.VALIDATED_3MF);
            asset.setAssetUrl(key.getFullPath());
            asset.setName("validated-plate-" + plateNumber + ".3mf");
            asset.setUserId(ownerUserId);
            FileAsset saved = fileAssetRepository.save(asset);
            return saved.getId();
        } catch (IOException e) {
            log.warn("Could not read validated 3MF for Order [{}] plate {}: {}", orderGuid, plateNumber, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Could not upload validated 3MF for Order [{}] plate {}: {}", orderGuid, plateNumber, e.getMessage());
            return null;
        }
    }
}
