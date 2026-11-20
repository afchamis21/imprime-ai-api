package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.ReviewTargetType;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "REVIEW")
public class Review extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_ID", nullable = false)
    private Long id;

    @Column(name = "AUTHOR_USER_ID", nullable = false)
    private Long authorUserId;

    @Convert(converter = ReviewTargetType.Converter.class)
    @Column(name = "TARGET_TYPE", nullable = false, length = 20)
    private ReviewTargetType targetType;

    @Column(name = "TARGET_ID", nullable = false)
    private Long targetId;

    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "RATING", nullable = false)
    private Integer rating;

    @Column(name = "COMMENT", nullable = false, columnDefinition = "text")
    private String comment;
}
