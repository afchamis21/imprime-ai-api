package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;

import java.util.List;

@Schema(description = "Dados do pedido recém-iniciado e lista de Makers recomendados")
public record StartOrderResponse(
        @Schema(description = "GUID único do pedido criado", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        String orderGuid,
        @Schema(description = "Endereço de entrega selecionado para o pedido")
        AddressDTO shipTo,
        @Schema(description = "Lista de empresas (Makers) disponíveis para cotação")
        List<CompanyChoiceDTO> companies
) {
}
