package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.TicketPriority;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "CONTENT_REPORT")
public class ContentReport extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONTENT_REPORT_ID", nullable = false)
    private Long id;

    @Column(name = "PROTOCOL", nullable = false, unique = true, length = 40)
    private String protocol;

    @Column(name = "REPORTER_USER_ID", nullable = false)
    private Long reporterUserId;

    @Convert(converter = ReportTargetType.Converter.class)
    @Column(name = "TARGET_TYPE", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "TARGET_ID", nullable = false)
    private Long targetId;

    @Column(name = "TARGET_GUID", nullable = false, length = 64)
    private String targetGuid;

    @Column(name = "TARGET_NAME", nullable = false, length = 200)
    private String targetName;

    @Convert(converter = ReportReason.Converter.class)
    @Column(name = "REASON", nullable = false, length = 40)
    private ReportReason reason;

    @Column(name = "DESCRIPTION", nullable = false, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "EVIDENCE_URLS", nullable = false, columnDefinition = "jsonb")
    private String evidenceUrlsJson = "[]";

    @Convert(converter = ReportStatus.Converter.class)
    @Column(name = "REPORT_STATUS", nullable = false, length = 20)
    private ReportStatus reportStatus = ReportStatus.OPEN;

    @Convert(converter = TicketPriority.Converter.class)
    @Column(name = "PRIORITY", nullable = false, length = 20)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "ASSIGNED_ADMIN_ID")
    private Long assignedAdminId;

    @Column(name = "RESOLUTION_NOTES", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "REVIEWED_BY_USER_ID")
    private Long reviewedByUserId;

    @Column(name = "REVIEWED_AT")
    private Instant reviewedAt;
}
