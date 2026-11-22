package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {
    Optional<FileAsset> findByGuid(String guid);

    List<FileAsset> findAllByIdIn(Collection<Long> ids);
}
