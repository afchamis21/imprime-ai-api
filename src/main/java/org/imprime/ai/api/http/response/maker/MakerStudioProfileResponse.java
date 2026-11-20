package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.ShippingOption;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Configurações privadas e perfil operacional do estúdio do Maker autenticado")
public record MakerStudioProfileResponse(
        @Schema(description = "GUID único da empresa/estúdio")
        String guid,
        @Schema(description = "Slug amigável do estúdio", example = "studio-impressao-3d")
        String slug,
        @Schema(description = "Handle público (@handle)", example = "@studioimpressao3d")
        String handle,
        @Schema(description = "Nome do estúdio", example = "Studio Impressão 3D")
        String name,
        @Schema(description = "Slogan curto")
        String tagline,
        @Schema(description = "Biografia pública")
        String bio,
        @Schema(description = "Tipo da chave PIX configurada", example = "EMAIL")
        PixKeyType pixKeyType,
        @Schema(description = "Chave PIX mascarada para exibição segura", example = "fin***@studio3d.com.br")
        String pixKeyMasked,
        @Schema(description = "Cidade do estúdio", example = "São Paulo")
        String city,
        @Schema(description = "UF do estúdio", example = "SP")
        String state,
        @Schema(description = "URL do avatar")
        String avatarUrl,
        @Schema(description = "URL da imagem de capa")
        String coverUrl,
        @Schema(description = "Capacidades e tecnologias oferecidas")
        List<MakerCapability> capabilities,
        @Schema(description = "Tempo médio de resposta em horas", example = "2")
        Integer responseTimeHours,
        @Schema(description = "Prazo mínimo de produção em dias", example = "2")
        Integer minLeadTimeDays,
        @Schema(description = "Prazo máximo de produção em dias", example = "5")
        Integer maxLeadTimeDays,
        @Schema(description = "Valor mínimo de pedido em BRL", example = "25.00")
        BigDecimal minOrderValue,
        @Schema(description = "Modalidades de envio habilitadas")
        List<ShippingOption> shippingOptions,
        @Schema(description = "Modo férias ativo", example = "false")
        boolean vacationMode,
        @Schema(description = "Mensagem exibida durante o modo férias")
        String vacationMessage,
        @Schema(description = "Indica se aceita cotações personalizadas", example = "true")
        boolean acceptingCustomQuotes,
        @Schema(description = "Indica se o portfólio público está visível", example = "true")
        boolean publicPortfolioEnabled,
        @Schema(description = "Links sociais e canais de contato")
        MakerSocialLinksDto socialLinks
) {
}
