package org.imprime.ai.api.http.response.model3d;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ScadParameterType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Definição dinâmica de um parâmetro customizável OpenSCAD")
public record ScadParameterDefinition(
        @Schema(description = "Nome da variável no código OpenSCAD", example = "radius")
        String name,
        @Schema(description = "Rótulo legível para exibição no formulário", example = "Raio (mm)")
        String label,
        @Schema(description = "Tipo de controle do parâmetro", example = "NUMBER")
        ScadParameterType type,
        @Schema(description = "Valor padrão inicial", example = "10")
        Object defaultValue,
        @Schema(description = "Valor mínimo permitido (para tipo NUMBER)", example = "2")
        BigDecimal min,
        @Schema(description = "Valor máximo permitido (para tipo NUMBER)", example = "100")
        BigDecimal max,
        @Schema(description = "Incremento/passo (para tipo NUMBER)", example = "1")
        BigDecimal step,
        @Schema(description = "Unidade de medida exibida na UI", example = "mm")
        String unit,
        @Schema(description = "Opções válidas quando type = SELECT")
        List<String> options
) {
}
