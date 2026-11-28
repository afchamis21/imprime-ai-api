package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.maker.BuildVolumeDto;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.List;

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
        boolean online,
        @Schema(description = "Impressora principal do estúdio", example = "Bambu Lab A1")
        String primaryPrinter,
        @Schema(description = "Indica se suporta impressão multicolor/AMS", example = "true")
        boolean supportsMulticolor,
        @Schema(description = "Quantidade máxima de cores/slots simultâneos", example = "4")
        int maxMulticolorSlots,
        @Schema(description = "Indica se aceita orçamentos personalizados", example = "true")
        boolean acceptingCustomQuotes,
        @Schema(description = "Materiais operados pelo Maker")
        List<MaterialType> materials,
        @Schema(description = "Nota média do Maker", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Total de avaliações", example = "48")
        int reviewsCount,
        @Schema(description = "Total de pedidos concluídos", example = "210")
        int printsDone,
        @Schema(description = "Indica se o Maker é verificado", example = "true")
        boolean verified,
        @Schema(description = "URL do avatar do Maker")
        String avatarUrl,
        @Schema(description = "Prazo mínimo típico em dias", example = "2")
        Integer minLeadTimeDays,
        @Schema(description = "Prazo máximo típico em dias", example = "5")
        Integer maxLeadTimeDays,
        @Schema(description = "Maior volume útil entre as impressoras do Maker")
        BuildVolumeDto maxBuildVolume,
        @Schema(description = "Resumo de cotação instantânea calculada quando o pedido possui DNA do modelo")
        InstantPrintQuoteResponse.MakerInstantQuoteItem instantQuote
) {
    public CompanyChoiceDTO(
            String name,
            String guid,
            AddressDTO address,
            Distance distance,
            boolean online
    ) {
        this(
                name, guid, address, distance, online,
                null, false, 1, true, List.of(), BigDecimal.ZERO, 0, 0, false, null, 2, 5, null, null
        );
    }

    public CompanyChoiceDTO(
            String name,
            String guid,
            AddressDTO address,
            Distance distance,
            boolean online,
            String primaryPrinter,
            boolean supportsMulticolor,
            int maxMulticolorSlots,
            boolean acceptingCustomQuotes,
            List<MaterialType> materials,
            BigDecimal ratingAvg,
            int reviewsCount,
            int printsDone,
            boolean verified,
            String avatarUrl,
            Integer minLeadTimeDays,
            Integer maxLeadTimeDays,
            BuildVolumeDto maxBuildVolume
    ) {
        this(
                name, guid, address, distance, online, primaryPrinter,
                supportsMulticolor, maxMulticolorSlots, acceptingCustomQuotes, materials,
                ratingAvg, reviewsCount, printsDone, verified, avatarUrl,
                minLeadTimeDays, maxLeadTimeDays, maxBuildVolume, null
        );
    }

    @Schema(description = "Distância calculada entre o Maker e o endereço de entrega")
    public record Distance(
            @Schema(description = "Valor numérico da distância", example = "4.25")
            BigDecimal value,
            @Schema(description = "Unidade de medida da distância", example = "km")
            String unit
    ) {
    }
}
