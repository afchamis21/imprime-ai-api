package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;
import org.imprime.ai.api.model.enums.CodeAttribute;

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

    @Column(name = "ASSET_URL", length = 1000)
    private String assetUrl;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Convert(converter = CodeAttributeConverter.class)
    @Column(name = "ASSET_TYPE")
    private AssetType assetType;

    @Getter
    @RequiredArgsConstructor
    public enum AssetType implements CodeAttribute {
        MODEL("MO");
        private final String code;

        public static class Converter extends CodeAttributeConverter<AssetType> {
            protected Converter() {
                super(AssetType.class);
            }
        }
    }
}