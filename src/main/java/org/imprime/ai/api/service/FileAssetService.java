package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.model.oci.ObjectStorageKey;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.validator.FileValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAssetService {
    private final FileValidator fileValidator;
    private final ObjectStorageService objectStorageService;
    private final FileAssetRepository fileAssetRepository;

    public FileAsset upload(MultipartFile file, FileAsset.AssetType assetType) {
        BadRequestException validationError = fileValidator.validateFile(file, assetType);
        if (validationError != null) {
            throw validationError;
        }

        String fileName = fileValidator.sanitizeFilename(file);
        try {
            return storeAndPersistAsset(
                    assetType,
                    fileName,
                    file.getInputStream(),
                    file.getResource().contentLength(),
                    file.getContentType()
            );
        } catch (InternalErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error reading uploaded multipart stream [{}]!", fileName, e);
            throw new InternalErrorException();
        }
    }

    public FileAsset uploadBytes(
            byte[] content,
            String filename,
            String contentType,
            FileAsset.AssetType assetType) {
        BadRequestException validationError = fileValidator.validateBytes(content, filename, assetType);
        if (validationError != null) {
            throw validationError;
        }

        String sanitizedFilename = fileValidator.sanitizeFilename(filename);
        String resolvedContentType = contentType != null && !contentType.isBlank()
                ? contentType
                : "application/octet-stream";

        return storeAndPersistAsset(
                assetType,
                sanitizedFilename,
                new ByteArrayInputStream(content),
                content.length,
                resolvedContentType
        );
    }

    private FileAsset storeAndPersistAsset(
            FileAsset.AssetType assetType,
            String fileName,
            InputStream stream,
            long contentLength,
            String contentType) {
        String extension = StringUtils.getFilenameExtension(fileName);
        User caller = ServiceContext.getUserOrThrow();
        ObjectStorageKey key = getObjectStorageKey(assetType, caller, extension);

        try {
            objectStorageService.upload(key, stream, contentLength, contentType);
        } catch (Exception e) {
            log.error("Error while uploading file [{}]!", key, e);
            throw new InternalErrorException();
        }

        try {
            FileAsset fileAsset = new FileAsset();
            fileAsset.setAssetType(key.getAssetType());
            fileAsset.setAssetUrl(key.getFullPath());
            fileAsset.setName(fileName);
            fileAsset.setUserId(caller.getId());
            return fileAssetRepository.save(fileAsset);
        } catch (Exception e) {
            log.error("Error persisting File Asset [{}]!", key, e);
            objectStorageService.deleteAsync(key);
            throw new InternalErrorException();
        }
    }

    private ObjectStorageKey getObjectStorageKey(FileAsset.AssetType assetType, User user, String extension) {
        String identifier = UUID.randomUUID().toString();
        return switch (assetType) {
            case MODEL -> ObjectStorageKey.model(user.getGuid(), identifier, extension);
            case IMAGE -> ObjectStorageKey.image(user.getGuid(), identifier, extension);
            case ARCHIVE_3D -> ObjectStorageKey.archive3d(user.getGuid(), identifier, extension);
            case SCAD_SCRIPT -> ObjectStorageKey.scadScript(user.getGuid(), identifier, extension);
            case ATTACHMENT -> ObjectStorageKey.attachment(user.getGuid(), identifier, extension);
        };
    }

    public void deleteForever(FileAsset fileAsset) {
        objectStorageService.deleteAsync(ObjectStorageKey.of(fileAsset));
        fileAssetRepository.deleteById(fileAsset.getId());
    }
}
