package org.imprime.ai.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "MELHOR_ENVIO_CREDENTIAL")
public class MelhorEnvioCredential {
    @Id
    @Column(name = "CREDENTIAL_ID")
    private Short id;

    @Column(name = "ENCRYPTED_ACCESS_TOKEN", nullable = false, columnDefinition = "text")
    private String encryptedAccessToken;

    @Column(name = "ENCRYPTED_REFRESH_TOKEN", columnDefinition = "text")
    private String encryptedRefreshToken;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;
}
