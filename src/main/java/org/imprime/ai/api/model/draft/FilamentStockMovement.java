package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.StockAdjustmentReason;

import java.math.BigDecimal;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "FILAMENT_STOCK_MOVEMENT")
public class FilamentStockMovement extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FILAMENT_STOCK_MOVEMENT_ID", nullable = false)
    private Long id;

    @Column(name = "FILAMENT_ID", nullable = false)
    private Long filamentId;

    @Column(name = "DELTA_GRAMS", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaGrams;

    @Column(name = "BALANCE_AFTER_GRAMS", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfterGrams;

    @Convert(converter = StockAdjustmentReason.Converter.class)
    @Column(name = "REASON", nullable = false, length = 30)
    private StockAdjustmentReason reason;

    @Column(name = "PRINT_QUEUE_ITEM_ID")
    private Long printQueueItemId;

    @Column(name = "NOTES", length = 300)
    private String notes;
}
