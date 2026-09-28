package org.imprime.ai.api.validator;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class FileValidator {
    private static final Map<FileAsset.AssetType, Set<String>> ALLOWED_EXTENSIONS = new EnumMap<>(FileAsset.AssetType.class);

    public FileValidator() {
        ALLOWED_EXTENSIONS.put(FileAsset.AssetType.MODEL, Set.of("stl", "3mf")); // TODO Conferir com o Amorim
    }

    public @Nullable BadRequestException validateFile(MultipartFile file, FileAsset.AssetType type) {
        return switch (type) {
            case MODEL -> defaultValidation(file, type);
        };
    }

    private @Nullable BadRequestException defaultValidation(MultipartFile file, FileAsset.AssetType type) {
        if (file == null || file.isEmpty()) {
            log.warn("File is empty!");
            return new BadRequestException(MessageCd.FILE_EMPTY);
        }

        String filename = sanitizeFilename(file);
        if (filename == null || filename.isBlank()) {
            log.warn("Filename is empty!");
            return new BadRequestException(MessageCd.FILE_MISSING_NAME);
        }

        String extension = StringUtils.getFilenameExtension(filename);
        Set<String> allowedExtensions = ALLOWED_EXTENSIONS.get(type);
        if (extension == null || allowedExtensions.contains(extension.toLowerCase(Locale.ROOT))) {
            log.warn("Extension [{}] is not on the allow list {}!", extension, allowedExtensions);
            return new BadRequestException(MessageCd.FILE_UNSUPPORTED_EXTENSION, allowedExtensions.toString());
        }

        return null;
    }

    public @Nullable String sanitizeFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String sanitized = StringUtils.getFilename(filename);
        if (sanitized == null || sanitized.isBlank()) {
            return null;
        }

        return sanitized;

    }
}
