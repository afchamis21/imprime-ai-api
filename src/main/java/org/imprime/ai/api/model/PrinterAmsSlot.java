package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

@Getter
@Setter
@Entity
@Table(name = "PRINTER_AMS_SLOT")
public class PrinterAmsSlot extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRINTER_AMS_SLOT_ID", nullable = false)
    private Long id;

    @Column(name = "ASSET_ID", nullable = false)
    private Long assetId;

    @Column(name = "SLOT_NUMBER", nullable = false)
    private Integer slotNumber;

    @Column(name = "FILAMENT_ID")
    private Long filamentId;

    @Column(name = "LABEL", length = 50)
    private String label;
}
