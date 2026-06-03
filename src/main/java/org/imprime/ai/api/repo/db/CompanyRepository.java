package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByName(String name);

    boolean existsByDocumentTypeAndDocument(DocumentType documentType, String document);
}
