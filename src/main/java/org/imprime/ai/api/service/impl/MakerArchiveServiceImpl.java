package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateArchiveFileRequest;
import org.imprime.ai.api.http.request.maker.ReprintArchiveFileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileDownloadResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerArchiveService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MakerArchiveServiceImpl implements MakerArchiveService {

    @Override
    public PageResponse<ArchiveFileResponse> listFiles(String query, FileFormat3D format, MaterialType material, String projectGuid, Pageable pageable) {
        throw new NotImplementedException("MakerArchiveService.listFiles not yet implemented");
    }

    @Override
    public ArchiveFileResponse uploadArchiveFile(MultipartFile file, CreateArchiveFileRequest metadata) {
        throw new NotImplementedException("MakerArchiveService.uploadArchiveFile not yet implemented");
    }

    @Override
    public ArchiveFileDownloadResponse generateDownloadUrl(String fileGuid) {
        throw new NotImplementedException("MakerArchiveService.generateDownloadUrl not yet implemented");
    }

    @Override
    public void deleteArchiveFile(String fileGuid) {
        throw new NotImplementedException("MakerArchiveService.deleteArchiveFile not yet implemented");
    }

    @Override
    public PrintQueueItemResponse reprintFile(String fileGuid, ReprintArchiveFileRequest request) {
        throw new NotImplementedException("MakerArchiveService.reprintFile not yet implemented");
    }
}
