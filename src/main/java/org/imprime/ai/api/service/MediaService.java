package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.media.MediaUploadResponse;
import org.imprime.ai.api.model.enums.MediaCategory;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {
    MediaUploadResponse uploadMedia(MultipartFile file, MediaCategory category);
}
