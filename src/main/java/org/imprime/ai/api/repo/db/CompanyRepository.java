package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByDocumentTypeAndDocument(DocumentType documentType, String document);

    Optional<Company> findByOwnerId(Long id);

    Optional<Company> findByGuid(String guid);

    Optional<Company> findByGuidAndStatus(String guid, StatusCd status);

    Optional<Company> findBySlugAndStatus(String slug, StatusCd status);

    Optional<Company> findFirstByNameIgnoreCaseAndStatus(String name, StatusCd status);

    List<Company> findAllByStatus(StatusCd status);

    List<Company> findAllByOrderByCreateDtDesc();

    List<Company> findAllByIdIn(Collection<Long> ids);

    @Query(value = "SELECT u FROM User u JOIN Company c ON u.id = c.ownerId WHERE c.guid = :companyId")
    Optional<User> findOwnerByCompanyGuid(String companyId);

    @Query(value = "SELECT c FROM User u JOIN Company c ON u.id = c.ownerId WHERE u.guid = :guid")
    Optional<Company> findByOwnerGuid(String guid);
}
