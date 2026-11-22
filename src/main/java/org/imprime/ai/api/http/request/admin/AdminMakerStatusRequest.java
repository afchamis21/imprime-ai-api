package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.StatusCd;

@Schema(description = "Payload administrativo para atualizar status, aprovação ou selo verificado de um Maker")
public record AdminMakerStatusRequest(
        MakerApprovalStatus approvalStatus,
        StatusCd status,
        Boolean verified,
        @Size(max = 2000)
        String reason
) {
}
