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
import org.springframework.web.multipart.MultipartFile;

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

        User caller = ServiceContext.getUserOrThrow();
        ObjectStorageKey key = getObjectStorageKey(assetType, caller);
        String fileName = fileValidator.sanitizeFilename(file);

        try {
            objectStorageService.upload(key, file.getInputStream(), file.getResource().contentLength(), file.getContentType());
        } catch (Exception e) {
            log.error("Error while uploading file [{}]!", key, e);
            throw new InternalErrorException();
        }

        try {
            FileAsset fileAsset = new FileAsset();
            fileAsset.setAssetType(key.getAssetType());
            fileAsset.setAssetUrl(key.getKey());
            fileAsset.setName(fileName);
            fileAsset.setUserId(caller.getId());
            return fileAssetRepository.save(fileAsset);
        } catch (Exception e) {
            log.error("Error persisting File Asset [{}]!", key, e);
            objectStorageService.deleteAsync(key);
            throw new InternalErrorException();
        }
    }

    private ObjectStorageKey getObjectStorageKey(FileAsset.AssetType assetType, User user) {
        return switch (assetType) {
            case MODEL -> ObjectStorageKey.model(user.getGuid(), UUID.randomUUID().toString());
        };
    }

    public void deleteForever(FileAsset fileAsset) {
        objectStorageService.deleteAsync(ObjectStorageKey.of(fileAsset));
        fileAssetRepository.deleteById(fileAsset.getId());
    }
}
