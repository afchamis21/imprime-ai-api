package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.QueuePriority;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "PRINT_QUEUE_ITEM")
public class PrintQueueItem extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRINT_QUEUE_ITEM_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "ASSET_ID")
    private Long assetId;

    @Column(name = "MAKER_ARCHIVE_FILE_ID")
    private Long makerArchiveFileId;

    @Column(name = "FILAMENT_ID")
    private Long filamentId;

    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "MAKER_PROJECT_ID")
    private Long makerProjectId;

    @Column(name = "POSITION", nullable = false)
    private Integer position;

    @Column(name = "TITLE", nullable = false, length = 150)
    private String title;

    @Column(name = "FILE_NAME", length = 255)
    private String fileName;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "MATERIAL", length = 30)
    private MaterialType material;

    @Column(name = "COLOR_NAME", length = 60)
    private String colorName;

    @Column(name = "COLOR_HEX", length = 7)
    private String colorHex;

    @Column(name = "ESTIMATED_WEIGHT_GRAMS", precision = 10, scale = 2)
    private BigDecimal estimatedWeightGrams;

    @Column(name = "ESTIMATED_TIME_MINUTES")
    private Integer estimatedTimeMinutes;

    @Column(name = "PROGRESS_PERCENT", nullable = false)
    private Integer progressPercent = 0;

    @Convert(converter = PrintJobStatus.Converter.class)
    @Column(name = "JOB_STATUS", nullable = false, length = 20)
    private PrintJobStatus jobStatus = PrintJobStatus.QUEUED;

    @Convert(converter = QueuePriority.Converter.class)
    @Column(name = "PRIORITY", nullable = false, length = 20)
    private QueuePriority priority = QueuePriority.NORMAL;

    @Column(name = "COPIES", nullable = false)
    private Integer copies = 1;

    @Column(name = "COMPLETED_COPIES", nullable = false)
    private Integer completedCopies = 0;

    @Column(name = "CUSTOMER_NAME", length = 120)
    private String customerName;

    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;

    @Column(name = "SCHEDULED_START_AT")
    private Instant scheduledStartAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version = 0L;
}
