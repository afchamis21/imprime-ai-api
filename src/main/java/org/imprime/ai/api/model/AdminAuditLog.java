package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.AuditAction;

@Getter
@Setter
@Entity
@Table(name = "ADMIN_AUDIT_LOG")
public class AdminAuditLog extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_LOG_ID", nullable = false)
    private Long id;

    @Column(name = "ADMIN_USER_ID", nullable = false)
    private Long adminUserId;

    @Column(name = "ADMIN_EMAIL", nullable = false)
    private String adminEmail;

    @Column(name = "ADMIN_NAME", nullable = false, length = 200)
    private String adminName;

    @Convert(converter = AuditAction.Converter.class)
    @Column(name = "ACTION", nullable = false, length = 60)
    private AuditAction action;

    @Column(name = "TARGET_TYPE", nullable = false, length = 40)
    private String targetType;

    @Column(name = "TARGET_GUID", length = 64)
    private String targetGuid;

    @Column(name = "REASON", columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "BEFORE_STATE", columnDefinition = "jsonb")
    private String beforeStateJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "AFTER_STATE", columnDefinition = "jsonb")
    private String afterStateJson;

    @Column(name = "IP_ADDRESS", length = 64)
    private String ipAddress;

    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;
}
