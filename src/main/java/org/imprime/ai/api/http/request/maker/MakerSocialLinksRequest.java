package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Links e canais sociais do Maker")
public record MakerSocialLinksRequest(
        @Schema(description = "Perfil no Instagram", example = "@studioimpressao3d")
        @Size(max = 120, message = "Instagram deve ter no máximo 120 caracteres")
        String instagram,

        @Schema(description = "Telefone WhatsApp comercial", example = "11987654321")
        @Size(max = 30, message = "WhatsApp deve ter no máximo 30 caracteres")
        String whatsapp,

        @Schema(description = "URL do website oficial", example = "https://studioimpressao3d.com.br")
        @Size(max = 255, message = "Website deve ter no máximo 255 caracteres")
        String website,

        @Schema(description = "URL do canal no YouTube")
        @Size(max = 255, message = "YouTube deve ter no máximo 255 caracteres")
        String youtube
) {
}
