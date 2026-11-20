package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.FavoriteTargetType;

@Getter
@Setter
@Entity
@Table(name = "USER_FAVORITE")
public class UserFavorite extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_FAVORITE_ID", nullable = false)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Convert(converter = FavoriteTargetType.Converter.class)
    @Column(name = "TARGET_TYPE", nullable = false, length = 20)
    private FavoriteTargetType targetType;

    @Column(name = "TARGET_ID", nullable = false)
    private Long targetId;
}
