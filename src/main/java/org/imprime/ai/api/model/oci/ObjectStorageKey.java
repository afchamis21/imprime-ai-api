package org.imprime.ai.api.model.oci;

import lombok.Getter;
import lombok.ToString;
import org.imprime.ai.api.model.FileAsset;

import java.nio.file.Path;

@Getter
@ToString
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

        return modelInBucket("models", userGuid, fileIdentifier, extension);
    }

    public static ObjectStorageKey modelInBucket(String bucket, String userGuid, String fileIdentifier, String extension) {
        return new ObjectStorageKey(
                Path.of(userGuid, fileIdentifier + "." + extension).toString(),
                bucket,
                FileAsset.AssetType.MODEL
        );
    }

    public static ObjectStorageKey validated3mf(String bucket, String orderGuid, String plateIdentifier) {
        return new ObjectStorageKey(
                Path.of("validated", orderGuid, plateIdentifier + ".3mf").toString(),
                bucket,
                FileAsset.AssetType.VALIDATED_3MF
        );
    }

    public static ObjectStorageKey validatedGcode(String bucket, String orderGuid, String plateIdentifier) {
        return new ObjectStorageKey(
                Path.of("validated", orderGuid, plateIdentifier + ".gcode").toString(),
                bucket,
                FileAsset.AssetType.VALIDATED_GCODE
        );
    }

    public static ObjectStorageKey image(
            String userGuid,
            String fileIdentifier,
            String extension) {

        return new ObjectStorageKey(
                Path.of(userGuid, fileIdentifier + "." + extension).toString(),
                "media",
                FileAsset.AssetType.IMAGE
        );
    }

    public static ObjectStorageKey archive3d(
            String userGuid,
            String fileIdentifier,
            String extension) {

        return new ObjectStorageKey(
                Path.of(userGuid, fileIdentifier + "." + extension).toString(),
                "archives",
                FileAsset.AssetType.ARCHIVE_3D
        );
    }

    public static ObjectStorageKey scadScript(
            String userGuid,
            String fileIdentifier,
            String extension) {

        return new ObjectStorageKey(
                Path.of(userGuid, fileIdentifier + "." + extension).toString(),
                "scad",
                FileAsset.AssetType.SCAD_SCRIPT
        );
    }

    public static ObjectStorageKey attachment(
            String userGuid,
            String fileIdentifier,
            String extension) {

        return new ObjectStorageKey(
                Path.of(userGuid, fileIdentifier + "." + extension).toString(),
                "media",
                FileAsset.AssetType.ATTACHMENT
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