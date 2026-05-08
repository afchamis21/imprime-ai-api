package org.imprime.ai.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.imprime.ai.api.model.base.Auditable;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "COMPANY")
public class Company extends Auditable {
    @Id
    @Column(name = "COMPANY_ID", nullable = false)
    private Long id;

    @Column(name = "NAME", length = 100)
    private String name;

    @Column(name = "CNPJ", length = 20)
    private String cnpj;

    @Column(name = "ADDRESS_ID")
    private Long addressId;

    @Column(name = "OWNER_ID")
    private Long ownerId;
}