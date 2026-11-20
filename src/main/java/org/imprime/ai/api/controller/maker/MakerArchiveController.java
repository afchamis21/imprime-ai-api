package org.imprime.ai.api.controller.maker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireMaker;
import org.imprime.ai.api.http.request.maker.CreateArchiveFileRequest;
import org.imprime.ai.api.http.request.maker.ReprintArchiveFileRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileDownloadResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.service.MakerArchiveService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maker/files")
@RequireMaker
@Tag(name = "Maker — Acervo de Arquivos (Print Archive)", description = "Armazenamento e reimpressão de arquivos 3D e fatiados (.3mf, .stl, .gcode) no Object Storage")
public class MakerArchiveController {
    private final MakerArchiveService makerArchiveService;

    @Operation(
            summary = "Listar arquivos do Print Archive",
            description = "Retorna página com o acervo de arquivos 3D e fatiados pertencentes ao estúdio autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arquivos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<ArchiveFileResponse>>> listFiles(
            @Parameter(description = "Busca textual em nome ou tags")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filtrar por formato do arquivo")
            @RequestParam(required = false) FileFormat3D format,
            @Parameter(description = "Filtrar por material previsto")
            @RequestParam(required = false) MaterialType material,
            @Parameter(description = "Filtrar por GUID do projeto associado")
            @RequestParam(required = false) String projectGuid,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(makerArchiveService.listFiles(q, format, material, projectGuid, pageable));
    }

    @Operation(
            summary = "Enviar novo arquivo para o Print Archive",
            description = "Recebe o arquivo binário (`.3mf`, `.stl`, `.gcode`, `.obj`) e seus metadados técnicos via `multipart/form-data`, armazena no Object Storage e registra no acervo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Arquivo armazenado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo ou metadados inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não possui perfil de Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<ArchiveFileResponse>> uploadArchiveFile(
            @Parameter(description = "Arquivo 3D/fatiado (.stl, .3mf, .gcode, .obj)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Metadados técnicos do arquivo", required = true)
            @RequestPart("metadata") @Valid CreateArchiveFileRequest metadata) {
        return BaseResponse.created(makerArchiveService.uploadArchiveFile(file, metadata));
    }

    @Operation(
            summary = "Gerar URL de download de arquivo do acervo",
            description = "Retorna uma URL pré-assinada temporária no Object Storage para download do arquivo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL gerada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Arquivo pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Arquivo não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{fileGuid}/download-url")
    public ResponseEntity<BaseResponse<ArchiveFileDownloadResponse>> generateDownloadUrl(
            @Parameter(description = "GUID do arquivo no acervo", required = true)
            @PathVariable String fileGuid) {
        return BaseResponse.ok(makerArchiveService.generateDownloadUrl(fileGuid));
    }

    @Operation(
            summary = "Excluir arquivo do Print Archive",
            description = "Remove logicamente um arquivo do acervo do Maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Arquivo removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Arquivo pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Arquivo não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{fileGuid}")
    public ResponseEntity<Void> deleteArchiveFile(
            @Parameter(description = "GUID do arquivo no acervo", required = true)
            @PathVariable String fileGuid) {
        makerArchiveService.deleteArchiveFile(fileGuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Reimprimir arquivo na fila de impressão",
            description = "Enfileira uma nova impressão a partir do arquivo do acervo e incrementa seu histórico de impressões."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Job criado na fila de impressão"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de reimpressão inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Arquivo pertence a outro estúdio ou usuário não é Maker", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Arquivo ou impressora não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{fileGuid}/reprint")
    public ResponseEntity<BaseResponse<PrintQueueItemResponse>> reprintFile(
            @Parameter(description = "GUID do arquivo no acervo", required = true)
            @PathVariable String fileGuid,
            @Valid @RequestBody(required = false) ReprintArchiveFileRequest request) {
        return BaseResponse.created(makerArchiveService.reprintFile(fileGuid, request));
    }
}
