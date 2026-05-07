package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ASSET")
public class Asset extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ASSET_ID", nullable = false)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "MODEL", nullable = false)
    private String model;

    @Column(name = "MAKE", nullable = false)
    private String make;

    @Column(name = "MANUFACTURE_YEAR", nullable = false)
    private Short manufactureYear;

    @Column(name = "TRAY_WIDTH", precision = 10, scale = 2)
    private BigDecimal trayWidth;

    @Column(name = "TRAY_DEPTH", precision = 10, scale = 2)
    private BigDecimal trayDepth;
}