package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.ShippingOption;

import java.math.BigDecimal;
import java.time.Instant;
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
        MakerSocialLinksDto socialLinks,
        @Schema(description = "Impressora principal do estúdio", example = "Bambu Lab A1")
        String primaryPrinter,
        @Schema(description = "GUID da impressora principal vinculada ao catálogo")
        String primaryPrinterCatalogGuid,
        @Schema(description = "Indica se a oficina/impressora suporta impressão multicolor", example = "true")
        boolean supportsMulticolor,
        @Schema(description = "Número máximo de cores/slots simultâneos", example = "4")
        int maxMulticolorSlots,
        @Schema(description = "Materiais operados pelo Maker")
        List<MaterialType> materials,
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
    public MakerStudioProfileResponse(
            String guid,
            String slug,
            String handle,
            String name,
            String tagline,
            String bio,
            PixKeyType pixKeyType,
            String pixKeyMasked,
            String city,
            String state,
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
            boolean publicPortfolioEnabled,
            MakerSocialLinksDto socialLinks
    ) {
        this(
                guid, slug, handle, name, tagline, bio, pixKeyType, pixKeyMasked, city, state,
                avatarUrl, coverUrl, capabilities, responseTimeHours, minLeadTimeDays, maxLeadTimeDays,
                minOrderValue, shippingOptions, vacationMode, vacationMessage, acceptingCustomQuotes,
                publicPortfolioEnabled, socialLinks,
                null, null, false, 1, List.of(), false, false, false, false, null, null
        );
    }
}
