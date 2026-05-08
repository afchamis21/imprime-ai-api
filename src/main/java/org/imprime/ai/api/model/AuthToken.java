package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.type.NumericBooleanConverter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;
import org.imprime.ai.api.model.enums.CodeAttribute;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "AUTH_TOKEN")
public class AuthToken extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUTH_TOKEN_ID", nullable = false)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "TOKEN_HASH", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "TYPE", nullable = false, length = 5)
    @Convert(converter = TokenType.Converter.class)
    private TokenType type;

    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "REVOKED", nullable = false)
    private Boolean revoked;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Transient
    private String token;

    @Transient
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Getter
    @RequiredArgsConstructor
    public enum TokenType implements CodeAttribute {
        ACCESS("A"), REFRESH("R");

        private final String code;

        public static class Converter extends CodeAttributeConverter<TokenType> {
            protected Converter() {
                super(TokenType.class);
            }
        }
    }
}