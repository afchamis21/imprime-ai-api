package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.dto.CompanyAndAddress;
import org.imprime.ai.api.model.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByName(String name);

    boolean existsByDocumentTypeAndDocument(DocumentType documentType, String document);

    Optional<Company> findByOwnerId(Long id);

    @Query(value = """
                SELECT new org.imprime.ai.api.model.dto.CompanyAndAddress(c, a) 
                FROM Company c JOIN Address a ON a.ownerId = c.id AND a.ownerType = EntityType.COMPANY AND a.defaultAddress AND a.status = StatusCd.ACTIVE
                WHERE a.city = :city
            """)
    List<CompanyAndAddress> findCompaniesByCity(String city);

    @Query(value = """
                SELECT new org.imprime.ai.api.model.dto.CompanyAndAddress(c, a) 
                FROM Company c JOIN Address a ON a.ownerId = c.id AND a.ownerType = EntityType.COMPANY AND a.defaultAddress AND a.status = StatusCd.ACTIVE
                WHERE a.state = :state
            """)
    List<CompanyAndAddress> findCompaniesByState(String state);

    @Query(value = """
                SELECT new org.imprime.ai.api.model.dto.CompanyAndAddress(c, a) 
                FROM Company c JOIN Address a ON a.ownerId = c.id AND a.ownerType = EntityType.COMPANY AND a.defaultAddress AND a.status = StatusCd.ACTIVE
                WHERE a.country = :country
            """)
    List<CompanyAndAddress> findCompaniesByCountry(String country);
}
