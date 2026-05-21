package org.imprime.ai.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

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

    // TODO Document Type

    @Column(name = "ADDRESS_ID")
    private Long addressId;

    // TODO Cover Picture ID

    // TODO Logo Picture ID

    // TODO Description

    // TODO Tags

    // TODO Porfolio

    @Column(name = "OWNER_ID")
    private Long ownerId;
}