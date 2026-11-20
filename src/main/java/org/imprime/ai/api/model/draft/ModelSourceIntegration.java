package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.ModelSourceProvider;
import org.imprime.ai.api.model.enums.ModelSourceStatus;

import java.time.Instant;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "MODEL_SOURCE_INTEGRATION")
public class ModelSourceIntegration extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MODEL_SOURCE_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "NAME", nullable = false, length = 120)
    private String name;

    @Convert(converter = ModelSourceProvider.Converter.class)
    @Column(name = "PROVIDER", nullable = false, length = 30)
    private ModelSourceProvider provider;

    @Column(name = "URL", nullable = false, length = 500)
    private String url;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "ENABLED", nullable = false)
    private Boolean enabled;

    @Column(name = "AUTO_SYNC", nullable = false)
    private Boolean autoSync;

    @Column(name = "SYNC_INTERVAL_HOURS", nullable = false)
    private Integer syncIntervalHours;

    @Convert(converter = ModelSourceStatus.Converter.class)
    @Column(name = "SYNC_STATUS", nullable = false, length = 20)
    private ModelSourceStatus syncStatus;

    @Column(name = "LAST_SYNCED_AT")
    private Instant lastSyncedAt;

    @Column(name = "MODELS_COUNT", nullable = false)
    private Integer modelsCount;

    @Column(name = "IMPORTED_FILES_COUNT", nullable = false)
    private Integer importedFilesCount;

    @Column(name = "ENCRYPTED_API_TOKEN", length = 1000)
    private String encryptedApiToken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TAGS", columnDefinition = "jsonb")
    private String tagsJson;
}
