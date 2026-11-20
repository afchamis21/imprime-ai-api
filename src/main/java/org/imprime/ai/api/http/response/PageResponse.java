package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Resposta paginada padrão")
public record PageResponse<T>(
        @Schema(description = "Itens da página atual")
        List<T> content,
        @Schema(description = "Número da página atual (base 0)", example = "0")
        int page,
        @Schema(description = "Tamanho máximo da página", example = "20")
        int size,
        @Schema(description = "Total de registros encontrados", example = "42")
        long totalElements,
        @Schema(description = "Total de páginas disponíveis", example = "3")
        int totalPages,
        @Schema(description = "Indica se esta é a primeira página", example = "true")
        boolean first,
        @Schema(description = "Indica se esta é a última página", example = "false")
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
