package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreatePrinterRequest;
import org.imprime.ai.api.http.request.maker.PrinterCommandRequest;
import org.imprime.ai.api.http.request.maker.UpdatePrinterRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.PrinterResponse;
import org.imprime.ai.api.http.response.maker.RegenerateApiKeyResponse;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.springframework.data.domain.Pageable;

public interface MakerPrinterService {
    PageResponse<PrinterResponse> listPrinters(PrinterStatus status, Pageable pageable);

    PrinterResponse getPrinter(String printerGuid);

    PrinterResponse createPrinter(CreatePrinterRequest request);

    PrinterResponse updatePrinter(String printerGuid, UpdatePrinterRequest request);

    void deletePrinter(String printerGuid);

    RegenerateApiKeyResponse regenerateApiKey(String printerGuid);

    PrinterResponse sendCommand(String printerGuid, PrinterCommandRequest request);
}
