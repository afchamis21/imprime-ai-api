package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MakerCapability;
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
        MakerSocialLinksDto socialLinks
) {
}
