package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.request.order.InstantCheckoutRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.order.InstantCheckoutResponse;
import org.imprime.ai.api.http.response.order.SlicingValidationStatusResponse;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;
import org.imprime.ai.api.service.InstantCheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@JwtAuthenticated
@Tag(name = "Pedidos Instantâneos e Validação CLI", description = "Checkout instantâneo com revalidação autoritativa do modelo 3D via fatiador headless no servidor")
public class InstantCheckoutController {

    private final InstantCheckoutService instantCheckoutService;

    @PostMapping(value = "/instant-checkout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cria pedido a partir da cotação instantânea e valida os arquivos 3D no servidor")
    public ResponseEntity<BaseResponse<InstantCheckoutResponse>> instantCheckout(
            @RequestPart("file") List<MultipartFile> files,
            @Valid @RequestPart("payload") InstantCheckoutRequest payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        InstantCheckoutResponse response = instantCheckoutService.createAndValidate(files, payload, idempotencyKey);
        HttpStatus status = switch (response.validationStatus()) {
            case PENDING, RUNNING -> HttpStatus.ACCEPTED;
            case RECALCULATED -> HttpStatus.OK;
            default -> HttpStatus.CREATED;
        };
        return BaseResponse.build(response, status);
    }

    @GetMapping("/{orderGuid}/slicing-validation")
    @Operation(summary = "Consulta o status e métricas da validação de fatiamento do pedido no servidor")
    public ResponseEntity<BaseResponse<SlicingValidationStatusResponse>> getSlicingValidationStatus(
            @PathVariable String orderGuid
    ) {
        return BaseResponse.ok(instantCheckoutService.getValidationStatus(orderGuid));
    }

    @PostMapping("/{orderGuid}/confirm-recalculated-quote")
    @Operation(summary = "Confirma o valor oficial recalculado pelo servidor após divergência acima da tolerância")
    public ResponseEntity<BaseResponse<SlicingValidationStatusResponse>> confirmRecalculatedQuote(
            @PathVariable String orderGuid
    ) {
        return BaseResponse.ok(instantCheckoutService.confirmRecalculatedQuote(orderGuid));
    }

    @PostMapping("/{orderGuid}/revalidate-slice")
    @Operation(summary = "Reexecuta a validação de fatiamento no servidor para um pedido pendente ou com falha")
    public ResponseEntity<BaseResponse<SlicingValidationStatusResponse>> revalidateSlice(
            @PathVariable String orderGuid
    ) {
        SlicingValidationStatusResponse response = instantCheckoutService.revalidateSlice(orderGuid);
        HttpStatus status = (response.validationStatus() == SlicingValidationStatus.PENDING
                || response.validationStatus() == SlicingValidationStatus.RUNNING)
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return BaseResponse.build(response, status);
    }
}
