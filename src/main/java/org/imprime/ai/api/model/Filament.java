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
@Table(name = "FILAMENT")
public class Filament extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FILAMENT_ID", nullable = false)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "ASSET_ID")
    private Long assetId;

    @Column(name = "STOCK", nullable = false, precision = 10, scale = 2)
    private BigDecimal stock;

    @Column(name = "FILAMENT_TYPE", nullable = false, length = 100)
    private String filamentType;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;
}