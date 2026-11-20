package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.ShippingOption;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Atualização parcial (PATCH) do perfil operacional do estúdio Maker")
public record PatchMakerProfileRequest(
        @Schema(description = "Nome do estúdio")
        @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres")
        String name,

        @Schema(description = "Handle público (@handle)")
        @Size(max = 60, message = "O handle deve ter no máximo 60 caracteres")
        String handle,

        @Schema(description = "Slogan curto")
        @Size(max = 200, message = "O slogan deve ter no máximo 200 caracteres")
        String tagline,

        @Schema(description = "Biografia pública")
        @Size(max = 2000, message = "A biografia deve ter no máximo 2000 caracteres")
        String bio,

        @Schema(description = "Tipo da chave PIX")
        PixKeyType pixKeyType,

        @Schema(description = "Chave PIX")
        @Size(max = 140, message = "A chave PIX deve ter no máximo 140 caracteres")
        String pixKey,

        @Schema(description = "Cidade do estúdio")
        @Size(min = 2, max = 100, message = "A cidade deve ter entre 2 e 100 caracteres")
        String city,

        @Schema(description = "UF do estúdio (2 letras maiúsculas)")
        @Pattern(regexp = "^[A-Z]{2}$", message = "UF deve conter 2 letras maiúsculas")
        String state,

        @Schema(description = "URL do avatar obtida via `/media/upload`")
        String avatarUrl,

        @Schema(description = "URL da imagem de capa obtida via `/media/upload`")
        String coverUrl,

        @Schema(description = "Capacidades e tecnologias oferecidas")
        List<MakerCapability> capabilities,

        @Schema(description = "Tempo médio de resposta em horas")
        @Min(value = 1, message = "O tempo médio de resposta deve ser pelo menos 1 hora")
        Integer responseTimeHours,

        @Schema(description = "Prazo mínimo de produção em dias")
        @Min(value = 1, message = "O prazo mínimo é 1 dia")
        Integer minLeadTimeDays,

        @Schema(description = "Prazo máximo de produção em dias")
        @Min(value = 1, message = "O prazo máximo é 1 dia")
        Integer maxLeadTimeDays,

        @Schema(description = "Valor mínimo de pedido em BRL")
        @PositiveOrZero(message = "O valor mínimo de pedido não pode ser negativo")
        BigDecimal minOrderValue,

        @Schema(description = "Modalidades de envio habilitadas")
        List<ShippingOption> shippingOptions,

        @Schema(description = "Pausa temporária de pedidos (modo férias)")
        Boolean vacationMode,

        @Schema(description = "Mensagem exibida no modo férias")
        @Size(max = 500, message = "A mensagem de férias deve ter no máximo 500 caracteres")
        String vacationMessage,

        @Schema(description = "Indica se aceita cotações sob demanda")
        Boolean acceptingCustomQuotes,

        @Schema(description = "Indica se o portfólio público está visível")
        Boolean publicPortfolioEnabled,

        @Schema(description = "Links sociais e contato público")
        @Valid
        MakerSocialLinksRequest socialLinks
) {
}
