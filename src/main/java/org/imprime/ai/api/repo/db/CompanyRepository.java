package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByName(String name);

    boolean existsByDocumentTypeAndDocument(DocumentType documentType, String document);

    Optional<Company> findByOwnerId(Long id);

    Optional<Company> findByGuid(String guid);

    @Query(value = "SELECT u FROM User u JOIN Company c ON u.id = c.ownerId WHERE c.guid = :companyId")
    Optional<User> findOwnerByCompanyGuid(String companyId);

    @Query(value = "SELECT c FROM User u JOIN Company c ON u.id = c.ownerId WHERE u.guid = :guid")
    Optional<Company> findByOwnerGuid(String guid);
}
