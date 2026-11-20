package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateExpenseRequest;
import org.imprime.ai.api.http.request.maker.CreatePayoutRequest;
import org.imprime.ai.api.http.request.maker.UpdateFinanceRatesRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FinanceRatesResponse;
import org.imprime.ai.api.http.response.maker.FinanceTransactionResponse;
import org.imprime.ai.api.http.response.maker.MakerFinanceSummaryResponse;
import org.imprime.ai.api.http.response.maker.PayoutResponse;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerFinanceService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MakerFinanceServiceImpl implements MakerFinanceService {

    @Override
    public MakerFinanceSummaryResponse getSummary() {
        throw new NotImplementedException("MakerFinanceService.getSummary not yet implemented");
    }

    @Override
    public PageResponse<FinanceTransactionResponse> listTransactions(
            FinanceTransactionType type,
            FinanceTransactionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        throw new NotImplementedException("MakerFinanceService.listTransactions not yet implemented");
    }

    @Override
    public PayoutResponse requestPayout(String idempotencyKey, CreatePayoutRequest request) {
        throw new NotImplementedException("MakerFinanceService.requestPayout not yet implemented");
    }

    @Override
    public FinanceTransactionResponse createExpense(CreateExpenseRequest request) {
        throw new NotImplementedException("MakerFinanceService.createExpense not yet implemented");
    }

    @Override
    public FinanceRatesResponse updateRates(UpdateFinanceRatesRequest request) {
        throw new NotImplementedException("MakerFinanceService.updateRates not yet implemented");
    }
}
