package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.media.MediaUploadResponse;
import org.imprime.ai.api.model.enums.MediaCategory;
import org.imprime.ai.api.service.MediaService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/media")
@JwtAuthenticated
@Tag(name = "Mídia", description = "Upload centralizado de imagens e arquivos de mídia para Object Storage (substitui Base64 em JSON)")
public class MediaController {
    private final MediaService mediaService;

    @Operation(
            summary = "Enviar arquivo de mídia",
            description = "Faz upload de imagem (`.png`, `.jpg`, `.jpeg`, `.webp`) para o Object Storage associado à categoria informada e retorna a URL/GUID do `FileAsset`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mídia enviada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido, vazio ou extensão não suportada", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<MediaUploadResponse>> uploadMedia(
            @Parameter(description = "Arquivo de imagem a ser enviado", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Finalidade da mídia enviada", required = true)
            @RequestParam MediaCategory category) {
        return BaseResponse.created(mediaService.uploadMedia(file, category));
    }
}
