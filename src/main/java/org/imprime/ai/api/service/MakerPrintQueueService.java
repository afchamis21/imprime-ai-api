package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreatePrintQueueItemRequest;
import org.imprime.ai.api.http.request.maker.PrintJobTransitionRequest;
import org.imprime.ai.api.http.request.maker.ReorderPrintQueueRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MakerPrintQueueService {
    PageResponse<PrintQueueItemResponse> listQueue(
            PrintJobStatus status,
            String printerGuid,
            Boolean activeOnly,
            OrderStatusCd orderStatus,
            Pageable pageable);

    PrintQueueItemResponse enqueueJob(CreatePrintQueueItemRequest request);

    void deleteJob(String jobGuid);

    PrintQueueItemResponse transitionJob(String jobGuid, PrintJobTransitionRequest request);

    List<PrintQueueItemResponse> reorderQueue(ReorderPrintQueueRequest request);

    PrintQueueItemResponse updateJob(String jobGuid, org.imprime.ai.api.http.request.maker.UpdatePrintQueueItemRequest request);
}
