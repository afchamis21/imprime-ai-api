package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findUserByEmail(String email);

    Optional<User> findUserByGuid(String guid);

    List<User> findAllByStatus(StatusCd statusCd);

    List<User> findAllByOrderByCreateDtDesc();

    long countByRoleAndStatus(UserRole role, StatusCd status);

    boolean existsUserByEmail(String email);

    boolean existsUserByEmailAndIdNot(String email, Long id);

    boolean existsUserByDocumentTypeAndDocument(DocumentType documentType, String document);

    boolean existsUserByDocumentTypeAndDocumentAndIdNot(DocumentType documentType, String document, Long id);

    boolean existsUserByPhoneNumber(String phoneNumber);

    boolean existsUserByPhoneNumberAndIdNot(String phoneNumber, Long id);

    List<User> findAllByCompanyId(Long companyId);
}
