package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "MODEL_CUSTOMIZATION")
public class ModelCustomization extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MODEL_CUSTOMIZATION_ID", nullable = false)
    private Long id;

    @Column(name = "MODEL_3D_ID", nullable = false, unique = true)
    private Long model3dId;

    @Column(name = "SCAD_CODE", nullable = false, columnDefinition = "text")
    private String scadCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "PARAMETERS_SCHEMA", nullable = false, columnDefinition = "jsonb")
    private String parametersSchemaJson;
}
