package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PrintFileValidator {
    private static final long MAX_BYTES = 60L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 240L * 1024 * 1024;
    public static final int MAX_PLATES = 64;
    private static final int MAX_ARCHIVE_ENTRIES = 512;

    public void validate(List<MultipartFile> files) {
        if (files.size() > MAX_PLATES || files.stream().filter(Objects::nonNull)
                .mapToLong(MultipartFile::getSize).sum() > MAX_BYTES) {
            throw invalid();
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BadRequestException(MessageCd.FILE_EMPTY);
            }
            String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
            try (InputStream stream = new BufferedInputStream(file.getInputStream())) {
                if ("stl".equalsIgnoreCase(extension)) {
                    validateStl(stream, file.getSize());
                } else if ("3mf".equalsIgnoreCase(extension)) {
                    validate3mf(stream);
                } else {
                    throw new BadRequestException(MessageCd.FILE_UNSUPPORTED_EXTENSION, "STL, 3MF");
                }
            } catch (IOException e) {
                throw invalid();
            }
        }
    }

    private void validateStl(InputStream stream, long size) throws IOException {
        byte[] header = stream.readNBytes(84);
        if (header.length == 84) {
            long triangles = Integer.toUnsignedLong(ByteBuffer.wrap(header, 80, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt());
            if (triangles > 0 && size == 84 + triangles * 50) return;
        }
        String prefix = new String(header, StandardCharsets.US_ASCII).stripLeading();
        if (!prefix.startsWith("solid ") && !prefix.startsWith("solid\n") && !prefix.startsWith("solid\r")) {
            throw invalid();
        }
        byte[] tail = header;
        long remaining = size - header.length;
        if (remaining > 0) {
            stream.skipNBytes(Math.max(0, remaining - 512));
            tail = stream.readNBytes(512);
        }
        if (!new String(tail, StandardCharsets.US_ASCII).contains("endsolid")) throw invalid();
    }

    private void validate3mf(InputStream stream) throws IOException {
        Set<String> entries = new HashSet<>();
        long expanded = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || List.of(name.split("/")).contains("..")
                        || !entries.add(name) || entries.size() > MAX_ARCHIVE_ENTRIES) {
                    throw invalid();
                }
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > MAX_EXPANDED_BYTES) throw invalid();
                }
            }
        }
        if (!entries.contains("[Content_Types].xml") || !entries.contains("_rels/.rels")
                || entries.stream().noneMatch(name -> name.toLowerCase(Locale.ROOT).endsWith(".model"))) {
            throw invalid();
        }
    }

    private BadRequestException invalid() {
        return new BadRequestException(MessageCd.PRINT_FILE_INVALID);
    }
}
