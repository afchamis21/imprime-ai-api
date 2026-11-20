package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.media.MediaUploadResponse;
import org.imprime.ai.api.model.enums.MediaCategory;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    @Override
    public MediaUploadResponse uploadMedia(MultipartFile file, MediaCategory category) {
        throw new NotImplementedException("MediaService.uploadMedia not yet implemented");
    }
}
