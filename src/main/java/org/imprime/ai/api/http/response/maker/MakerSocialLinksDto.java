package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Links e canais de contato público do Maker")
public record MakerSocialLinksDto(
        @Schema(description = "Perfil no Instagram", example = "@studioimpressao3d")
        String instagram,
        @Schema(description = "Número/link de WhatsApp comercial", example = "11987654321")
        String whatsapp,
        @Schema(description = "Website oficial", example = "https://studioimpressao3d.com.br")
        String website,
        @Schema(description = "Canal no YouTube", example = "https://youtube.com/@studio3d")
        String youtube
) {
}
