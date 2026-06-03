package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findUserByEmail(String email);

    Optional<User> findUserByGuid(String guid);

    List<User> findAllByStatus(StatusCd statusCd);

    boolean existsUserByEmail(String email);

    boolean existsUserByDocumentTypeAndDocument(DocumentType documentType, String document);

}
