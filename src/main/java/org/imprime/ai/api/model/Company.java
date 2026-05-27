package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.DocumentType;

@Getter
@Setter
@Entity
@Table(name = "COMPANY")
public class Company extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMPANY_ID", nullable = false)
    private Long id;

    @Column(name = "NAME", length = 100)
    private String name;

    @Column(name = "DOCUMENT", length = 20)
    private String document;

    @Column(name = "DOCUMENT_TYPE", nullable = false, length = 10)
    @Convert(converter = DocumentType.Converter.class)
    private DocumentType documentType;

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