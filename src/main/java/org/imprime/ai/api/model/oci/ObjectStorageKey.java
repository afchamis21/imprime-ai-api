package org.imprime.ai.api.model.oci;

import lombok.Getter;
import org.imprime.ai.api.model.FileAsset;

import java.nio.file.Path;

@Getter
public final class ObjectStorageKey {
    private final String key;
    private final FileAsset.AssetType assetType;

    private ObjectStorageKey(String value, FileAsset.AssetType assetType) {
        this.key = value;
        this.assetType = assetType;
    }

    public static ObjectStorageKey model(
            String userGuid,
            String fileIdentifier) {

        return new ObjectStorageKey(
                Path.of("models", userGuid, fileIdentifier).toString(),
                FileAsset.AssetType.MODEL
        );
    }

    public static ObjectStorageKey of(FileAsset fileAsset) {
        return new ObjectStorageKey(fileAsset.getAssetUrl(), FileAsset.AssetType.MODEL);
    }
}
