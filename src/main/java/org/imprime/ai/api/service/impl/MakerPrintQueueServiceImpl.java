package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreatePrintQueueItemRequest;
import org.imprime.ai.api.http.request.maker.PrintJobTransitionRequest;
import org.imprime.ai.api.http.request.maker.ReorderPrintQueueRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerPrintQueueService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MakerPrintQueueServiceImpl implements MakerPrintQueueService {

    @Override
    public PageResponse<PrintQueueItemResponse> listQueue(PrintJobStatus status, String printerGuid, Boolean activeOnly, Pageable pageable) {
        throw new NotImplementedException("MakerPrintQueueService.listQueue not yet implemented");
    }

    @Override
    public PrintQueueItemResponse enqueueJob(CreatePrintQueueItemRequest request) {
        throw new NotImplementedException("MakerPrintQueueService.enqueueJob not yet implemented");
    }

    @Override
    public void deleteJob(String jobGuid) {
        throw new NotImplementedException("MakerPrintQueueService.deleteJob not yet implemented");
    }

    @Override
    public PrintQueueItemResponse transitionJob(String jobGuid, PrintJobTransitionRequest request) {
        throw new NotImplementedException("MakerPrintQueueService.transitionJob not yet implemented");
    }

    @Override
    public List<PrintQueueItemResponse> reorderQueue(ReorderPrintQueueRequest request) {
        throw new NotImplementedException("MakerPrintQueueService.reorderQueue not yet implemented");
    }
}
