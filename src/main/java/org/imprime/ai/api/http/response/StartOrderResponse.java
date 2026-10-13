package org.imprime.ai.api.http.response;

import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;

import java.util.List;

public record StartOrderResponse(String orderGuid, AddressDTO shipTo, List<CompanyChoiceDTO> companies) {
}
