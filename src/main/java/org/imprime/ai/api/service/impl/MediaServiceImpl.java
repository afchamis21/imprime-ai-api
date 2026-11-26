package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.media.MediaUploadResponse;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.enums.MediaCategory;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.service.FileAssetService;
import org.imprime.ai.api.service.MediaService;
import org.imprime.ai.api.service.ObjectStorageService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "gif", "svg", "avif"
    );

    private static final Set<String> REVIEW_PHOTO_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif", "avif");

    private static final Set<String> MODEL_EXTENSIONS = Set.of(
            "stl", "3mf", "obj", "gcode", "scad", "step", "stp", "iges", "igs", "glb"
    );

    private final FileAssetService fileAssetService;
    private final ObjectStorageService objectStorageService;

    @Override
    public MediaUploadResponse uploadMedia(MultipartFile file, MediaCategory category) {
        String ext = file != null && file.getOriginalFilename() != null
                ? StringUtils.getFilenameExtension(file.getOriginalFilename())
                : null;
        if (ext != null && MODEL_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException(MessageCd.PRINT_DECLARATION_REQUIRED);
        }
        if (category == MediaCategory.REVIEW_PHOTO
                && (ext == null || !REVIEW_PHOTO_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT)))) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        boolean isImageExt = ext != null && IMAGE_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT));
        FileAsset.AssetType assetType = (MediaCategory.CHAT_ATTACHMENT.equals(category) || !isImageExt)
                ? FileAsset.AssetType.ATTACHMENT
                : FileAsset.AssetType.IMAGE;
        FileAsset asset = fileAssetService.upload(file, assetType);

        String url = objectStorageService.buildPublicObjectUrl(asset);
        String contentType = file.getContentType() != null && !file.getContentType().isBlank()
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return new MediaUploadResponse(
                asset.getGuid(),
                url,
                contentType,
                file.getSize(),
                category,
                asset.getCreateDt() != null ? asset.getCreateDt() : Instant.now()
        );
    }
}
