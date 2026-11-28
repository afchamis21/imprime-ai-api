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
        ALLOWED_EXTENSIONS.put(FileAsset.AssetType.MODEL, Set.of("stl", "3mf", "obj", "glb", "scad"));
        ALLOWED_EXTENSIONS.put(FileAsset.AssetType.IMAGE, Set.of("png", "jpg", "jpeg", "webp", "gif", "svg", "avif"));
        ALLOWED_EXTENSIONS.put(FileAsset.AssetType.ARCHIVE_3D, Set.of("stl", "3mf", "gcode", "obj"));
        ALLOWED_EXTENSIONS.put(FileAsset.AssetType.SCAD_SCRIPT, Set.of("scad"));
        ALLOWED_EXTENSIONS.put(FileAsset.AssetType.VALIDATED_3MF, Set.of("3mf"));
        ALLOWED_EXTENSIONS.put(FileAsset.AssetType.VALIDATED_GCODE, Set.of("gcode"));
        ALLOWED_EXTENSIONS.put(
                FileAsset.AssetType.ATTACHMENT,
                Set.of(
                        "png", "jpg", "jpeg", "webp", "gif", "svg", "avif",
                        "stl", "3mf", "obj", "gcode", "scad", "step", "stp", "iges", "igs",
                        "pdf", "zip", "rar", "7z", "txt", "csv", "json", "doc", "docx", "xls", "xlsx"
                )
        );
    }

    public @Nullable BadRequestException validateFile(MultipartFile file, FileAsset.AssetType type) {
        return switch (type) {
            case MODEL, IMAGE, ARCHIVE_3D, SCAD_SCRIPT, ATTACHMENT, VALIDATED_3MF, VALIDATED_GCODE -> defaultValidation(file, type);
        };
    }

    public @Nullable BadRequestException validateBytes(byte[] content, String rawFilename, FileAsset.AssetType type) {
        if (content == null || content.length == 0) {
            log.warn("Byte content is empty!");
            return new BadRequestException(MessageCd.FILE_EMPTY);
        }
        return validateFilenameAndExtension(sanitizeFilename(rawFilename), type);
    }

    private @Nullable BadRequestException defaultValidation(MultipartFile file, FileAsset.AssetType type) {
        if (file == null || file.isEmpty()) {
            log.warn("File is empty!");
            return new BadRequestException(MessageCd.FILE_EMPTY);
        }

        return validateFilenameAndExtension(sanitizeFilename(file), type);
    }

    private @Nullable BadRequestException validateFilenameAndExtension(@Nullable String filename, FileAsset.AssetType type) {
        if (filename == null || filename.isBlank()) {
            log.warn("Filename is empty!");
            return new BadRequestException(MessageCd.FILE_MISSING_NAME);
        }

        String extension = StringUtils.getFilenameExtension(filename);
        Set<String> allowedExtensions = ALLOWED_EXTENSIONS.get(type);
        if (extension == null || !allowedExtensions.contains(extension.toLowerCase(Locale.ROOT))) {
            log.warn("Extension [{}] is not on the allow list {}!", extension, allowedExtensions);
            return new BadRequestException(MessageCd.FILE_UNSUPPORTED_EXTENSION, allowedExtensions.toString());
        }

        return null;
    }

    public @Nullable String sanitizeFilename(MultipartFile file) {
        return sanitizeFilename(file.getOriginalFilename());
    }

    public @Nullable String sanitizeFilename(@Nullable String filename) {
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
