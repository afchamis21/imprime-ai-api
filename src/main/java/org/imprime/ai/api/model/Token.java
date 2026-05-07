package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.type.NumericBooleanConverter;
import org.imprime.ai.api.model.base.Auditable;

import java.time.Instant;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "TOKENS")
public class Token extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TOKEN_ID", nullable = false)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "TOKEN_HASH", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "\"TYPE\"", nullable = false, length = 5)
    private String type;

    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "REVOKED", nullable = false)
    private Boolean revoked;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;
}