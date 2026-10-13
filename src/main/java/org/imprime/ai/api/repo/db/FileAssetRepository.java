package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {
}
