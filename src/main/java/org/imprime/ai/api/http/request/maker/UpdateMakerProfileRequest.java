package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.ShippingOption;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dados completos para substituição/atualização do perfil operacional do estúdio Maker (company extraída do JWT)")
public record UpdateMakerProfileRequest(
        @Schema(description = "Nome do estúdio/Maker", example = "Studio Impressão 3D", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do estúdio é obrigatório")
        @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres")
        String name,

        @Schema(description = "Handle público (@handle)", example = "@studioimpressao3d")
        @Size(max = 60, message = "O handle deve ter no máximo 60 caracteres")
        String handle,

        @Schema(description = "Slogan curto do estúdio")
        @Size(max = 200, message = "O slogan deve ter no máximo 200 caracteres")
        String tagline,

        @Schema(description = "Biografia pública do estúdio", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A biografia é obrigatória")
        @Size(max = 2000, message = "A biografia deve ter no máximo 2000 caracteres")
        String bio,

        @Schema(description = "Tipo da chave PIX para recebimentos", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O tipo da chave PIX é obrigatório")
        PixKeyType pixKeyType,

        @Schema(description = "Chave PIX para recebimentos", example = "financeiro@studio3d.com.br", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A chave PIX é obrigatória")
        @Size(max = 140, message = "A chave PIX deve ter no máximo 140 caracteres")
        String pixKey,

        @Schema(description = "Cidade do estúdio", example = "São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A cidade é obrigatória")
        @Size(min = 2, max = 100, message = "A cidade deve ter entre 2 e 100 caracteres")
        String city,

        @Schema(description = "UF do estúdio (2 letras maiúsculas)", example = "SP", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O estado (UF) é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "UF deve conter 2 letras maiúsculas")
        String state,

        @Schema(description = "URL do avatar obtida via `/media/upload`")
        String avatarUrl,

        @Schema(description = "URL da imagem de capa obtida via `/media/upload`")
        String coverUrl,

        @Schema(description = "Tecnologias e capacidades oferecidas")
        List<MakerCapability> capabilities,

        @Schema(description = "Tempo médio de resposta em horas", example = "2")
        @Min(value = 1, message = "O tempo médio de resposta deve ser pelo menos 1 hora")
        Integer responseTimeHours,

        @Schema(description = "Prazo mínimo de produção em dias", example = "2")
        @Min(value = 1, message = "O prazo mínimo é 1 dia")
        Integer minLeadTimeDays,

        @Schema(description = "Prazo máximo de produção em dias", example = "5")
        @Min(value = 1, message = "O prazo máximo é 1 dia")
        Integer maxLeadTimeDays,

        @Schema(description = "Valor mínimo de pedido em BRL", example = "25.00")
        @PositiveOrZero(message = "O valor mínimo de pedido não pode ser negativo")
        BigDecimal minOrderValue,

        @Schema(description = "Modalidades de envio aceitas")
        List<ShippingOption> shippingOptions,

        @Schema(description = "Pausa temporária de novos pedidos (modo férias)", example = "false")
        Boolean vacationMode,

        @Schema(description = "Mensagem exibida aos compradores durante o modo férias")
        @Size(max = 500, message = "A mensagem de férias deve ter no máximo 500 caracteres")
        String vacationMessage,

        @Schema(description = "Indica se aceita cotações sob demanda", example = "true")
        Boolean acceptingCustomQuotes,

        @Schema(description = "Indica se o portfólio público está visível", example = "true")
        Boolean publicPortfolioEnabled,

        @Schema(description = "Links sociais e contato público")
        @Valid
        MakerSocialLinksRequest socialLinks
) {
}
