package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.request.report.CreateReportRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.report.ReportResponse;
import org.imprime.ai.api.service.ContentReportService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
@JwtAuthenticated
@Tag(name = "Denúncias", description = "Registro e acompanhamento de denúncias contra produtos, modelos 3D e makers")
public class ReportController {
    private final ContentReportService contentReportService;

    @Operation(
            summary = "Enviar nova denúncia",
            description = "Registra uma denúncia autenticada contra um produto, modelo 3D ou perfil de maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Denúncia registrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou tentativa de denunciar o próprio item", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item ou maker alvo não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe uma denúncia em análise do mesmo usuário para este item", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<ReportResponse>> createReport(
            @Valid @RequestBody CreateReportRequest request
    ) {
        return BaseResponse.created(contentReportService.createReport(request));
    }

    @Operation(
            summary = "Listar minhas denúncias",
            description = "Retorna página com as denúncias enviadas pelo usuário autenticado."
    )
    @GetMapping("/mine")
    public ResponseEntity<BaseResponse<PageResponse<ReportResponse>>> listMyReports(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(contentReportService.listMyReports(pageable));
    }
}
