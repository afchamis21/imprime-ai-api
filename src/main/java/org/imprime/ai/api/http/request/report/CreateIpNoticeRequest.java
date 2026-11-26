package org.imprime.ai.api.http.request.report;

import jakarta.validation.constraints.*;
import org.imprime.ai.api.model.enums.ReportTargetType;

public record CreateIpNoticeRequest(
        @NotBlank @Size(max = 150) String claimantName,
        @NotBlank @Email @Size(max = 254) String claimantEmail,
        @NotNull ReportTargetType targetType,
        @NotBlank @Size(max = 200) String targetIdOrSlug,
        @NotBlank @Size(max = 2000) String protectedWork,
        @NotBlank @Size(min = 10, max = 4000) String justification,
        @Size(max = 3) java.util.List<@NotBlank @Size(max = 500) String> evidenceUrls,
        @NotNull @AssertTrue Boolean goodFaith
) {
}
