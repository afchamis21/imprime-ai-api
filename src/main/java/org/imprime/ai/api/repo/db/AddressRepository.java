package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByOwnerTypeAndOwnerId(EntityType ownerType, Long ownerId);
    Optional<Address> findByGuid(String guid);
}
