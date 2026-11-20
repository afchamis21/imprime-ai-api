package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateArchiveFileRequest;
import org.imprime.ai.api.http.request.maker.ReprintArchiveFileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileDownloadResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface MakerArchiveService {
    PageResponse<ArchiveFileResponse> listFiles(String query, FileFormat3D format, MaterialType material, String projectGuid, Pageable pageable);

    ArchiveFileResponse uploadArchiveFile(MultipartFile file, CreateArchiveFileRequest metadata);

    ArchiveFileDownloadResponse generateDownloadUrl(String fileGuid);

    void deleteArchiveFile(String fileGuid);

    PrintQueueItemResponse reprintFile(String fileGuid, ReprintArchiveFileRequest request);
}
