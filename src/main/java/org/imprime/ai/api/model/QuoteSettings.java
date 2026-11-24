package org.imprime.ai.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.QuoteModuleType;

@Getter
@Setter
@Entity
@Table(name = "QUOTE_SETTINGS")
public class QuoteSettings extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUOTE_SETTINGS_ID", nullable = false)
    private Long id;

    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "TITLE", nullable = false, length = 100)
    private String title;

    @Column(name = "DESCRIPTION", nullable = false, length = 500)
    private String description;

    @Convert(converter = QuoteModuleType.Converter.class)
    @Column(name = "QUOTE_TYPE", nullable = false, length = 30)
    private QuoteModuleType quoteType;

    @Column(name = "PHASE_ORDER", nullable = false)
    private Integer phaseOrder;

    @Column(name = "EXECUTION_ORDER", nullable = false)
    private Integer executionOrder;

    @Column(name = "QUOTE_CALCULATOR_CLASS", nullable = false, length = 255)
    private String quoteCalculatorClass;

    @Column(name = "DEFAULT_ENABLED", nullable = false)
    private Boolean defaultEnabled = Boolean.TRUE;

    @Column(name = "CAN_DISABLE", nullable = false)
    private Boolean canDisable = Boolean.TRUE;

    @Column(name = "ALLOW_QUOTE_OVERRIDE", nullable = false)
    private Boolean allowQuoteOverride = Boolean.TRUE;

    @Column(name = "MUTUAL_EXCLUSION_GROUP", length = 50)
    private String mutualExclusionGroup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "DEFAULT_CONFIG", columnDefinition = "jsonb", nullable = false)
    private String defaultConfigJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "UI_SCHEMA", columnDefinition = "jsonb", nullable = false)
    private String uiSchemaJson = "{}";
}
