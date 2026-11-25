package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Item resumido de um Maker no catálogo público")
public record MakerCatalogItemResponse(
        @Schema(description = "GUID único do Maker (empresa)", example = "550e8400-e29b-41d4-a716-446655440000")
        String guid,
        @Schema(description = "Slug amigável para URL pública", example = "studio-impressao-3d")
        String slug,
        @Schema(description = "Nome do Maker/estúdio", example = "Studio Impressão 3D")
        String name,
        @Schema(description = "Descrição da especialidade principal", example = "Peças mecânicas e protótipos")
        String specialty,
        @Schema(description = "Categoria de especialidade", example = "mechanical")
        String specialtyCategory,
        @Schema(description = "Resumo curto ou equipamento destaque", example = "Prototipagem rápida em FDM e SLA")
        String summary,
        @Schema(description = "Impressora principal do estúdio", example = "Bambu Lab X1 Carbon")
        String primaryPrinter,
        @Schema(description = "Cidade do estúdio", example = "São Paulo")
        String city,
        @Schema(description = "UF do estúdio", example = "SP")
        String state,
        @Schema(description = "Distância calculada em km quando há referência geográfica", example = "8.4")
        BigDecimal distanceKm,
        @Schema(description = "Preço base estimado por peça/hora em BRL", example = "18.00")
        BigDecimal basePrice,
        @Schema(description = "Nota média (0.00 a 5.00)", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Total de avaliações recebidas", example = "312")
        int reviewsCount,
        @Schema(description = "Total de pedidos concluídos", example = "1840")
        int completedOrdersCount,
        @Schema(description = "Prazo mínimo típico em dias", example = "2")
        int minLeadTimeDays,
        @Schema(description = "Materiais operados pelo Maker")
        List<MaterialType> materials,
        @Schema(description = "Tags de destaque", example = "[\"Prototipagem\", \"Alta Precisão\"]")
        List<String> tags,
        @Schema(description = "Selo de destaque (opcional)", example = "TOP_MAKER")
        String badge,
        @Schema(description = "URL da imagem de capa do cartão")
        String coverImageUrl,
        @Schema(description = "URL do avatar do Maker")
        String avatarUrl,
        @Schema(description = "Indica se o estúdio está online para atendimento imediato", example = "true")
        boolean online,
        @Schema(description = "Indica se o usuário autenticado favoritou este Maker", example = "false")
        boolean favorited,
        @Schema(description = "Indica se o Maker foi verificado pela plataforma", example = "true")
        boolean verified,
        @Schema(description = "Data de ingresso na plataforma em UTC (ISO-8601)", example = "2024-03-10T12:00:00Z")
        Instant createdAt,
        @Schema(description = "Latitude aproximada do endereço principal do estúdio", example = "-23.55052")
        Double latitude,
        @Schema(description = "Longitude aproximada do endereço principal do estúdio", example = "-46.63331")
        Double longitude,
        @Schema(description = "URLs de prévia das peças do portfólio público do Maker (até 6)")
        List<String> portfolioImages,
        @Schema(description = "Indica se suporta impressão multicolor/AMS", example = "true")
        boolean supportsMulticolor,
        @Schema(description = "Número máximo de cores/slots simultâneos", example = "4")
        int maxMulticolorSlots,
        @Schema(description = "Indica se aceita orçamentos personalizados", example = "true")
        boolean acceptingCustomQuotes,
        @Schema(description = "GUID da impressora principal vinculada ao catálogo")
        String primaryPrinterCatalogGuid,
        @Schema(description = "Maior volume útil disponível na frota do Maker")
        BuildVolumeDto maxBuildVolume,
        @Schema(description = "Oferece pós-processamento")
        boolean offersPostProcessing,
        @Schema(description = "Oferece pintura")
        boolean offersPainting,
        @Schema(description = "Oferece modelagem CAD")
        boolean offersCadModeling,
        @Schema(description = "Aceita pedidos expressos")
        boolean supportsExpressOrders
) {
    public MakerCatalogItemResponse(
            String guid,
            String slug,
            String name,
            String specialty,
            String specialtyCategory,
            String summary,
            String primaryPrinter,
            String city,
            String state,
            BigDecimal distanceKm,
            BigDecimal basePrice,
            BigDecimal rating,
            int reviewsCount,
            int completedOrdersCount,
            int minLeadTimeDays,
            List<MaterialType> materials,
            List<String> tags,
            String badge,
            String coverImageUrl,
            String avatarUrl,
            boolean online,
            boolean favorited,
            boolean verified,
            Instant createdAt,
            Double latitude,
            Double longitude,
            List<String> portfolioImages
    ) {
        this(
                guid, slug, name, specialty, specialtyCategory, summary, primaryPrinter,
                city, state, distanceKm, basePrice, rating, reviewsCount, completedOrdersCount,
                minLeadTimeDays, materials, tags, badge, coverImageUrl, avatarUrl,
                online, favorited, verified, createdAt, latitude, longitude, portfolioImages,
                false, 1, true, null, null, false, false, false, false
        );
    }
}
