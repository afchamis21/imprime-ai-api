package org.imprime.ai.api.model.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.enums.StatusCd;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class Auditable {
    @Column(name = "GUID", length = 20)
    private String guid;

    @Column(name = "STATUS", length = 5)
    @Convert(converter = StatusCd.Converter.class)
    private StatusCd status;

    @Column(name = "CREATE_DT", nullable = false, updatable = false)
    private Instant createDt;

    @Column(name = "CREATE_USER", length = 100, updatable = false)
    private String createUser;

    @Column(name = "UPDATE_DT")
    private Instant updateDt;

    @Column(name = "UPDATE_USER", length = 100)
    private String updateUser;

    @PrePersist
    protected void beforeCreate() {
        Instant now = Instant.now();
        if (createDt == null) {
            createDt = now;
        }

        if (updateDt == null) {
            updateDt = now;
        }

        // TODO Once I have a service context
        String user = "SYSTEM";
        if (createUser == null) {
            createUser = user;
        }

        if (updateUser == null) {
            updateUser = user;
        }

        if (status == null) {
            status = StatusCd.ACTIVE;
        }

        if (guid == null || guid.isEmpty()) {
            guid = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void beforeUpdate() {
        updateDt = Instant.now();
        updateUser = "SYSTEM";
        // TODO Once I have a service context
    }
}
