package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminDmcaTakedownRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminOptionalNoteRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminModelDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminModelSummaryResponse;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.service.AdminModelModerationService;
import org.imprime.ai.api.service.OrderManagementService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/models")
@RequireAdmin
@Tag(name = "Admin — Moderação de Modelos 3D", description = "Fila de revisão de modelos 3D, aprovações, recusas e takedowns DMCA")
public class AdminModelModerationController {
    private final AdminModelModerationService modelModerationService;

    @Operation(summary = "Listar modelos 3D para moderação")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminModelSummaryResponse>>> listModels(
            @RequestParam(required = false) ModelModerationStatus moderationStatus,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String authorGuid,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(modelModerationService.listModels(moderationStatus, q, category, authorGuid, pageable));
    }

    @Operation(summary = "Obter detalhes de um modelo 3D para moderação")
    @GetMapping("/{modelGuid}")
    public ResponseEntity<BaseResponse<AdminModelDetailResponse>> getModelDetail(@PathVariable String modelGuid) {
        return BaseResponse.ok(modelModerationService.getModelDetail(modelGuid));
    }

    @Operation(
            summary = "Baixar ou visualizar o binário do arquivo 3D do modelo",
            description = "Faz streaming do conteúdo binário do arquivo 3D associado ao modelo para renderização interativa no painel administrativo ou download direto."
    )
    @GetMapping("/{modelGuid}/file")
    public ResponseEntity<byte[]> downloadModelFile(
            @PathVariable String modelGuid,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        OrderManagementService.OrderFileContent content = modelModerationService.downloadModelFile(modelGuid);

        ContentDisposition disposition = ContentDisposition
                .builder(download ? "attachment" : "inline")
                .filename(content.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(content.bytes());
    }

    @Operation(summary = "Aprovar modelo 3D")
    @PostMapping("/{modelGuid}/approve")
    public ResponseEntity<BaseResponse<AdminModelDetailResponse>> approveModel(
            @PathVariable String modelGuid,
            @Valid @RequestBody(required = false) AdminOptionalNoteRequest request
    ) {
        return BaseResponse.ok(modelModerationService.approveModel(modelGuid, request));
    }

    @Operation(summary = "Recusar modelo 3D (motivo obrigatório)")
    @PostMapping("/{modelGuid}/reject")
    public ResponseEntity<BaseResponse<AdminModelDetailResponse>> rejectModel(
            @PathVariable String modelGuid,
            @Valid @RequestBody AdminModerationReasonRequest request
    ) {
        return BaseResponse.ok(modelModerationService.rejectModel(modelGuid, request));
    }

    @Operation(summary = "Derrubar modelo 3D por denúncia / DMCA")
    @PostMapping("/{modelGuid}/takedown")
    public ResponseEntity<BaseResponse<AdminModelDetailResponse>> takedownModel(
            @PathVariable String modelGuid,
            @Valid @RequestBody AdminDmcaTakedownRequest request
    ) {
        return BaseResponse.ok(modelModerationService.takedownModel(modelGuid, request));
    }

    @Operation(summary = "Restaurar modelo 3D após revisão")
    @PostMapping("/{modelGuid}/restore")
    public ResponseEntity<BaseResponse<AdminModelDetailResponse>> restoreModel(
            @PathVariable String modelGuid,
            @Valid @RequestBody(required = false) AdminOptionalNoteRequest request
    ) {
        return BaseResponse.ok(modelModerationService.restoreModel(modelGuid, request));
    }
}
