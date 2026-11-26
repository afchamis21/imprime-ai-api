package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "REVIEW_REPLY")
public class ReviewReply extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_REPLY_ID")
    private Long id;

    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

    @Column(name = "AUTHOR_USER_ID", nullable = false)
    private Long authorUserId;

    @Column(name = "COMMENT", nullable = false, columnDefinition = "text")
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "IMAGE_URLS", nullable = false, columnDefinition = "jsonb")
    private List<String> imageUrls = List.of();
}
