package org.imprime.ai.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

@Getter
@Setter
@Entity
@Table(name = "ORDER_FILE_ASSET")
public class OrderFileAsset extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_FILE_ASSET_ID", nullable = false)
    private Long id;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "FILE_ASSET_ID", nullable = false)
    private Long fileAssetId;

    @Column(name = "PLATE_NUMBER", nullable = false)
    private Integer plateNumber = 1;

    @Column(name = "PLATE_NAME", length = 180)
    private String plateName;

    @Column(name = "MATERIAL", length = 60)
    private String material;

    @Column(name = "COLOR_HEX", length = 30)
    private String colorHex;

    @Column(name = "INFILL_PERCENT")
    private Integer infillPercent;

    @Column(name = "SUPPORTS")
    private Boolean supports;

    @Column(name = "SCALE_X", nullable = false)
    private Double scaleX = 1.0;

    @Column(name = "SCALE_Y", nullable = false)
    private Double scaleY = 1.0;

    @Column(name = "SCALE_Z", nullable = false)
    private Double scaleZ = 1.0;

    @Column(name = "IS_MULTI_COLOR", nullable = false)
    private Boolean isMultiColor = Boolean.FALSE;

    @Column(name = "PAINTING_INSTRUCTIONS", columnDefinition = "text")
    private String paintingInstructions;
}
