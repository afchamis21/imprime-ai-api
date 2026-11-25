package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ShippingOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Perfil público detalhado de um Maker")
public record MakerProfileResponse(
        @Schema(description = "GUID único do Maker (empresa)", example = "550e8400-e29b-41d4-a716-446655440000")
        String guid,
        @Schema(description = "Slug amigável do Maker", example = "studio-impressao-3d")
        String slug,
        @Schema(description = "Identificador público (@handle)", example = "@studioimpressao3d")
        String handle,
        @Schema(description = "Nome do estúdio", example = "Studio Impressão 3D")
        String name,
        @Schema(description = "Slogan curto", example = "Especialistas em Prototipagem Rápida e SLA")
        String tagline,
        @Schema(description = "Biografia pública completa")
        String bio,
        @Schema(description = "Cidade do estúdio", example = "São Paulo")
        String city,
        @Schema(description = "UF do estúdio", example = "SP")
        String state,
        @Schema(description = "Nota média (0.00 a 5.00)", example = "4.90")
        BigDecimal rating,
        @Schema(description = "Quantidade de avaliações", example = "128")
        int reviewsCount,
        @Schema(description = "Quantidade de impressões concluídas", example = "342")
        int printsDone,
        @Schema(description = "Data de cadastro do estúdio em UTC (ISO-8601)", example = "2023-03-15T10:00:00Z")
        Instant memberSince,
        @Schema(description = "URL do avatar")
        String avatarUrl,
        @Schema(description = "URL da imagem de capa")
        String coverUrl,
        @Schema(description = "Tecnologias e capacidades oferecidas")
        List<MakerCapability> capabilities,
        @Schema(description = "Tempo médio de resposta em horas", example = "2")
        Integer responseTimeHours,
        @Schema(description = "Prazo mínimo de produção em dias", example = "2")
        Integer minLeadTimeDays,
        @Schema(description = "Prazo máximo de produção em dias", example = "5")
        Integer maxLeadTimeDays,
        @Schema(description = "Valor mínimo de pedido em BRL", example = "25.00")
        BigDecimal minOrderValue,
        @Schema(description = "Modalidades de envio aceitas")
        List<ShippingOption> shippingOptions,
        @Schema(description = "Indica se o estúdio está em modo férias", example = "false")
        boolean vacationMode,
        @Schema(description = "Mensagem exibida durante o modo férias")
        String vacationMessage,
        @Schema(description = "Indica se aceita cotações personalizadas sob demanda", example = "true")
        boolean acceptingCustomQuotes,
        @Schema(description = "Indica se o usuário autenticado favoritou este Maker", example = "false")
        boolean favorited,
        @Schema(description = "Indica se o usuário autenticado é o proprietário deste estúdio", example = "false")
        boolean isOwner,
        @Schema(description = "Links sociais e contato público")
        MakerSocialLinksDto socialLinks,
        @Schema(description = "Indica se o estúdio está online agora", example = "true")
        boolean online,
        @Schema(description = "Impressora principal do estúdio", example = "Bambu Lab A1")
        String primaryPrinter,
        @Schema(description = "GUID da impressora principal vinculada ao catálogo")
        String primaryPrinterCatalogGuid,
        @Schema(description = "Indica se suporta impressão multicolor/AMS", example = "true")
        boolean supportsMulticolor,
        @Schema(description = "Número máximo de cores/slots simultâneos", example = "4")
        int maxMulticolorSlots,
        @Schema(description = "Materiais operados pelo Maker")
        List<MaterialType> materials,
        @Schema(description = "Maior volume útil entre as impressoras do Maker")
        BuildVolumeDto maxBuildVolume,
        @Schema(description = "Oferece pós-processamento")
        boolean offersPostProcessing,
        @Schema(description = "Oferece pintura")
        boolean offersPainting,
        @Schema(description = "Oferece modelagem CAD")
        boolean offersCadModeling,
        @Schema(description = "Aceita pedidos expressos")
        boolean supportsExpressOrders,
        @Schema(description = "Menor altura de camada atendida em mm", example = "0.08")
        BigDecimal minLayerHeightMm,
        @Schema(description = "Observações técnicas do estúdio")
        String technicalNotes
) {
    public MakerProfileResponse(
            String guid,
            String slug,
            String handle,
            String name,
            String tagline,
            String bio,
            String city,
            String state,
            BigDecimal rating,
            int reviewsCount,
            int printsDone,
            Instant memberSince,
            String avatarUrl,
            String coverUrl,
            List<MakerCapability> capabilities,
            Integer responseTimeHours,
            Integer minLeadTimeDays,
            Integer maxLeadTimeDays,
            BigDecimal minOrderValue,
            List<ShippingOption> shippingOptions,
            boolean vacationMode,
            String vacationMessage,
            boolean acceptingCustomQuotes,
            boolean favorited,
            boolean isOwner,
            MakerSocialLinksDto socialLinks,
            boolean online
    ) {
        this(
                guid, slug, handle, name, tagline, bio, city, state, rating, reviewsCount, printsDone,
                memberSince, avatarUrl, coverUrl, capabilities, responseTimeHours, minLeadTimeDays,
                maxLeadTimeDays, minOrderValue, shippingOptions, vacationMode, vacationMessage,
                acceptingCustomQuotes, favorited, isOwner, socialLinks, online,
                null, null, false, 1, List.of(), null, false, false, false, false, null, null
        );
    }
}
