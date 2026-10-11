package org.imprime.ai.api.model.oci;

import lombok.Getter;
import org.imprime.ai.api.model.FileAsset;

import java.nio.file.Path;

@Getter
public final class ObjectStorageKey {
    private final String key;
    private final String bucket;
    private final FileAsset.AssetType assetType;

    private ObjectStorageKey(
            String key,
            String bucket,
            FileAsset.AssetType assetType) {
        this.key = key;
        this.bucket = bucket;
        this.assetType = assetType;
    }

    public static ObjectStorageKey model(
            String userGuid,
            String fileIdentifier,
            String extension) {

        return new ObjectStorageKey(
                Path.of(userGuid, fileIdentifier + "." + extension).toString(),
                "models",
                FileAsset.AssetType.MODEL
        );
    }

    public static ObjectStorageKey of(FileAsset fileAsset) {
        String assetUrl = fileAsset.getAssetUrl();

        int separatorIndex = assetUrl.indexOf('/');

        if (separatorIndex <= 0 || separatorIndex == assetUrl.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid asset URL: " + assetUrl
            );
        }

        String bucket = assetUrl.substring(0, separatorIndex);
        String key = assetUrl.substring(separatorIndex + 1);

        return new ObjectStorageKey(
                key,
                bucket,
                fileAsset.getAssetType()
        );
    }

    public String getFullPath() {
        return bucket + "/" + key;
    }
}