package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
