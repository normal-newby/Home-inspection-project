package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.CompanyAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyAssetRepository extends JpaRepository<CompanyAsset, UUID> {
    Optional<CompanyAsset> findByKey(String key);
}
