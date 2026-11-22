package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.UserRole;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "USERS")
public class User extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID", nullable = false)
    private Long id;

    @Column(name = "FIRST_NAME", nullable = false, length = 100)
    private String firstName;

    @Column(name = "LAST_NAME", nullable = false, length = 100)
    private String lastName;

    @Column(name = "DOCUMENT", nullable = false, length = 20)
    private String document;

    @Column(name = "DOCUMENT_TYPE", nullable = false, length = 10)
    @Convert(converter = DocumentType.Converter.class)
    private DocumentType documentType;

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false)
    private String passwordHash;

    @Column(name = "PHONE_COUNTRY", nullable = false, length = 5)
    private String phoneCountry;

    @Column(name = "PHONE_NUMBER", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "PRIMARY_ADDRESS_ID")
    private Long primaryAddressId;

    @Column(name = "COMPANY_ID")
    private Long companyId;

    @Column(name = "AVATAR_URL", length = 1000)
    private String avatarUrl;

    @Convert(converter = UserRole.Converter.class)
    @Column(name = "ROLE", nullable = false, length = 20)
    private UserRole role = UserRole.BUYER;

    @Column(name = "SUSPENSION_REASON", length = 500)
    private String suspensionReason;

    @Column(name = "SUSPENDED_AT")
    private Instant suspendedAt;

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Transient
    public boolean isAdmin() {
        return UserRole.ADMIN.equals(role);
    }
}