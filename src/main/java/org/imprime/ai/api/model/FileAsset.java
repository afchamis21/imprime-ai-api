package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.StatusCd;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "FILE_ASSET")
public class FileAsset extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FILE_ASSET_ID", nullable = false)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "IMAGE_URL", length = 1000)
    private String imageUrl;

    @Column(name = "NAME", nullable = false)
    private String name;
}