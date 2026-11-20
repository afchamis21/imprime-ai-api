package org.imprime.ai.api.service;

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
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface MakerFinanceService {
    MakerFinanceSummaryResponse getSummary();

    PageResponse<FinanceTransactionResponse> listTransactions(
            FinanceTransactionType type,
            FinanceTransactionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );

    PayoutResponse requestPayout(String idempotencyKey, CreatePayoutRequest request);

    FinanceTransactionResponse createExpense(CreateExpenseRequest request);

    FinanceRatesResponse updateRates(UpdateFinanceRatesRequest request);
}
