package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.ReviewTargetType;

@Getter
@Setter
@Entity
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "IMAGE_URLS", nullable = false, columnDefinition = "jsonb")
    private List<String> imageUrls = List.of();
}
