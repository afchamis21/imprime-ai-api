package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByUserId(Long id);
}
