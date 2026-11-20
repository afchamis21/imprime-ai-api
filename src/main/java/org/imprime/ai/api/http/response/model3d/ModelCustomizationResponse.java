package org.imprime.ai.api.http.response.model3d;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Dados de customização paramétrica OpenSCAD de um modelo 3D")
public record ModelCustomizationResponse(
        @Schema(description = "GUID do modelo 3D", example = "7f9c24ef-1000-4000-8000-000000000001")
        String modelGuid,
        @Schema(description = "Código-fonte OpenSCAD parametrizado")
        String scadCode,
        @Schema(description = "Lista dinâmica de definições de parâmetros para geração dos controles na UI")
        List<ScadParameterDefinition> parameters
) {
}
