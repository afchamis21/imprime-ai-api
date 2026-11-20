package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByOwnerTypeAndOwnerId(EntityType ownerType, Long ownerId);

    Optional<Address> findByGuid(String guid);

    Optional<Address> findByGuidAndStatus(String guid, StatusCd status);

    List<Address> findAllByOwnerTypeAndOwnerIdAndStatus(EntityType ownerType, Long ownerId, StatusCd status);

    List<Address> findAllByOwnerTypeAndOwnerIdInAndStatus(EntityType ownerType, Collection<Long> ownerIds, StatusCd status);

    Page<Address> findAllByOwnerTypeAndOwnerIdAndStatus(EntityType ownerType, Long ownerId, StatusCd status, Pageable pageable);

    @Query("""
            SELECT a FROM Address a
            WHERE a.status = :status
              AND ((a.ownerType = :userType AND a.ownerId = :userId)
                OR (a.ownerType = :companyType AND a.ownerId = :companyId))
            """)
    Page<Address> findActiveForUserAndCompany(
            @Param("userType") EntityType userType,
            @Param("userId") Long userId,
            @Param("companyType") EntityType companyType,
            @Param("companyId") Long companyId,
            @Param("status") StatusCd status,
            Pageable pageable
    );
}
