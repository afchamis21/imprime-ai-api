package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreatePrinterRequest;
import org.imprime.ai.api.http.request.maker.PrinterCommandRequest;
import org.imprime.ai.api.http.request.maker.UpdatePrinterRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.PrinterResponse;
import org.imprime.ai.api.http.response.maker.RegenerateApiKeyResponse;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerPrinterService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MakerPrinterServiceImpl implements MakerPrinterService {

    @Override
    public PageResponse<PrinterResponse> listPrinters(PrinterStatus status, Pageable pageable) {
        throw new NotImplementedException("MakerPrinterService.listPrinters not yet implemented");
    }

    @Override
    public PrinterResponse getPrinter(String printerGuid) {
        throw new NotImplementedException("MakerPrinterService.getPrinter not yet implemented");
    }

    @Override
    public PrinterResponse createPrinter(CreatePrinterRequest request) {
        throw new NotImplementedException("MakerPrinterService.createPrinter not yet implemented");
    }

    @Override
    public PrinterResponse updatePrinter(String printerGuid, UpdatePrinterRequest request) {
        throw new NotImplementedException("MakerPrinterService.updatePrinter not yet implemented");
    }

    @Override
    public void deletePrinter(String printerGuid) {
        throw new NotImplementedException("MakerPrinterService.deletePrinter not yet implemented");
    }

    @Override
    public RegenerateApiKeyResponse regenerateApiKey(String printerGuid) {
        throw new NotImplementedException("MakerPrinterService.regenerateApiKey not yet implemented");
    }

    @Override
    public PrinterResponse sendCommand(String printerGuid, PrinterCommandRequest request) {
        throw new NotImplementedException("MakerPrinterService.sendCommand not yet implemented");
    }
}
