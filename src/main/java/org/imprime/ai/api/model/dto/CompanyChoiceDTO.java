package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Opção de empresa (Maker) disponível para atender a um pedido")
public record CompanyChoiceDTO(
        @Schema(description = "Nome da empresa/Maker", example = "Imprime 3D Lab")
        String name,
        @Schema(description = "GUID único da empresa", example = "550e8400-e29b-41d4-a716-446655440000")
        String guid,
        @Schema(description = "Endereço da empresa")
        AddressDTO address,
        @Schema(description = "Distância estimada até o endereço de entrega do comprador")
        Distance distance,
        @Schema(description = "Indica se o estúdio está online agora", example = "true")
        boolean online
) {
    @Schema(description = "Distância calculada entre o Maker e o endereço de entrega")
    public record Distance(
            @Schema(description = "Valor numérico da distância", example = "4.25")
            BigDecimal value,
            @Schema(description = "Unidade de medida da distância", example = "km")
            String unit
    ) {
    }
}
