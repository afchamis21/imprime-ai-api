package org.imprime.ai.api.model;

import jakarta.persistence.Column;
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

@Getter
@Setter
@Entity
@Table(name = "MAKER_QUOTE_SETTINGS")
public class MakerQuoteSettings extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAKER_QUOTE_SETTINGS_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "QUOTE_SETTINGS_ID", nullable = false)
    private Long quoteSettingsId;

    @Column(name = "ENABLED", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "EXECUTION_ORDER", nullable = false)
    private Integer executionOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "CONFIG", columnDefinition = "jsonb", nullable = false)
    private String configJson = "{}";
}
