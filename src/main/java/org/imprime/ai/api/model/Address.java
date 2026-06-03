package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.type.NumericBooleanConverter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.EntityType;

@Getter
@Setter
@Entity
@Table(name = "ADDRESS")
public class Address extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ADDRESS_ID", nullable = false)
    private Long id;

    @Column(name = "OWNER_ID", nullable = false)
    private Long ownerId;

    @Column(name = "OWNER_TYPE")
    @Convert(converter = EntityType.Converter.class)
    private EntityType ownerType;

    @Column(name = "COUNTRY", nullable = false, length = 100)
    private String country;

    @Column(name = "STATE", length = 100)
    private String state;

    @Column(name = "CITY", length = 100)
    private String city;

    @Column(name = "ZIP_CODE", length = 20)
    private String zipCode;

    @Column(name = "NEIGHBORHOOD", length = 100)
    private String neighborhood;

    @Column(name = "ADDRESS_LINE_1", nullable = false)
    private String addressLine1;

    @Column(name = "ADDRESS_LINE_2")
    private String addressLine2;

    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "DEFAULT_ADDRESS", nullable = false)
    private Boolean defaultAddress;
}